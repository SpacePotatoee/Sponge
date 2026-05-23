package sp.sponge.render;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRExternalMemoryWin32;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkMemoryGetWin32HandleInfoKHR;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.image.Attachment;
import sp.sponge.render.vulkan.image.Image;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

public class Framebuffer {
    private final int framebuffer;
    private final int colorAttachment;

    public Framebuffer(int colorAttachment, int width, int height) {
        this.colorAttachment = colorAttachment;
        this.framebuffer = GL30.glGenFramebuffers();

        this.bind();
        glBindTexture(GL_TEXTURE_2D, this.colorAttachment);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.colorAttachment, 0);

        glBindTexture(GL_TEXTURE_2D, 0);
        unBind();
    }

    public int getFramebuffer() {
        return framebuffer;
    }

    public int getColorAttachment() {
        return colorAttachment;
    }

    public void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
    }

    public static void unBind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public static Framebuffer convertVkToGL(VulkanCtx ctx, Attachment attachment) {
        Image vkImage = attachment.getImage();
        int width = vkImage.getWidth();
        int height = vkImage.getHeight();
        int colorAttachment;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointerBuffer = stack.mallocPointer(1);

            //Vulkan
            VkMemoryGetWin32HandleInfoKHR getInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(vkImage.getVkMemory())
                    .handleType(VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT);
            VulkanUtils.check(
                    KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR(ctx.getLogicalDevice().getVkDevice(), getInfo, pointerBuffer),
                    "Failed to get image memory handle"
            );
            long sharedMemoryHandle = pointerBuffer.get(0);


            //OpenGl
            int memory = EXTMemoryObject.glCreateMemoryObjectsEXT();
            EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
                    memory, vkImage.getSize(), EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, sharedMemoryHandle);


            colorAttachment = GL45.glCreateTextures(GL_TEXTURE_2D);
            EXTMemoryObject.glTextureStorageMem2DEXT(
                    colorAttachment, vkImage.getMipLevels(), GL_RGBA16, width, height, memory, 0);
        }

        return new Framebuffer(colorAttachment, width, height);
    }

    public void free() {
        glDeleteTextures(this.colorAttachment);
        GL30.glDeleteFramebuffers(this.framebuffer);
    }
}
