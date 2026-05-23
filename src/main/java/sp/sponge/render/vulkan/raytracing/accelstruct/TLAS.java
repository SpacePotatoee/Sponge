package sp.sponge.render.vulkan.raytracing.accelstruct;

import org.joml.Matrix4x3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.VulkanUtils;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBuffer;
import sp.sponge.render.vulkan.buffer.vkbuffer.VkBufferImpl;
import sp.sponge.render.vulkan.device.Queue;
import sp.sponge.render.vulkan.device.command.CommandBuffer;
import sp.sponge.render.vulkan.device.command.CommandPool;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.KHRAccelerationStructure.*;

public class TLAS {
    private final long asHandle;
    private final long asDeviceAddress;
    private final VkAccelerationStructureInstanceKHR.Buffer blasInstances;
    private final VkBuffer tlasBuffer;

    public TLAS(VulkanCtx ctx, BLAS[] blases, CommandPool commandPool, Queue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDevice logicalDevice = ctx.getLogicalDevice().getVkDevice();
            int numOfBlases = blases.length;

            //Put all the Bottom Level AS into a buffer
            blasInstances = VkAccelerationStructureInstanceKHR.calloc(numOfBlases);
            for (int i = 0; i < numOfBlases; i++) {
                BLAS blas = blases[i];

                Matrix4x3f matrix4x3f = new Matrix4x3f().identity();
                VkTransformMatrixKHR transformMatrix = VkTransformMatrixKHR.calloc(stack)
                        .matrix(matrix4x3f.getTransposed(stack.callocFloat(12)));

                blasInstances.get(i)
                        .mask(0xFF)
                        .instanceShaderBindingTableRecordOffset(0)
                        .flags(VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR)
                        .accelerationStructureReference(blas.getAsDeviceAddress())
                        .transform(transformMatrix);
            }



            //Transfer that BLASes data into a VkBuffer
            ByteBuffer instanceDataBB = MemoryUtil.memByteBuffer(blasInstances);
            int size = instanceDataBB.remaining();
            VkBufferImpl instanceDataVkB = new VkBufferImpl(ctx, size,
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);

            VulkanUtils.copyByteBufferToVkBuffer(ctx, instanceDataBB, 0, instanceDataVkB, 0, size);

            //Start recording for later
            CommandBuffer commandBuffer = new CommandBuffer(ctx, commandPool, true, true);
            commandBuffer.beginRecordingPrimary();

            //Memory Barrier (Kinda like the i mage barrier)
            VkMemoryBarrier2.Buffer memoryBarrier = VkMemoryBarrier2.calloc(1, stack)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .dstStageMask(VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR);
            VkDependencyInfo memDepInfo = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pMemoryBarriers(memoryBarrier);

            VK13.vkCmdPipelineBarrier2(commandBuffer.getVkCommandBuffer(), memDepInfo);



            VkDeviceOrHostAddressConstKHR instanceDataAddress = VulkanUtils.getBufferGpuAddressConst(ctx, stack, instanceDataVkB.getBufferPtr());

            //BLASes are sent as instances instead of as geometry (triangles). The address for those instances are passed through
            VkAccelerationStructureGeometryInstancesDataKHR instancesDataKHR = VkAccelerationStructureGeometryInstancesDataKHR.calloc(stack)
                    .sType$Default()
                    .data(instanceDataAddress);

            //This is the same type of buffer that was used to put triangles into a BLAS.
            //For TLASes, instead of triangles as geometry, BLASes are passed through
            VkAccelerationStructureGeometryKHR.Buffer geometryKHR = VkAccelerationStructureGeometryKHR.calloc(1, stack)
                    .sType$Default()
                    .geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR);
            geometryKHR.geometry().instances(instancesDataKHR);

            VkAccelerationStructureBuildGeometryInfoKHR.Buffer geometryInfoKHR = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                    .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR | VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_UPDATE_BIT_KHR)
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
                    .geometryCount(1)
                    .pGeometries(geometryKHR);

            VkAccelerationStructureBuildSizesInfoKHR buildSizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
                    .sType$Default();
            vkGetAccelerationStructureBuildSizesKHR(
                    logicalDevice,
                    VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    geometryInfoKHR.get(0),
                    stack.ints(1),
                    buildSizes
            );



            //Create the actual AS
            this.tlasBuffer = new VkBufferImpl(ctx, buildSizes.accelerationStructureSize(),
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR | VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, 0);

            VkAccelerationStructureCreateInfoKHR asCreateInfo = VkAccelerationStructureCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                    .buffer(this.tlasBuffer.getBufferPtr())
                    .size(this.tlasBuffer.getRequestedSize());

            LongBuffer longBuffer = stack.callocLong(1);
            VulkanUtils.check(
                    vkCreateAccelerationStructureKHR(logicalDevice, asCreateInfo, null, longBuffer),
                    "Failed to create TLAS"
            );
            this.asHandle = longBuffer.get(0);



            //======================================================================================================//
            //======================================================================================================//
            //Now Build the AS
            VkBufferImpl tempBuffer = new VkBufferImpl(ctx, buildSizes.buildScratchSize(),
                    VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, 0);

            geometryInfoKHR
                    .srcAccelerationStructure(VK10.VK_NULL_HANDLE)
                    .dstAccelerationStructure(this.asHandle);
            geometryInfoKHR.scratchData().deviceAddress(VulkanUtils.getBufferGpuAddress(ctx, stack, tempBuffer.getBufferPtr()));

            VkAccelerationStructureBuildRangeInfoKHR.Buffer buildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack);
            buildRangeInfo.get(0)
                    .firstVertex(0)
                    .primitiveOffset(0)
                    .transformOffset(0)
                    .primitiveCount(1);

            vkCmdBuildAccelerationStructuresKHR(commandBuffer.getVkCommandBuffer(), geometryInfoKHR, stack.pointersOfElements(buildRangeInfo));
            commandBuffer.endRecording();
            commandBuffer.submitAndWait(ctx, queue);


            //Cleanup
            commandBuffer.close(ctx, commandPool);
            tempBuffer.free(ctx);
            instanceDataVkB.free(ctx);


            //Get the device address
            VkAccelerationStructureDeviceAddressInfoKHR deviceAddressInfo = VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack)
                    .sType$Default()
                    .accelerationStructure(this.asHandle);
            this.asDeviceAddress = vkGetAccelerationStructureDeviceAddressKHR(logicalDevice, deviceAddressInfo);
        }
    }

    public long getAsHandle() {
        return asHandle;
    }

    public long getAsDeviceAddress() {
        return asDeviceAddress;
    }

    public void free(VulkanCtx ctx) {
        vkDestroyAccelerationStructureKHR(ctx.getLogicalDevice().getVkDevice(), this.asHandle, null);
        tlasBuffer.free(ctx);
        MemoryUtil.memFree(blasInstances);
    }
}
