package sp.sponge.render.vulkan.image.texture;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkBufferImageCopy;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBuffer;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBufferImpl;
import sp.sponge.render.vulkan.device.command.CommandBuffer;
import sp.sponge.render.vulkan.image.Image;
import sp.sponge.render.vulkan.image.ImageView;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class Texture {
    private final int width;
    private final int height;
    private final String id;
    private final Image image;
    private final ImageView imageView;
    private boolean recordedTransition;
    private VkBuffer buffer;

    public Texture(VulkanCtx ctx, String id, TextureInfo textureInfo, int format) {
        this.id = id;
        this.width = textureInfo.width;
        this.height = textureInfo.height;

        this.createBuffer(ctx, textureInfo.buffer);
        this.image = new Image.ImageBuilder()
                .width(width)
                .height(height)
                .usage(VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT)
                .format(format)
                .build(ctx);

        this.imageView = new ImageView.ImageViewBuilder()
                .setFormat(this.image.getFormat())
                .setAspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .build(ctx.getLogicalDevice(), this.image.getVkImage());
    }

    private void createBuffer(VulkanCtx ctx, ByteBuffer data) {
        this.buffer = new VkBufferImpl(ctx, data.remaining(), VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        ByteBuffer dstBuffer = this.buffer.map(ctx);
        dstBuffer.put(data);
        dstBuffer.flip();
        this.buffer.unmap(ctx);
    }

    public void recordImageTransition(CommandBuffer commandBuffer) {
        if (buffer != null && !recordedTransition) {
            recordedTransition = true;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VulkanUtils.imageBarrier(stack, commandBuffer.getVkCommandBuffer(), this.image.getVkImage(),
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK13.VK_ACCESS_2_NONE, VK_ACCESS_TRANSFER_WRITE_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);

                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                        .bufferOffset(0)
                        .bufferRowLength(0)
                        .bufferImageHeight(0)
                        .imageOffset(vkOffset3D -> vkOffset3D.x(0).y(0).z(0))
                        .imageExtent(vkExtent3D -> vkExtent3D.width(this.width).height(this.height).depth(1))
                        .imageSubresource(vkImageSubresourceLayers ->
                                vkImageSubresourceLayers.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                        .mipLevel(0)
                                        .baseArrayLayer(0)
                                        .layerCount(1)
                        );

                vkCmdCopyBufferToImage(
                        commandBuffer.getVkCommandBuffer(),
                        this.buffer.getBufferPtr(),
                        this.image.getVkImage(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        region
                );

                VulkanUtils.imageBarrier(stack, commandBuffer.getVkCommandBuffer(), this.image.getVkImage(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getId() {
        return id;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public Image getImage() {
        return image;
    }

    public void free(VulkanCtx ctx) {
        this.freeBuffer(ctx);
        this.imageView.free();
        this.image.free(ctx);
    }

    public void freeBuffer(VulkanCtx ctx) {
        if (buffer != null) {
            buffer.free(ctx);
            buffer = null;
        }
    }

    public record TextureInfo(ByteBuffer buffer, int width, int height, int channels){}
}
