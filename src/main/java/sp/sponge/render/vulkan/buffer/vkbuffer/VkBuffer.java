package sp.sponge.render.vulkan.buffer.vkbuffer;

import sp.sponge.render.vulkan.VulkanCtx;

import java.nio.ByteBuffer;

public interface VkBuffer {
    long getBufferPtr();

    long getRequestedSize();

    ByteBuffer map(VulkanCtx ctx);

    void unmap(VulkanCtx ctx);

    void free(VulkanCtx ctx);
}
