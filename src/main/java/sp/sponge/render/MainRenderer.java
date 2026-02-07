package sp.sponge.render;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import sp.sponge.Sponge;
import sp.sponge.render.vulkan.DescriptorSets;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.buffer.MeshBuffers;
import sp.sponge.render.vulkan.buffer.UniformBlockUtil;
import sp.sponge.render.vulkan.buffer.descriptors.DescriptorAllocator;
import sp.sponge.render.vulkan.buffer.descriptors.DescriptorSet;
import sp.sponge.render.vulkan.buffer.descriptors.DescriptorSetLayout;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBuffer;
import sp.sponge.render.vulkan.device.command.CommandBuffer;
import sp.sponge.render.vulkan.device.command.CommandPool;
import sp.sponge.render.vulkan.device.Queue;
import sp.sponge.render.vulkan.image.Attachment;
import sp.sponge.render.vulkan.image.Image;
import sp.sponge.render.vulkan.image.texture.TextureManager;
import sp.sponge.render.vulkan.image.texture.TextureSampler;
import sp.sponge.render.vulkan.pipeline.shaders.ShaderCompiler;
import sp.sponge.render.vulkan.pipeline.shaders.ShaderModule;
import sp.sponge.render.vulkan.raytracing.RayTracingPipeline;
import sp.sponge.render.vulkan.raytracing.accelstruct.BLAS;
import sp.sponge.render.vulkan.raytracing.accelstruct.TLAS;
import sp.sponge.render.vulkan.raytracing.shader.ShaderBindingTable;
import sp.sponge.render.vulkan.raytracing.shader.ShaderGroup;
import sp.sponge.render.vulkan.image.ImageView;
import sp.sponge.render.vulkan.sync.Fence;
import sp.sponge.scene.SceneManager;
import sp.sponge.scene.objects.SceneObject;
import sp.sponge.scene.objects.custom.obj.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;

public class MainRenderer {
    private static final String RGEN_SHADER_FILE_GLSL = "shaders/raytracing/rgen.glsl";
    private static final String RGEN_SHADER_FILE_SPV = RGEN_SHADER_FILE_GLSL + ".spv";
    private static final String MISS_SHADER_FILE_GLSL = "shaders/raytracing/miss.glsl";
    private static final String MISS_SHADER_FILE_SPV = MISS_SHADER_FILE_GLSL + ".spv";
    private static final String CHIT_SHADER_FILE_GLSL = "shaders/raytracing/chit.glsl";
    private static final String CHIT_SHADER_FILE_SPV = CHIT_SHADER_FILE_GLSL + ".spv";
    private static final String AHIT_SHADER_FILE_GLSL = "shaders/raytracing/ahit.glsl";
    private static final String AHIT_SHADER_FILE_SPV = AHIT_SHADER_FILE_GLSL + ".spv";

    private static final String VERTEX_DESC_SET = "vert_desc_set";
    private static final String TLAS_DESC_SET = "tlas_desc_set";
    private static final String SAMPLER_DESC_SET = "samp_desc_set";

    private static final String IMG_STORAGE_DESC_SET = "img_desc_set";
    private static final String PREV_SAMPLER_DESC_SET = "prev_sampler_desc_set";

    private final Camera camera;
    private final VulkanCtx vulkanCtx;
    private final Queue.GraphicsQueue graphicsQueue;
    private final CommandPool commandPool;
    private final List<CommandBuffer> commandBuffers = new ArrayList<>();

    private final Fence fences;

    private VkBuffer vertexUniformBuffer;
    private final DescriptorSets descriptorSets;
    private final MeshBuffers vertexMeshBuffers;
    private RayTracingPipeline pipeline;
    private ShaderBindingTable shaderBindingTable;

    private BLAS blas;
    private TLAS tlas;

    private final Interop interop;

    private boolean needsToResize;

    private static int frame;
    private static int currentFrame = 0;

    public MainRenderer() {
        this.camera = new Camera();
        this.vulkanCtx = new VulkanCtx();
        this.graphicsQueue = new Queue.GraphicsQueue(this.vulkanCtx, 1);
        this.commandPool = new CommandPool(this.vulkanCtx, this.graphicsQueue.getQueueFamilyIndex(), true);

        int numOfImages = 2;
        this.createCommandBuffers(numOfImages);

        this.fences = new Fence(this.vulkanCtx, true);

        this.vertexMeshBuffers = new MeshBuffers(this.vulkanCtx, 1000, 0, 50000000, VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR);

        this.descriptorSets = new DescriptorSets();
        this.interop = new Interop(this.vulkanCtx);
    }

    public void init() {
        this.initScene();
        TextureManager textureManager = Sponge.getInstance().getTextureManager();
        textureManager.sendAllTexturesToGpu(this.vulkanCtx, this.commandPool, this.graphicsQueue);

        DescriptorSets.Group defaultUniformGroup = this.descriptorSets.addDescriptorGroup(
                this.vulkanCtx,
                VERTEX_DESC_SET,
                VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                0,
                1,
                VK_SHADER_STAGE_RAYGEN_BIT_KHR | VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR | VK_SHADER_STAGE_MISS_BIT_KHR | VK_SHADER_STAGE_ANY_HIT_BIT_KHR
        );

        DescriptorSet defUniformDescSet = defaultUniformGroup.descriptorSet();
        DescriptorSetLayout defUniformDescLayout = defaultUniformGroup.layout();

        vertexUniformBuffer = VulkanUtils.createCpuBuffer(this.vulkanCtx, 292, VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
        defUniformDescSet.setBuffer(
                this.vulkanCtx,
                vertexUniformBuffer,
                vertexUniformBuffer.getRequestedSize(),
                defUniformDescLayout.getLayoutInfo().binding(),
                defUniformDescLayout.getLayoutInfo().descriptorType()
        );
        this.setVertexUniformsUniforms(vertexUniformBuffer);


        DescriptorSets.Group samplerSet = this.descriptorSets.addDescriptorGroup(
                this.vulkanCtx,
                SAMPLER_DESC_SET,
                VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0,
                1,
                VK_SHADER_STAGE_RAYGEN_BIT_KHR | VK_SHADER_STAGE_MISS_BIT_KHR
        );

        TextureSampler sampler = new TextureSampler.TextureSamplerBuilder().filter(VK10.VK_FILTER_LINEAR).build(this.vulkanCtx);
        samplerSet.descriptorSet().setImage(
                this.vulkanCtx,
                textureManager.getTexture("sky_gradient").getImageView(),
                sampler,
                VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0
        );

        DescriptorSetLayout imageLayout = this.createImageDescSet(
                this.interop.getVkFramebuffer().getImageView(), IMG_STORAGE_DESC_SET, VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, null);
        DescriptorSetLayout prevImageLayout = this.createImageDescSet(
                this.interop.getPrevVkFramebuffer().getImageView(), PREV_SAMPLER_DESC_SET, VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, sampler);

        ShaderModule[] shaderModules = createRTShaderModules();
        VkRayTracingShaderGroupCreateInfoKHR.Buffer groups = createShaderGroups();
        this.pipeline = this.createRTPipeline(
                shaderModules, groups, new DescriptorSetLayout[]{defUniformDescLayout, this.descriptorSets.getLayout(TLAS_DESC_SET), imageLayout, prevImageLayout, samplerSet.layout()});
        this.shaderBindingTable = createShaderBindingTable(groups.remaining());

        Arrays.asList(shaderModules).forEach(shaderModule -> shaderModule.free(this.vulkanCtx));
        groups.free();
    }

    private void initScene() {
        this.camera.updateCamera();
        Sponza sponza = new Sponza(false);
        sponza.getMaterial().setColor(1,1,1);
        SceneManager.addObject(sponza);

        Dragon dragon = new Dragon(false);
        dragon.getMaterial().setColor(1,1,1);
        dragon.getTransformations().scale(10);
        dragon.getTransformations().setPosition(0, 3, -3);
        SceneManager.addObject(dragon);

        Cube lightCube = new Cube(false);
        lightCube.getTransformations().setPosition(0, 20, -3);
        lightCube.getMaterial().setColor(0, 0, 0);
        lightCube.getMaterial().setEmissiveColor(0, 1, 0);
        lightCube.getTransformations().scale(5);
        lightCube.getMaterial().setEmissiveStrength(5);
        SceneManager.addObject(lightCube);

        Cube lightCube2 = new Cube(false);
        lightCube2.getTransformations().setPosition(20, 20, -3);
        lightCube2.getMaterial().setColor(0, 0, 0);
        lightCube2.getMaterial().setEmissiveColor(1, 0, 0);
        lightCube2.getTransformations().scale(5);
        lightCube2.getMaterial().setEmissiveStrength(5);
        SceneManager.addObject(lightCube2);

        Cube lightCube3 = new Cube(false);
        lightCube3.getTransformations().setPosition(-20, 20, -3);
        lightCube3.getMaterial().setColor(0, 0, 0);
        lightCube3.getMaterial().setEmissiveColor(0, 0, 1);
        lightCube3.getTransformations().scale(5);
        lightCube3.getMaterial().setEmissiveStrength(5);
        SceneManager.addObject(lightCube3);


//        Square floor = new Square(false);
//        floor.getTransformations().setPosition(0, -0.5f, 0);
//        floor.getTransformations().rotate(90, 0, 0);
//        floor.getMaterial().setColor(1, 1, 1);
//        SceneManager.addObject(floor);
//
//        Square backWall = new Square(false);
//        backWall.getTransformations().setPosition(0, 0, -0.5f);
//        backWall.getTransformations().rotate(0, 0, 0);
//        backWall.getMaterial().setColor(1, 1, 1);
//        SceneManager.addObject(backWall);
//
//        Square redWall = new Square(false);
//        redWall.getTransformations().setPosition(-0.5f, 0, 0);
//        redWall.getTransformations().rotate(0, 90, 0);
//        redWall.getMaterial().setColor(1, 0, 0);
//        SceneManager.addObject(redWall);
//
//        Square greenWall = new Square(false);
//        greenWall.getTransformations().setPosition(0.5f, 0, 0);
//        greenWall.getTransformations().rotate(0, -90, 0);
//        greenWall.getMaterial().setColor(0, 1, 0);
//        SceneManager.addObject(greenWall);
//
//        Square ceiling = new Square(false);
//        ceiling.getTransformations().setPosition(0, 0.5f, 0);
//        ceiling.getTransformations().rotate(90, 0, 0);
//        ceiling.getMaterial().setColor(1, 1, 1);
//        ceiling.getMaterial().setEmissiveColor(1, 1, 1);
//        ceiling.getMaterial().setEmissiveStrength(5);
//        SceneManager.addObject(ceiling);
//
//        Sphere sphere = new Sphere(false);
//        sphere.getTransformations().setPosition(0, -0.3f, 0);
//        sphere.getTransformations().scale(0.2f);
//        sphere.getMaterial().setColor(1, 1, 1);
//        SceneManager.addObject(sphere);

        this.updateObjects(this.vulkanCtx, this.commandPool, this.graphicsQueue);

        this.blas = new BLAS(this.vulkanCtx, this.vertexMeshBuffers, this.commandPool, this.graphicsQueue);
        this.tlas = new TLAS(this.vulkanCtx, new BLAS[]{this.blas}, this.commandPool, this.graphicsQueue);

        DescriptorSets.Group group = this.descriptorSets.addDescriptorGroup(
                this.vulkanCtx,
                TLAS_DESC_SET,
                KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
                0,
                1,
                VK_SHADER_STAGE_RAYGEN_BIT_KHR
        );

        DescriptorSetLayout.LayoutInfo layoutInfo = group.layout().getLayoutInfo();
        group.descriptorSet().setTLAS(this.vulkanCtx, layoutInfo.binding(), layoutInfo.descriptorType(), this.tlas);
    }

    private DescriptorSetLayout createImageDescSet(ImageView imageView, String name, int descriptorType, @Nullable TextureSampler sampler) {
        DescriptorSetLayout layout = new DescriptorSetLayout(
                this.vulkanCtx,
                new DescriptorSetLayout.LayoutInfo(
                        descriptorType,
                        0,
                        1,
                        VK_SHADER_STAGE_RAYGEN_BIT_KHR
                )
        );

        DescriptorSets.Group group = this.descriptorSets.addDescriptorGroup(this.vulkanCtx, name, layout);
        group.descriptorSet().setImage(this.vulkanCtx, imageView, sampler, descriptorType, layout.getLayoutInfo().binding());

        return layout;
    }

    private RayTracingPipeline createRTPipeline(ShaderModule[] shaderModules, VkRayTracingShaderGroupCreateInfoKHR.Buffer groups, DescriptorSetLayout[] layouts) {
        return new RayTracingPipeline.Builder(shaderModules, groups)
                .setDescriptorSetLayouts(layouts)
                .build(this.vulkanCtx);
    }

    private ShaderModule[] createRTShaderModules() {
        ShaderCompiler.compiledShaderIfChanged(RGEN_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_raygen_shader);
        ShaderCompiler.compiledShaderIfChanged(MISS_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_miss_shader);
        ShaderCompiler.compiledShaderIfChanged(CHIT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_closesthit_shader);
        ShaderCompiler.compiledShaderIfChanged(AHIT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_anyhit_shader);

        return new ShaderModule[] {
                new ShaderModule(this.vulkanCtx, VK_SHADER_STAGE_RAYGEN_BIT_KHR, RGEN_SHADER_FILE_SPV),
                new ShaderModule(this.vulkanCtx, VK_SHADER_STAGE_MISS_BIT_KHR, MISS_SHADER_FILE_SPV),
                new ShaderModule(this.vulkanCtx, VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR, CHIT_SHADER_FILE_SPV),
                new ShaderModule(this.vulkanCtx, VK_SHADER_STAGE_ANY_HIT_BIT_KHR, AHIT_SHADER_FILE_SPV)
        };
    }

    private VkRayTracingShaderGroupCreateInfoKHR.Buffer createShaderGroups() {
        VkRayTracingShaderGroupCreateInfoKHR.Buffer result = VkRayTracingShaderGroupCreateInfoKHR.calloc(3);

        //Should be the same order that they were put into the shader modules
        //Ray Gen
        result.get(0)
                .sType$Default()
                .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
                .generalShader(0)
                .closestHitShader(VK_SHADER_UNUSED_KHR)
                .anyHitShader(VK_SHADER_UNUSED_KHR)
                .intersectionShader(VK_SHADER_UNUSED_KHR);

        //Miss
        result.get(1)
                .sType$Default()
                .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
                .generalShader(1)
                .closestHitShader(VK_SHADER_UNUSED_KHR)
                .anyHitShader(VK_SHADER_UNUSED_KHR)
                .intersectionShader(VK_SHADER_UNUSED_KHR);

        //Hit groups
        result.get(2)
                .sType$Default()
                .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR)
                .generalShader(VK_SHADER_UNUSED_KHR)
                .closestHitShader(2)
                .anyHitShader(3)
                .intersectionShader(VK_SHADER_UNUSED_KHR);

        return result;
    }

    private ShaderBindingTable createShaderBindingTable(int numOfGroups) {
        ShaderBindingTable result;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceRayTracingPipelinePropertiesKHR properties = this.vulkanCtx.getPhysicalDevice().getRayTracingProperties();
            int groupSize = properties.shaderGroupHandleSize();
            int alignment = properties.shaderGroupHandleAlignment();
            int alignedHandle = VulkanUtils.alignSize(groupSize, alignment);
            int size = numOfGroups * alignedHandle;

            ByteBuffer byteBuffer = stack.calloc(size);

            KHRRayTracingPipeline.vkGetRayTracingShaderGroupHandlesKHR(
                    this.vulkanCtx.getLogicalDevice().getVkDevice(),
                    this.pipeline.getVkPipeline(),
                    0,
                    numOfGroups,
                    byteBuffer
            );

            result = new ShaderBindingTable(
                    new ShaderGroup(this.vulkanCtx),
                    new ShaderGroup(this.vulkanCtx),
                    new ShaderGroup(this.vulkanCtx)
            );

            int offset = 0;
            VulkanUtils.copyByteBufferToVkBuffer(this.vulkanCtx, byteBuffer, offset, result.rayGen().getBuffer(), 0, groupSize);
            offset += alignedHandle;
            VulkanUtils.copyByteBufferToVkBuffer(this.vulkanCtx, byteBuffer, offset, result.miss().getBuffer(), 0, groupSize);
            offset += alignedHandle;
            VulkanUtils.copyByteBufferToVkBuffer(this.vulkanCtx, byteBuffer, offset, result.hit().getBuffer(), 0, groupSize);
        }
        return result;
    }

    public void render() {
        GL11.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        Window window = Window.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        if (this.needsToResize) {
            this.resize();
        }
        this.camera.updateCamera();
        this.setVertexUniformsUniforms(vertexUniformBuffer);
        CommandBuffer buffer = commandBuffers.get(currentFrame);

        this.fences.waitForFence(this.vulkanCtx);

        buffer.reset();
        buffer.beginRecordingPrimary();
        this.renderScene(buffer.getVkCommandBuffer());
        buffer.endRecording();
        this.submit(buffer);

        EXTSemaphore.glWaitSemaphoreEXT(this.interop.getCompleteSemaphorePair().glSemaphore(), new int[]{0}, new int[] {this.interop.getGlFramebuffer().getColorAttachment()}, new int[]{EXTSemaphore.GL_LAYOUT_COLOR_ATTACHMENT_EXT});

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.interop.getGlFramebuffer().getFramebuffer());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);


        frame++;
        if (this.camera.hasMoved()) {
            frame = 0;
        }
        currentFrame = (currentFrame + 1) % (VulkanUtils.MAX_FRAMES_IN_FLIGHT + 1);
    }

    public void renderScene(VkCommandBuffer buffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Window window = Window.getWindow();
            long swapChainImage = this.interop.getVkFramebuffer().getImageView().getImageHandle();

            VulkanUtils.imageBarrier(stack, buffer, swapChainImage,
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    VK10.VK_IMAGE_LAYOUT_GENERAL,
                    VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                    VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR,
                    VK13.VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK13.VK_ACCESS_SHADER_READ_BIT | VK13.VK_ACCESS_SHADER_WRITE_BIT,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
            );

            VK10.vkCmdBindPipeline(buffer, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, this.pipeline.getVkPipeline());

            DescriptorAllocator allocator = this.vulkanCtx.getDescriptorAllocator();
            LongBuffer descriptorSets = stack.mallocLong(5)
                    .put(0, allocator.getDescriptorSet(VERTEX_DESC_SET).getVkDescriptorSet())
                    .put(1, allocator.getDescriptorSet(TLAS_DESC_SET).getVkDescriptorSet())
                    .put(2, allocator.getDescriptorSet(IMG_STORAGE_DESC_SET).getVkDescriptorSet())
                    .put(3, allocator.getDescriptorSet(PREV_SAMPLER_DESC_SET).getVkDescriptorSet())
                    .put(4, allocator.getDescriptorSet(SAMPLER_DESC_SET).getVkDescriptorSet());
            VK10.vkCmdBindDescriptorSets(
                    buffer,
                    VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR,
                    this.pipeline.getVkPipelineLayout(),
                    0,
                    descriptorSets,
                    null
            );

            vkCmdTraceRaysKHR(
                    buffer,
                    shaderBindingTable.rayGen().getDeviceAddressRegion(),
                    shaderBindingTable.miss().getDeviceAddressRegion(),
                    shaderBindingTable.hit().getDeviceAddressRegion(),
                    VkStridedDeviceAddressRegionKHR.calloc(stack),
                    window.getWidth(),
                    window.getHeight(),
                    1
            );

            VulkanUtils.imageBarrier(stack, buffer, swapChainImage,
                    VK10.VK_IMAGE_LAYOUT_GENERAL,
                    VK10.VK_IMAGE_LAYOUT_GENERAL,
                    VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                    VK13.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK13.VK_PIPELINE_STAGE_2_NONE,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT
            );

            this.copyToPrevBuffer(buffer, stack, this.interop.getVkFramebuffer(), this.interop.getPrevVkFramebuffer());
        }
    }

    public void copyToPrevBuffer(VkCommandBuffer commandBuffer, MemoryStack stack, Attachment srcAttachment, Attachment dstAttachment) {
        Image srcImage = srcAttachment.getImage();
        Image dstImage = dstAttachment.getImage();
        ImageView srcImageView = srcAttachment.getImageView();
        ImageView dstImageView = dstAttachment.getImageView();

        VkImageCopy.Buffer imageCopies = VkImageCopy.calloc(1, stack)
                .srcOffset(vkOffset3D -> vkOffset3D.set(0, 0, 0))
                .dstOffset(vkOffset3D -> vkOffset3D.set(0, 0, 0))
                .extent(vkExtent3D -> vkExtent3D.set(dstImage.getWidth(), dstImage.getHeight(), 1))
                .srcSubresource(vkImageSubresourceLayers ->
                        vkImageSubresourceLayers
                                .layerCount(srcImageView.getMipLevels())
                                .aspectMask(srcImageView.getAspectMask())
                                .baseArrayLayer(srcImageView.getBaseArrayLayer()))
                .dstSubresource(vkImageSubresourceLayers ->
                        vkImageSubresourceLayers
                                .layerCount(dstImageView.getMipLevels())
                                .aspectMask(dstImageView.getAspectMask())
                                .baseArrayLayer(dstImageView.getBaseArrayLayer()));

        VK11.vkCmdCopyImage(commandBuffer, srcImage.getVkImage(), VK10.VK_IMAGE_LAYOUT_GENERAL, dstImage.getVkImage(), VK10.VK_IMAGE_LAYOUT_GENERAL, imageCopies);
    }

    public void updateObjects(VulkanCtx ctx, CommandPool commandPool, Queue queue) {
        CommandBuffer cmdBuffer = new CommandBuffer(ctx, commandPool, true, true);

        cmdBuffer.beginRecordingPrimary();

        this.vertexMeshBuffers.startMapping();
        int meshIndex = 0;
        for (SceneObject sceneObject : SceneManager.getSceneObjects()) {
            this.vertexMeshBuffers.putMesh(sceneObject.getTransformMatrix(), sceneObject.getMesh(), meshIndex);
            meshIndex++;
        }
        this.vertexMeshBuffers.stopMapping();

        this.vertexMeshBuffers.sendVerticesToGpu(cmdBuffer);

        cmdBuffer.endRecording();
        cmdBuffer.submitAndWait(ctx, queue);
        cmdBuffer.close(ctx, commandPool);
    }

    private void submit(CommandBuffer buffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.fences.reset(this.vulkanCtx);
            VkCommandBufferSubmitInfo.Buffer cmdSubmitBuffer = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(buffer.getVkCommandBuffer());

            VkSemaphoreSubmitInfo.Buffer waitSemaphores = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .semaphore(this.interop.getReadySemaphorePair().vkSemaphore().getVkSemaphore());

            VkSemaphoreSubmitInfo.Buffer signalSemaphores = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
                    .semaphore(this.interop.getCompleteSemaphorePair().vkSemaphore().getVkSemaphore());

            graphicsQueue.submit(cmdSubmitBuffer, null, signalSemaphores, this.fences);
        }
    }

    private void createCommandBuffers(int num) {
        for (int i = 0; i < num; i++) {
            commandBuffers.add(new CommandBuffer(this.vulkanCtx, this.commandPool, true, true));
        }
    }

    private void setVertexUniformsUniforms(VkBuffer vkBuffer) {
        ByteBuffer buffer = vkBuffer.map(this.vulkanCtx);

        UniformBlockUtil uniformBlock = new UniformBlockUtil(buffer);

        uniformBlock.putMatrix4f(this.camera.getProjectionMatrix());
        uniformBlock.putMatrix4f(this.camera.getModelViewMatrix());
        uniformBlock.putMatrix4f(this.camera.getInvProjectionMatrix());
        uniformBlock.putMatrix4f(this.camera.getInvModelViewMatrix());

        uniformBlock.putVec3f(this.camera.getPosition());
        uniformBlock.putLong(this.vertexMeshBuffers.getVertexBuffers().getGpuAddress(this.vulkanCtx));
        uniformBlock.putLong(this.vertexMeshBuffers.getMeshBuffers().getGpuAddress(this.vulkanCtx));

        uniformBlock.putFloat((float) frame);
        //284

        vkBuffer.unmap(this.vulkanCtx);
    }

    private void resize() {
        this.needsToResize = false;
        this.vulkanCtx.getLogicalDevice().waitIdle();

        this.vulkanCtx.resize();

        this.interop.resize(this.vulkanCtx);
    }

    public VulkanCtx getVulkanCtx() {
        return vulkanCtx;
    }

    public void close() {
        this.descriptorSets.free(this.vulkanCtx);
        TextureSampler.samplers.forEach(sampler -> sampler.free(this.vulkanCtx));
        this.interop.free(this.vulkanCtx);
        this.shaderBindingTable.free(this.vulkanCtx);
        this.pipeline.free(this.vulkanCtx);
        this.vertexUniformBuffer.free(this.vulkanCtx);
        this.tlas.free(this.vulkanCtx);
        this.blas.free(this.vulkanCtx);
        this.vertexMeshBuffers.free();
        this.fences.close(this.vulkanCtx);
        this.commandPool.close();
        this.vulkanCtx.free();
    }
}
