package sp.sponge.render.vulkan.buffer.descriptors;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBuffer;
import sp.sponge.render.vulkan.device.LogicalDevice;
import sp.sponge.render.vulkan.image.texture.TextureSampler;
import sp.sponge.render.vulkan.raytracing.accelstruct.TLAS;
import sp.sponge.render.vulkan.image.ImageView;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;

public class DescriptorSet {
    private final long vkDescriptorSet;

    public DescriptorSet(LogicalDevice device, DescriptorPool pool, DescriptorSetLayout layout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longBuffer = stack.mallocLong(1);
            longBuffer.put(0, layout.getVkDescriptorSetLayout());
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(pool.getVkDescriptorPool())
                    .pSetLayouts(longBuffer);

            VulkanUtils.check(
                    VK13.vkAllocateDescriptorSets(device.getVkDevice(), allocateInfo, longBuffer),
                    "Failed to allocate descriptor sets"
            );
            this.vkDescriptorSet = longBuffer.get(0);
        }
    }

    public void setBuffer(VulkanCtx ctx, VkBuffer buffer, long range, int binding, int type) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer.getBufferPtr())
                    .offset(0)
                    .range(range);

            VkWriteDescriptorSet.Buffer descriptorSetBuffer = VkWriteDescriptorSet.calloc(1, stack);

            descriptorSetBuffer.get(0)
                    .sType$Default()
                    .dstSet(this.vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(type)
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo);

            VK10.vkUpdateDescriptorSets(ctx.getLogicalDevice().getVkDevice(), descriptorSetBuffer, null);
        }
    }

    public void setTLAS(VulkanCtx ctx, int binding, int descriptorType, TLAS tlas) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer descriptorSetBuffer = VkWriteDescriptorSet.calloc(1, stack);

            VkWriteDescriptorSetAccelerationStructureKHR setAS = VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack)
                    .sType$Default()
                    .pAccelerationStructures(stack.longs(tlas.getAsHandle()));

            descriptorSetBuffer.get(0)
                    .sType$Default()
                    .dstSet(this.vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(descriptorType)
                    .descriptorCount(1)
                    .pNext(setAS);

            VK10.vkUpdateDescriptorSets(ctx.getLogicalDevice().getVkDevice(), descriptorSetBuffer, null);
        }
    }

    public void setImage(VulkanCtx ctx, ImageView imageView, @Nullable TextureSampler sampler, int descriptorType, int binding) {
        this.setImages(ctx, List.of(imageView), sampler, descriptorType, binding);
    }

    public void setImages(VulkanCtx ctx, List<ImageView> imageViews, @Nullable TextureSampler sampler, int descriptorType, int binding) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int numOfImages = imageViews.size();
            VkWriteDescriptorSet.Buffer descriptorSetBuffer = VkWriteDescriptorSet.calloc(numOfImages, stack);
            for (int i = 0; i < numOfImages; i++) {
                ImageView imageView = imageViews.get(i);

                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .imageView(imageView.getVkImageViewHandle());

//                if ((imageView.getUsage() & VK10.VK_IMAGE_USAGE_STORAGE_BIT) > 0) {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_GENERAL);
//                } else {
//                    imageInfo.imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
//                }

                if (sampler != null) {
                    imageInfo.sampler(sampler.getVkSampler());
                }


                descriptorSetBuffer.get(i)
                        .sType$Default()
                        .dstSet(this.vkDescriptorSet)
                        .dstBinding(binding)
                        .descriptorType(descriptorType)
                        .descriptorCount(1)
                        .pImageInfo(imageInfo);
            }


            VK10.vkUpdateDescriptorSets(ctx.getLogicalDevice().getVkDevice(), descriptorSetBuffer, null);
        }
    }

    public long getVkDescriptorSet() {
        return vkDescriptorSet;
    }
}
