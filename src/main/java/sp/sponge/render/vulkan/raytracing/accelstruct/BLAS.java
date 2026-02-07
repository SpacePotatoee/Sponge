package sp.sponge.render.vulkan.raytracing.accelstruct;

import org.joml.Matrix4x3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.vulkan.*;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.buffer.MeshBuffers;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBuffer;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBufferImpl;
import sp.sponge.render.vulkan.device.Queue;
import sp.sponge.render.vulkan.device.command.CommandBuffer;
import sp.sponge.render.vulkan.device.command.CommandPool;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.KHRAccelerationStructure.*;

public class BLAS {
    private static final int STRIDE = Float.BYTES*8;
    private final long asHandle;
    private final long asDeviceAddress;
    private final VkBuffer blasBuffer;

    public BLAS(VulkanCtx ctx, MeshBuffers meshBuffers, CommandPool commandPool, Queue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int numOfTriangles = meshBuffers.getNumOfTriangles();
            VkDevice logicalDevice = ctx.getLogicalDevice().getVkDevice();

            //Add geometry to the acceleration structure
            VkAccelerationStructureGeometryKHR.Buffer geometry = VkAccelerationStructureGeometryKHR.calloc(1, stack);

            VkDeviceOrHostAddressConstKHR vertexAddress = VulkanUtils.getBufferGpuAddressConst(ctx, stack, meshBuffers.getVertexBuffers().getGpuBuffer());

            VkBuffer transformBuffer = createTransformBuffer(ctx);
            VkDeviceOrHostAddressConstKHR transformAddress = VulkanUtils.getBufferGpuAddressConst(ctx, stack, transformBuffer.getBufferPtr());

            geometry.get(0)
                    .sType$Default()
                    .geometryType(VK_GEOMETRY_TYPE_TRIANGLES_KHR)
                    .flags(VK_GEOMETRY_NO_DUPLICATE_ANY_HIT_INVOCATION_BIT_KHR)
                    .geometry().triangles()
                        .sType$Default()
                        .vertexFormat(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                        .vertexData(vertexAddress)
                        .maxVertex(numOfTriangles * 3)
                        .vertexStride(STRIDE)
                        .indexType(VK_INDEX_TYPE_NONE_KHR)
                        .transformData(transformAddress);


            //Get the size requirements for the buffers
            VkAccelerationStructureBuildGeometryInfoKHR geometryInfoKHR = VkAccelerationStructureBuildGeometryInfoKHR.calloc(stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
                    .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                    .geometryCount(1)
                    .pGeometries(geometry);

            IntBuffer primCountArray = stack.mallocInt(1)
                    .put(0, numOfTriangles);

            VkAccelerationStructureBuildSizesInfoKHR buildSizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
                    .sType$Default();
            vkGetAccelerationStructureBuildSizesKHR(
                    logicalDevice,
                    VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    geometryInfoKHR,
                    primCountArray,
                    buildSizes
            );


            //Create the buffer that will hold the Acceleration Structure
            blasBuffer = new VkBufferImpl(ctx, buildSizes.accelerationStructureSize(),
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR | VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, 0);


            //Create the acceleration structure
            VkAccelerationStructureCreateInfoKHR asCreateInfo = VkAccelerationStructureCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
                    .buffer(blasBuffer.getBufferPtr())
                    .size(blasBuffer.getRequestedSize());

            LongBuffer longBuffer = stack.mallocLong(1);
            VulkanUtils.check(
                    vkCreateAccelerationStructureKHR(logicalDevice, asCreateInfo, null, longBuffer),
                    "Failed to create acceleration structure"
            );
            this.asHandle = longBuffer.get(0);


            //Now build the Acceleration structure
            VkBufferImpl tempBuffer = new VkBufferImpl(ctx, buildSizes.buildScratchSize(),
                    VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, 0);

            VkAccelerationStructureBuildGeometryInfoKHR.Buffer buildGeometryInfoKHR = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
                    .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
                    .dstAccelerationStructure(this.asHandle)
                    .geometryCount(1)
                    .pGeometries(geometry);
            buildGeometryInfoKHR.scratchData().deviceAddress(VulkanUtils.getBufferGpuAddress(ctx, stack, tempBuffer.getBufferPtr()));

            VkAccelerationStructureBuildRangeInfoKHR.Buffer buildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack);
            buildRangeInfo.get(0)
                    .firstVertex(0)
                    .primitiveOffset(0)
                    .transformOffset(0)
                    .primitiveCount(numOfTriangles);


            CommandBuffer commandBuffer = new CommandBuffer(ctx, commandPool, true, true);
            commandBuffer.beginRecordingPrimary();

            vkCmdBuildAccelerationStructuresKHR(commandBuffer.getVkCommandBuffer(), buildGeometryInfoKHR, stack.pointers(buildRangeInfo));

            commandBuffer.endRecording();
            commandBuffer.submitAndWait(ctx, queue);


            //Get gpu memory address
            VkAccelerationStructureDeviceAddressInfoKHR addressInfoKHR = VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack)
                    .sType$Default()
                    .accelerationStructure(this.asHandle);

            this.asDeviceAddress = KHRAccelerationStructure.vkGetAccelerationStructureDeviceAddressKHR(logicalDevice, addressInfoKHR);

            commandBuffer.close(ctx, commandPool);
            transformBuffer.free(ctx);
            tempBuffer.free(ctx);
        }
    }

    private VkBuffer createTransformBuffer(VulkanCtx ctx) {
        VkBufferImpl transformBuffer = new VkBufferImpl(ctx, Float.BYTES * 12,
                VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        //No transforms
        Matrix4x3f matrix = new Matrix4x3f().identity();
        ByteBuffer byteBuffer = transformBuffer.map(ctx);
        matrix.getTransposed(0, byteBuffer);
        transformBuffer.unmap(ctx);

        return transformBuffer;
    }

    public long getAsDeviceAddress() {
        return asDeviceAddress;
    }

    public void free(VulkanCtx ctx) {
        KHRAccelerationStructure.vkDestroyAccelerationStructureKHR(ctx.getLogicalDevice().getVkDevice(), this.asHandle,  null);
        blasBuffer.free(ctx);

    }
}
