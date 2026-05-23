package sp.sponge.scene.objects;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import sp.sponge.render.vulkan.model.Mesh;
import sp.sponge.scene.registries.custom.object.ObjectType;
import sp.sponge.util.objects.Material;
import sp.sponge.util.objects.Transformation;

public abstract class SceneObject {
    protected final String name;
    protected transient Mesh mesh;
    protected boolean fixed;
    protected Transformation transformation;
    protected final Material material;
    private boolean dirty;

    public SceneObject(ObjectType<SceneObject> objectType, boolean fixed) {
        this(objectType, new Transformation(), fixed);
    }

    public SceneObject(ObjectType<SceneObject> objectType, Transformation transformation, boolean fixed) {
        this.name = objectType.getName();
        this.fixed = fixed;
        this.transformation = transformation;
        this.material = new Material();
    }

    public abstract Mesh createMesh();

    public Mesh getMesh() {
        if (this.mesh == null || this.isDirty()) {
            this.mesh = this.createMesh();
            this.clean();
        }

        this.mesh.setMaterial(this.material);
        return this.mesh;
    }

    public Material getMaterial() {
        return material;
    }

    public Transformation getTransformations() {
        return this.transformation;
    }

    public Matrix4f getTransformMatrix() {
        return this.transformation.getTransformationMatrix();
    }

    public boolean shouldUseResolution() {
        return this instanceof ObjectWithResolution;
    }

    public boolean isFixed() {
        return this.fixed;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clean() {
        this.dirty = false;
    }

    public boolean isDirty() {
        return this.dirty;
    }

}
