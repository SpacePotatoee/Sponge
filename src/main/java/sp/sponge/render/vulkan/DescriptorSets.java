package sp.sponge.render.vulkan;

import sp.sponge.render.vulkan.buffer.descriptors.DescriptorSet;
import sp.sponge.render.vulkan.buffer.descriptors.DescriptorSetLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DescriptorSets {
    private final HashMap<String, Group> groupsList = new HashMap<>();

    public Group addDescriptorGroup(VulkanCtx ctx, String id, int descriptorType, int binding, int count, int stage) {
        DescriptorSetLayout layout = new DescriptorSetLayout(
                ctx,
                new DescriptorSetLayout.LayoutInfo(
                        descriptorType,
                        binding,
                        count,
                        stage
                )
        );

        return addDescriptorGroup(ctx, id, layout);
    }

    public Group addDescriptorGroup(VulkanCtx ctx, String id, DescriptorSetLayout layout) {
        DescriptorSet set = ctx.getDescriptorAllocator().createDescriptorSet(
                ctx,
                id,
                layout
        );

        Group group = new Group(set, layout);
        groupsList.put(id, group);

        return group;
    }

    public Group getGroup(String id) {
        return this.groupsList.get(id);
    }

    public DescriptorSet getDescriptorSet(String id) {
        return this.groupsList.get(id).descriptorSet;
    }

    public DescriptorSetLayout getLayout(String id) {
        return this.groupsList.get(id).layout;
    }

    public void free(VulkanCtx ctx) {
        for (Group group : groupsList.values()) {
            group.free(ctx);
        }
    }


    public record Group(DescriptorSet descriptorSet, DescriptorSetLayout layout){
        public void free(VulkanCtx ctx) {
            layout.free(ctx);
        }
    }
}
