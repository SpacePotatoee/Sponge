package sp.sponge.render.vulkan.buffer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import sp.sponge.render.vulkan.model.Mesh;
import sp.sponge.render.vulkan.VulkanCtx;
import sp.sponge.render.vulkan.device.command.CommandBuffer;

import java.nio.ByteBuffer;

public class MeshBuffers {
    private final BufferSet meshBuffers;
    private final BufferSet vertexBuffers;
    private int numOfTriangles;

    public MeshBuffers(VulkanCtx ctx, int meshSize, int meshUsage, int vertSize, int vertUsage) {
        this.meshBuffers = new BufferSet(ctx, meshSize, meshUsage);
        this.vertexBuffers = new BufferSet(ctx, vertSize, vertUsage);
    }

    public void putMesh(Matrix4f transform, Mesh mesh, int num) {
        ByteBuffer meshBuffer = meshBuffers.getBuffer();
        ByteBuffer vertBuffer = vertexBuffers.getBuffer();
        if (vertBuffer == null) return;

        Vector3f color = mesh.getColor();
        meshBuffer.putFloat(color.x);
        meshBuffer.putFloat(color.y);
        meshBuffer.putFloat(color.z);
        meshBuffer.putFloat(0);

        Vector3f emissiveColor = mesh.getEmissiveColor();
        meshBuffer.putFloat(emissiveColor.x);
        meshBuffer.putFloat(emissiveColor.y);
        meshBuffer.putFloat(emissiveColor.z);
        meshBuffer.putFloat(mesh.getMaterial().getEmissiveStrength());

        for (Mesh.Face face : mesh.getFaces()) {
            Mesh.Vertex v1 = face.v1();
            Vector3f pointA = transform.transformPosition(v1.x(), v1.y(), v1.z(), new Vector3f());
            vertBuffer.putFloat(pointA.x());
            vertBuffer.putFloat(pointA.y());
            vertBuffer.putFloat(pointA.z());
            vertBuffer.putInt(num);

            vertBuffer.putFloat(v1.normalX());
            vertBuffer.putFloat(v1.normalY());
            vertBuffer.putFloat(v1.normalZ());
            vertBuffer.putFloat(0);


            Mesh.Vertex v2 = face.v2();
            Vector3f pointB = transform.transformPosition(v2.x(), v2.y(), v2.z(), new Vector3f());
            vertBuffer.putFloat(pointB.x());
            vertBuffer.putFloat(pointB.y());
            vertBuffer.putFloat(pointB.z());
            vertBuffer.putFloat(0);

            vertBuffer.putFloat(v2.normalX());
            vertBuffer.putFloat(v2.normalY());
            vertBuffer.putFloat(v2.normalZ());
            vertBuffer.putFloat(0);


            Mesh.Vertex v3 = face.v3();
            Vector3f pointC = transform.transformPosition(v3.x(), v3.y(), v3.z(), new Vector3f());
            vertBuffer.putFloat(pointC.x());
            vertBuffer.putFloat(pointC.y());
            vertBuffer.putFloat(pointC.z());
            vertBuffer.putFloat(0);

            vertBuffer.putFloat(v3.normalX());
            vertBuffer.putFloat(v3.normalY());
            vertBuffer.putFloat(v3.normalZ());
            vertBuffer.putFloat(0);
            this.numOfTriangles++;
        }
    }

    public void startMapping() {
        this.vertexBuffers.startMapping();
        this.meshBuffers.startMapping();
    }

    public void stopMapping() {
        this.vertexBuffers.stopMapping();
        this.meshBuffers.stopMapping();
    }

    public void sendVerticesToGpu(CommandBuffer commandBuffer) {
        this.vertexBuffers.sendDataToGpu(commandBuffer);
        this.meshBuffers.sendDataToGpu(commandBuffer);
    }

    public int getNumOfTriangles() {
        return numOfTriangles;
    }

    public void reset() {
        this.numOfTriangles = 0;
//        meshBuffers.getBuffer().clear();
//        vertexBuffers.getBuffer().clear();
    }

    public void free() {
        this.meshBuffers.free();
        this.vertexBuffers.free();
    }

    public BufferSet getMeshBuffers() {
        return meshBuffers;
    }

    public BufferSet getVertexBuffers() {
        return vertexBuffers;
    }
}
