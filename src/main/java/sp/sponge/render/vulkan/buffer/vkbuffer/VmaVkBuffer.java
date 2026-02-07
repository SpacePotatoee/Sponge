package sp.sponge.render.vulkan.buffer.vkbuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public class VmaVkBuffer implements VkBuffer {
    private final long bufferPtr;
    private final long allocation;
    private final PointerBuffer pointerBuffer;
    private final long requestedSize;
    private final boolean persistentMap;

    private long mappedMemory;

    public VmaVkBuffer(VulkanCtx ctx, long size, int usage, int vmaUsage, int vmaFlags, boolean mapped, int reqMask) {
        this.requestedSize = size;
        this.mappedMemory = MemoryUtil.NULL;
        this.persistentMap = mapped;
        this.pointerBuffer = MemoryUtil.memAllocPointer(1);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            //Create the Buffer
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);


            if (mapped) {
                vmaFlags |= Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT;
            }

            //Allocate Memory
            VmaAllocationCreateInfo allocationCreateInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(vmaUsage)
                    .flags(vmaFlags)
                    .requiredFlags(reqMask);

            PointerBuffer memPtrBuffer = stack.callocPointer(1);
            LongBuffer longBuffer = stack.mallocLong(1);
            VmaAllocationInfo allocationInfo = VmaAllocationInfo.calloc(stack);
            VulkanUtils.check(
                    Vma.vmaCreateBuffer(
                            ctx.getMemoryAllocator().getVmaHandle(),
                            bufferCreateInfo,
                            allocationCreateInfo,
                            longBuffer,
                            memPtrBuffer,
                            allocationInfo
                    ),
                    "Failed to create Buffer"
            );
            this.bufferPtr = longBuffer.get(0);
            this.allocation = memPtrBuffer.get(0);

            if (mapped) {
                this.mappedMemory = allocationInfo.pMappedData();
            }
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
        if (this.mappedMemory == MemoryUtil.NULL && !this.persistentMap) {
            VulkanUtils.check(
                    Vma.vmaMapMemory(ctx.getMemoryAllocator().getVmaHandle(), this.allocation, pointerBuffer),
                    "Failed to map Buffer"
            );
            this.mappedMemory = pointerBuffer.get(0);
        }

        return MemoryUtil.memByteBuffer(this.mappedMemory, (int) this.requestedSize);
    }

    @Override
    public void unmap(VulkanCtx ctx) {
        if (this.mappedMemory != MemoryUtil.NULL && !this.persistentMap) {
            Vma.vmaUnmapMemory(ctx.getMemoryAllocator().getVmaHandle(), this.allocation);
            this.mappedMemory = MemoryUtil.NULL;
        }
    }

    @Override
    public void free(VulkanCtx ctx) {
        MemoryUtil.memFree(pointerBuffer);
        this.unmap(ctx);
        Vma.vmaDestroyBuffer(ctx.getMemoryAllocator().getVmaHandle(), this.bufferPtr, this.allocation);
    }
}
