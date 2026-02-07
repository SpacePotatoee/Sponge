package sp.sponge.render.vulkan.raytracing.shader;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBuffer;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBufferImpl;

public class ShaderGroup {
    private final VkBuffer buffer;
    private final VkStridedDeviceAddressRegionKHR deviceAddressRegion;

    public ShaderGroup(VulkanCtx ctx) {
        VkPhysicalDeviceRayTracingPipelinePropertiesKHR properties = ctx.getPhysicalDevice().getRayTracingProperties();
        int size = properties.shaderGroupHandleSize();

        this.buffer = new VkBufferImpl(ctx, size,
                KHRRayTracingPipeline.VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR | KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int groupSize = properties.shaderGroupHandleSize();
            int alignment = properties.shaderGroupHandleAlignment();
            int alignedHandle = VulkanUtils.alignSize(groupSize, alignment);

            this.deviceAddressRegion = VkStridedDeviceAddressRegionKHR.calloc()
                    .deviceAddress(VulkanUtils.getBufferGpuAddress(ctx, stack, this.buffer.getBufferPtr()))
                    .stride(alignedHandle)
                    .size(alignedHandle);
        }
    }

    public VkBuffer getBuffer() {
        return buffer;
    }

    public VkStridedDeviceAddressRegionKHR getDeviceAddressRegion() {
        return deviceAddressRegion;
    }

    public void free(VulkanCtx ctx) {
        this.buffer.free(ctx);
        deviceAddressRegion.free();
    }

}
