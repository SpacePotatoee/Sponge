package sp.sponge.render.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.vulkan.*;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBuffer;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBufferImpl;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.logging.Logger;

import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.VK_FALSE;

public class VulkanUtils {
    public static final int MAX_FRAMES_IN_FLIGHT = 1;
    private static OSType osType;

    public static final int MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
    public static final int MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
    private static final String DBG_CALL_BACK_PREFIX = "VkDebugUtilsCallback, ";

    public static OSType getOS() {
        if (osType == null) {
            String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
            osType = OSType.getTypeFromName(os);
        }

        return osType;
    }

    public static int alignSize(int size, int alignment) {
        return (size + alignment - 1) & -alignment;
    }

    public static VkDeviceOrHostAddressConstKHR getBufferGpuAddressConst(VulkanCtx ctx, MemoryStack stack, long buffer) {
        return VkDeviceOrHostAddressConstKHR
                .malloc(stack)
                .deviceAddress(
                        VulkanUtils.getBufferGpuAddress(ctx, stack, buffer)
                );
    }

    public static void copyByteBufferToVkBuffer(VulkanCtx ctx, ByteBuffer srcBuffer, int srcOffset, VkBuffer dstBuffer, int dstOffset, int length) {
        ByteBuffer dstMapBuffer = dstBuffer.map(ctx);

        for (int i = 0; i < length; i++) {
            dstMapBuffer.put(i + dstOffset, srcBuffer.get(i + srcOffset));
        }

        dstBuffer.unmap(ctx);
    }

    public static long getBufferGpuAddress(VulkanCtx ctx, MemoryStack stack, long buffer) {
        VkBufferDeviceAddressInfo info = VkBufferDeviceAddressInfo.calloc(stack)
                .sType$Default()
                .buffer(buffer);

        return VK12.vkGetBufferDeviceAddress(ctx.getLogicalDevice().getVkDevice(), info);
    }

    public static VkBuffer createCpuBuffer(VulkanCtx ctx, long size, int usage) {
        return new VkBufferImpl(
                ctx,
                size,
                usage,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
    }

    public static int getMemoryType(VulkanCtx ctx, int memoryBits, int reqMask) {
        int result = -1;
        VkMemoryType.Buffer memoryTypes = ctx.getPhysicalDevice().getDeviceMemoryProperties().memoryTypes();
        for (int i = 0; i < VK10.VK_MAX_MEMORY_TYPES; i++) {
            if ((memoryBits & 1) == 1 && (memoryTypes.get(i).propertyFlags() & reqMask) == reqMask) {
                result = i;
                break;
            }
            memoryBits >>= 1;
        }
        if (result < 0) {
            throw new RuntimeException("Failed to find memory type");
        }
        return result;
    }

    public static void imageBarrier(MemoryStack stack, VkCommandBuffer buffer, long imageHandle,
                                    int oldLayout, int newLayout, long srcStage, long dstStage, long srcAccess, long dstAccess, int aspectMask) {

        VkImageMemoryBarrier2.Buffer imageBarrierBuffer = VkImageMemoryBarrier2.calloc(1, stack)
                .sType$Default()
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcStageMask(srcStage)
                .dstStageMask(dstStage)
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(imageHandle)
                .subresourceRange(vkImageSubresourceRange -> vkImageSubresourceRange
                        .aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(VK10.VK_REMAINING_MIP_LEVELS)
                        .baseArrayLayer(0)
                        .layerCount(VK10.VK_REMAINING_ARRAY_LAYERS));

        VkDependencyInfo dependencyInfo = VkDependencyInfo.calloc(stack)
                        .sType$Default()
                        .pImageMemoryBarriers(imageBarrierBuffer);

        VK13.vkCmdPipelineBarrier2(buffer, dependencyInfo);
    }

    public static VkDebugUtilsMessengerCreateInfoEXT createDebugCallBack(Logger logger) {
        return VkDebugUtilsMessengerCreateInfoEXT.calloc()
                .sType$Default()
                .messageSeverity(MESSAGE_SEVERITY_BITMASK)
                .messageType(MESSAGE_TYPE_BITMASK)
                .pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);

                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                        logger.info(DBG_CALL_BACK_PREFIX + callbackData.pMessageString());
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                        logger.warning(DBG_CALL_BACK_PREFIX + callbackData.pMessageString());
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        logger.severe(DBG_CALL_BACK_PREFIX + callbackData.pMessageString());
                    } else {
                        logger.info(DBG_CALL_BACK_PREFIX + callbackData.pMessageString());
                    }
                    return VK_FALSE;
                });
    }

    public static void check(int errorCode, String errorString) {
        if (errorCode != VK14.VK_SUCCESS) {
            String type = switch (errorCode) {
                case VK10.VK_NOT_READY                   -> "VK_NOT_READY";
                case VK10.VK_TIMEOUT                     -> "VK_TIMEOUT";
                case VK10.VK_EVENT_SET                   -> "VK_EVENT_SET";
                case VK10.VK_EVENT_RESET                 -> "VK_EVENT_RESET";
                case VK10.VK_INCOMPLETE                  -> "VK_INCOMPLETE";
                case VK10.VK_ERROR_OUT_OF_HOST_MEMORY    -> "VK_ERROR_OUT_OF_HOST_MEMORY";
                case VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY  -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
                case VK10.VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
                case VK10.VK_ERROR_DEVICE_LOST           -> "VK_ERROR_DEVICE_LOST";
                case VK10.VK_ERROR_MEMORY_MAP_FAILED     -> "VK_ERROR_MEMORY_MAP_FAILED";
                case VK10.VK_ERROR_LAYER_NOT_PRESENT     -> "VK_ERROR_LAYER_NOT_PRESENT";
                case VK10.VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
                case VK10.VK_ERROR_FEATURE_NOT_PRESENT   -> "VK_ERROR_FEATURE_NOT_PRESENT";
                case VK10.VK_ERROR_INCOMPATIBLE_DRIVER   -> "VK_ERROR_INCOMPATIBLE_DRIVER";
                case VK10.VK_ERROR_TOO_MANY_OBJECTS      -> "VK_ERROR_TOO_MANY_OBJECTS";
                case VK10.VK_ERROR_FORMAT_NOT_SUPPORTED  -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
                case VK10.VK_ERROR_FRAGMENTED_POOL       -> "VK_ERROR_FRAGMENTED_POOL";
                case VK10.VK_ERROR_UNKNOWN               -> "VK_ERROR_UNKNOWN ";
                default -> "Unknown error";
            };

            throw new RuntimeException(errorString + ": " + type);
        }
    }

    public enum OSType {
        MACOS("mac", "darwin"),
        WINDOWS("win"),
        LINUX("nux"),
        OTHER;

        final String[] names;

        OSType(String... names) {
            this.names = names;
        }

        public static OSType getTypeFromName(String name) {
            for (OSType type : values()) {
                for (String osName : type.names) {
                    if (name.equals(osName)) {
                        return type;
                    }
                }
            }

            return OTHER;
        }
    }
}
