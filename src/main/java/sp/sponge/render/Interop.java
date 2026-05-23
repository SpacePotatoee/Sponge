package sp.sponge.render;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.image.Attachment;
import sp.sponge.render.vulkan.sync.Semaphore;

import static org.lwjgl.opengl.EXTSemaphoreWin32.*;

//Thank you https://github.com/jherico/VulkanExamples/blob/cpp/examples/glinterop/glinterop.cpp
public class Interop {
    private SemaphorePair readySemaphorePair;
    private SemaphorePair completeSemaphorePair;
    private final Attachment vkFramebuffer;
    private final Attachment prevVkFramebuffer;
    private final Framebuffer glFramebuffer;

    public Interop(VulkanCtx ctx) {
        VkDevice device = ctx.getLogicalDevice().getVkDevice();
        Window window = Window.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            //Create Vulkan stuff
            //Semaphores
            Semaphore vk_glReadySemaphore = new Semaphore(ctx, true);
            long glReadySemaphoreHandle = getSemaphoreWin32Handle(device, vk_glReadySemaphore, stack);

            Semaphore vk_glCompleteSemaphore = new Semaphore(ctx, true);
            long glCompleteSemaphoreHandle = getSemaphoreWin32Handle(device, vk_glCompleteSemaphore, stack);

            //Image
            this.vkFramebuffer = new Attachment(
                    ctx, width, height, VK10.VK_FORMAT_R16G16B16A16_UNORM,
                    VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_STORAGE_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
            this.prevVkFramebuffer = new Attachment(
                    ctx, width, height, VK10.VK_FORMAT_R16G16B16A16_UNORM,
                    VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT);


            //Create OpenGL stuff
            //Semaphores
            int gl_glReadySemaphore = EXTSemaphore.glGenSemaphoresEXT();
            glImportSemaphoreWin32HandleEXT(gl_glReadySemaphore, GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, glReadySemaphoreHandle);
            this.readySemaphorePair = new SemaphorePair(vk_glReadySemaphore, gl_glReadySemaphore);

            int gl_glCompleteSemaphore = EXTSemaphore.glGenSemaphoresEXT();
            glImportSemaphoreWin32HandleEXT(gl_glCompleteSemaphore, GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, glCompleteSemaphoreHandle);
            this.completeSemaphorePair = new SemaphorePair(vk_glCompleteSemaphore, gl_glCompleteSemaphore);

            //Image
            this.glFramebuffer = Framebuffer.convertVkToGL(ctx, this.vkFramebuffer);
        }
    }

    public Attachment getVkFramebuffer() {
        return vkFramebuffer;
    }

    public Attachment getPrevVkFramebuffer() {
        return prevVkFramebuffer;
    }

    public Framebuffer getGlFramebuffer() {
        return glFramebuffer;
    }

    public SemaphorePair getCompleteSemaphorePair() {
        return completeSemaphorePair;
    }

    public SemaphorePair getReadySemaphorePair() {
        return readySemaphorePair;
    }

    public void resize(VulkanCtx ctx) {
        this.completeSemaphorePair.vkSemaphore().free(ctx);
        int glSemaphore = this.completeSemaphorePair.glSemaphore();

        this.completeSemaphorePair = new SemaphorePair(new Semaphore(ctx, true), glSemaphore);


        this.readySemaphorePair.vkSemaphore().free(ctx);
        int glSemaphore2 = this.readySemaphorePair.glSemaphore();

        this.readySemaphorePair = new SemaphorePair(new Semaphore(ctx, true), glSemaphore2);
    }

    public void free(VulkanCtx ctx) {
        this.completeSemaphorePair.free(ctx);
        this.readySemaphorePair.free(ctx);
        this.vkFramebuffer.free(ctx);
        this.prevVkFramebuffer.free(ctx);

        this.glFramebuffer.free();
    }

    private static long getSemaphoreWin32Handle(VkDevice device, Semaphore semaphore, MemoryStack stack) {
        PointerBuffer pointerBuffer = stack.mallocPointer(1);

        VkSemaphoreGetWin32HandleInfoKHR semaphoreWin32HandleInfo = VkSemaphoreGetWin32HandleInfoKHR.calloc(stack)
                .sType$Default()
                .semaphore(semaphore.getVkSemaphore())
                .handleType(VK11.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT);
        VulkanUtils.check(
                KHRExternalSemaphoreWin32.vkGetSemaphoreWin32HandleKHR(device, semaphoreWin32HandleInfo, pointerBuffer),
                "Failed to get Semaphore Win32 Handle"
        );


        return pointerBuffer.get(0);
    }

    public record SemaphorePair(Semaphore vkSemaphore, int glSemaphore) {
        public void free(VulkanCtx ctx) {
            vkSemaphore.free(ctx);
        }
    }

}
