package sp.sponge.util.objects;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Transformation {
    private Vector3f translation;
    private Quaternionf rotation;
    private Vector3f scale;

    public Transformation() {
        this(new Vector3f(0.0f, 0.0f, 0.0f), new Quaternionf(), new Vector3f(1.0f));
    }

    public Transformation(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;
    }


    public Vector3f getScale() {
        return this.scale;
    }

    public void scale(float value) {
        this.scale(new Vector3f(value, value, value));
    }

    public void scale(float x, float y, float z) {
        this.scale(new Vector3f(x, y, z));
    }

    public void scale(Vector3f scale) {
        this.scale = scale;
    }


    public Quaternionf getRotation() {
        return this.rotation;
    }

    public void setRotation(float x, float y, float z) {
        this.rotation = new Quaternionf().rotateXYZ((float) Math.toRadians(x), (float) Math.toRadians(y), (float) Math.toRadians(z));
    }

    public void rotate(float x, float y, float z) {
        this.rotation.rotateXYZ((float) Math.toRadians(x), (float) Math.toRadians(y), (float) Math.toRadians(z));
    }


    public Vector3f getPosition() {
        return this.translation;
    }

    public void setPosition(float x, float y, float z) {
        this.setPosition(new Vector3f(x, y, z));
    }

    public void setPosition(Vector3f position) {
        this.translation = position;
    }

    public Matrix4f getTransformationMatrix() {
        return new Matrix4f().translationRotateScale(this.translation, this.rotation, this.scale);
    }
}
