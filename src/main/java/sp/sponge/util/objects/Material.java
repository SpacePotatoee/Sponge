package sp.sponge.util.objects;

import org.joml.Vector3f;

public class Material {
    private Vector3f color;
    private Vector3f emissiveColor;
    private float emissiveStrength;

    public Material() {
        this(new Vector3f(1.0f), new Vector3f(0.0f), 0.0f);
    }

    public Material(Vector3f color, Vector3f emissiveColor, float emissiveStrength) {
        this.color = color;
        this.emissiveColor = emissiveColor;
        this.emissiveStrength = emissiveStrength;
    }

    public Vector3f getColor() {
        return color;
    }

    public void setColor(float red, float green, float blue) {
        this.setColor(new Vector3f(red, green, blue));
    }

    public void setColor(Vector3f color) {
        this.color = color;
    }


    public Vector3f getEmissiveColor() {
        return emissiveColor;
    }

    public void setEmissiveColor(float red, float green, float blue) {
        this.setEmissiveColor(new Vector3f(red, green, blue));
    }

    public void setEmissiveColor(Vector3f emissiveColor) {
        this.emissiveColor = emissiveColor;
    }


    public float getEmissiveStrength() {
        return emissiveStrength;
    }

    public void setEmissiveStrength(float emissiveStrength) {
        this.emissiveStrength = emissiveStrength;
    }

    public void copyValues(Material material) {
        this.color = material.color;
        this.emissiveColor = material.emissiveColor;
        this.emissiveStrength = material.emissiveStrength;
    }
}
