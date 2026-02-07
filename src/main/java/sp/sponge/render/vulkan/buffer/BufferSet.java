package sp.sponge.render.vulkan.buffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkBufferCopy;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBuffer;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBufferImpl;
import sp.sponge.render.vulkan.device.command.CommandBuffer;

import java.nio.ByteBuffer;

public class BufferSet {
    private final int SIZE;
    private final VulkanCtx vulkanCtx;
    private final BufferPair bufferPair;
    private ByteBuffer buffer;
    private long gpuAddress = -1L;

    public BufferSet(VulkanCtx ctx, int size, int usage) {
        this.vulkanCtx = ctx;
        this.SIZE = size;
        this.bufferPair = createBufferPair(ctx, usage);
    }

    public void startMapping() {
        this.buffer = this.bufferPair.cpuBuffer().map(this.vulkanCtx);
    }

    public void stopMapping() {
        this.bufferPair.cpuBuffer().unmap(this.vulkanCtx);
        this.buffer = null;
    }

    public void sendDataToGpu(CommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0).dstOffset(0).size(bufferPair.cpuBuffer().getRequestedSize());
            VK10.vkCmdCopyBuffer(commandBuffer.getVkCommandBuffer(), bufferPair.cpuBuffer().getBufferPtr(), bufferPair.gpuBuffer().getBufferPtr(), copyRegion);
        }
    }

    private BufferPair createBufferPair(VulkanCtx ctx, int usage) {
        VkBufferImpl cpuBuffer = new VkBufferImpl(ctx, SIZE,
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );

        VkBufferImpl gpuBuffer = new VkBufferImpl(ctx, SIZE,
                VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | usage | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );

        return new BufferPair(cpuBuffer, gpuBuffer);
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void free() {
        this.bufferPair.free(this.vulkanCtx);
    }

    public long getGpuBuffer() {
        return this.bufferPair.gpuBuffer.getBufferPtr();
    }

    public long getGpuAddress(VulkanCtx ctx) {
        if (gpuAddress == -1L) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                gpuAddress = VulkanUtils.getBufferGpuAddressConst(ctx, stack, this.bufferPair.gpuBuffer.getBufferPtr()).deviceAddress();
            }
        }

        return gpuAddress;
    }

    public record BufferPair(VkBuffer cpuBuffer, VkBuffer gpuBuffer){
        public void free(VulkanCtx ctx) {
            this.cpuBuffer.free(ctx);
            this.gpuBuffer.free(ctx);
        }
    }
}
