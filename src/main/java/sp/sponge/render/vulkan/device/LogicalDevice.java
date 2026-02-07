package sp.sponge.render.vulkan.device;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import sp.sponge.render.vulkan.VulkanUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class LogicalDevice implements AutoCloseable {
    private final VkDevice vkDevice;

    public LogicalDevice(PhysicalDevice physicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            VkQueueFamilyProperties.Buffer queueFamilyPropBuffer = physicalDevice.getDeviceQueFamilyProperties();
            int numOfQueueFamilies = queueFamilyPropBuffer.capacity();
            VkDeviceQueueCreateInfo.Buffer info = VkDeviceQueueCreateInfo.calloc(numOfQueueFamilies, stack);
            for (int i = 0; i < numOfQueueFamilies; i++) {
                FloatBuffer priorities = stack.callocFloat(queueFamilyPropBuffer.get(i).queueCount());
                info.get(i)
                        .sType$Default()
                        .queueFamilyIndex(i)
                        .pQueuePriorities(priorities);
            }

            PointerBuffer enabledExtensinsPtr = createRequiredExtensions(physicalDevice, stack);


            VkPhysicalDeviceVulkan11Features features11 = VkPhysicalDeviceVulkan11Features.calloc(stack)
                    .sType$Default()
                    .shaderDrawParameters(true);

            VkPhysicalDeviceVulkan12Features features12 = VkPhysicalDeviceVulkan12Features.calloc(stack)
                    .sType$Default()
                    .bufferDeviceAddress(true)
                    .runtimeDescriptorArray(true)
                    .scalarBlockLayout(true)
                    .drawIndirectCount(true)
                    .samplerMirrorClampToEdge(true)
                    .descriptorIndexing(true)
                    .samplerFilterMinmax(true)
                    .shaderOutputViewportIndex(true)
                    .shaderOutputLayer(true);

            VkPhysicalDeviceVulkan13Features features13 = VkPhysicalDeviceVulkan13Features.calloc(stack)
                    .sType$Default()
                    .dynamicRendering(true)
                    .synchronization2(true);

            VkPhysicalDeviceRayTracingPositionFetchFeaturesKHR positionFetch = VkPhysicalDeviceRayTracingPositionFetchFeaturesKHR.calloc(stack)
                    .sType$Default()
                    .rayTracingPositionFetch(true);

            VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            features2.features().shaderInt64(true);
            features2.pNext(features11.address());
            features2.pNext(positionFetch);
            features11.pNext(features12.address());
            features12.pNext(features13.address());


            VkPhysicalDeviceRayTracingPipelineFeaturesKHR rtPipelineFeatures = VkPhysicalDeviceRayTracingPipelineFeaturesKHR.calloc(stack)
                    .sType$Default()
                    .rayTracingPipeline(true);

            VkPhysicalDeviceAccelerationStructureFeaturesKHR asFeatures = VkPhysicalDeviceAccelerationStructureFeaturesKHR.calloc(stack)
                    .sType$Default()
                    .accelerationStructure(true);


            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(features2.address())
                    .pQueueCreateInfos(info)
                    .ppEnabledExtensionNames(enabledExtensinsPtr)
                    .pNext(rtPipelineFeatures)
                    .pNext(asFeatures);

            PointerBuffer devicePointer = stack.mallocPointer(1);
            VulkanUtils.check(
                    VK10.vkCreateDevice(physicalDevice.getVkPhysicalDevice(), deviceCreateInfo, null, devicePointer),
                    "Failed to create logical device"
            );

            this.vkDevice = new VkDevice(devicePointer.get(0), physicalDevice.getVkPhysicalDevice(), deviceCreateInfo);
        }
    }

    private PointerBuffer createRequiredExtensions(PhysicalDevice physicalDevice, MemoryStack stack) {
        List<String> deviceExtensions = new ArrayList<>();
        VkExtensionProperties.Buffer extensionsBuffer = physicalDevice.getDeviceExtensions();
        for (int i = 0; i < extensionsBuffer.capacity(); i++) {
            VkExtensionProperties properties = extensionsBuffer.get(i);
            String name = properties.extensionNameString();
            deviceExtensions.add(name);
        }

        boolean mac = deviceExtensions.contains(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME) && VulkanUtils.getOS() == VulkanUtils.OSType.MACOS;

        List<ByteBuffer> extensionsList = new ArrayList<>();
        for (String name : deviceExtensions) {
            extensionsList.add(stack.ASCII(name));
        }
//        extensionsList.add(stack.ASCII(KHRGetSurfaceCapabilities2.VK_KHR_GET_SURFACE_CAPABILITIES_2_EXTENSION_NAME));
        if (mac) {
            extensionsList.add(stack.ASCII(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME));
        }

        PointerBuffer requiredExtensions = stack.callocPointer(extensionsList.size());
        extensionsList.forEach(requiredExtensions::put);
        requiredExtensions.flip();

        return requiredExtensions;
    }

    public VkDevice getVkDevice() {
        return vkDevice;
    }

    public void waitIdle() {
        VK10.vkDeviceWaitIdle(this.vkDevice);
    }

    @Override
    public void close() {
        VK10.vkDestroyDevice(this.vkDevice, null);
    }
}
