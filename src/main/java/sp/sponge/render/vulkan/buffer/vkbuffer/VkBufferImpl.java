package sp.sponge.render.vulkan.buffer.vkbuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public class VkBufferImpl implements VkBuffer {
    private final long bufferPtr;
    private final long allocation;
    private final PointerBuffer pointerBuffer;
    private final long requestedSize;
    private final long allocationSize;

    private long mappedMemory;

    public VkBufferImpl(VulkanCtx ctx, long size, int usage, int reqMask) {
        this.requestedSize = size;
        this.mappedMemory = MemoryUtil.NULL;
        this.pointerBuffer = MemoryUtil.memAllocPointer(1);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDevice logicalDevice = ctx.getLogicalDevice().getVkDevice();
            LongBuffer longBuffer = stack.mallocLong(1);

            //Create the Buffer
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

            VulkanUtils.check(
                    VK10.vkCreateBuffer(logicalDevice, bufferCreateInfo, null, longBuffer),
                    "Failed to create buffer"
            );
            this.bufferPtr = longBuffer.get(0);


            //Allocate the memory
            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.calloc(stack);
            VK10.vkGetBufferMemoryRequirements(logicalDevice, this.bufferPtr, memoryRequirements);

            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(VulkanUtils.getMemoryType(ctx, memoryRequirements.memoryTypeBits(), reqMask));

            if ((usage & VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT) != 0) {
                VkMemoryAllocateFlagsInfo memoryFlags = VkMemoryAllocateFlagsInfo.calloc(stack)
                        .sType$Default()
                        .flags(VK12.VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT);
                allocateInfo.pNext(memoryFlags);
            }

            VulkanUtils.check(
                    VK10.vkAllocateMemory(logicalDevice, allocateInfo, null, longBuffer),
                    "Failed to allocate memory for buffer"
            );
            this.allocation = longBuffer.get(0);
            this.allocationSize = allocateInfo.allocationSize();


            //Bind the memory
            VulkanUtils.check(
                    VK10.vkBindBufferMemory(logicalDevice, this.bufferPtr, this.allocation, 0),
                    "Failed to bind buffer memory"
            );
        }
    }

    @Override
    public long getBufferPtr() {
        return bufferPtr;
    }

    @Override
    public long getRequestedSize() {
        return requestedSize;
    }

    @Override
    public ByteBuffer map(VulkanCtx ctx) {
        if (this.mappedMemory == MemoryUtil.NULL) {
            VulkanUtils.check(
                    VK10.vkMapMemory(ctx.getLogicalDevice().getVkDevice(), this.allocation,0, this.allocationSize, 0, pointerBuffer),
                    "Failed to map Buffer"
            );
            this.mappedMemory = pointerBuffer.get(0);
        }

        return MemoryUtil.memByteBuffer(this.mappedMemory, (int) this.requestedSize);
    }

    @Override
    public void unmap(VulkanCtx ctx) {
        if (this.mappedMemory != MemoryUtil.NULL) {
            VK10.vkUnmapMemory(ctx.getLogicalDevice().getVkDevice(), this.allocation);
            this.mappedMemory = MemoryUtil.NULL;
        }
    }

    @Override
    public void free(VulkanCtx ctx) {
        MemoryUtil.memFree(pointerBuffer);
        this.unmap(ctx);
        VkDevice logicalDevice = ctx.getLogicalDevice().getVkDevice();
        VK10.vkFreeMemory(logicalDevice, this.allocation, null);
        VK10.vkDestroyBuffer(logicalDevice, this.bufferPtr, null);
    }

}
