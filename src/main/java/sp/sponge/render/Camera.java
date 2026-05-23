package sp.sponge.render;

import imgui.ImGui;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import sp.sponge.input.Input;
import sp.sponge.input.keybind.Keybinds;
import sp.sponge.util.math.Vec3f;

public class Camera {
    private float fov;
    private final Vec3f position;
    private final Vec3f rotationVector;

    private final Matrix4f modelViewMatrix = new Matrix4f().identity();
    private final Matrix4f invModelViewMatrix = new Matrix4f().identity();

    private final Matrix4f projectionMatrix = new Matrix4f().identity();
    private final Matrix4f invProjectionMatrix = new Matrix4f().identity();
    private boolean moved;

    public Camera() {
        this.fov = 70;
//        this.position = new Vec3f(-50,5, -3);
//        this.position = new Vec3f(-6,7, -7);
        this.position = new Vec3f(0);
//        this.rotationVector = new Vec3f((float) Math.toRadians(25), (float) Math.toRadians(115), 1);
        this.rotationVector = new Vec3f(0, (float) Math.toRadians(90), 1);
    }

    public float getFov() {
        return fov;
    }

    public void updateCamera() {
        this.moved = false;
        float speed = 0.2f;
        Vec3f rotation = this.getRotationVector();
        if (Keybinds.FORWARDS.isPressed()) {
            this.moved = true;
            this.position.subtractInternal(rotation.mul(speed));
        }
        if (Keybinds.BACKWARDS.isPressed()) {
            this.moved = true;
            this.position.addInternal(rotation.mul(speed));
        }
        if (Keybinds.LEFT.isPressed()) {
            this.moved = true;
            this.position.subtractInternal(rotation.rotateY((float) Math.toRadians(90)).mul(speed));
        }
        if (Keybinds.RIGHT.isPressed()) {
            this.moved = true;
            this.position.addInternal(rotation.rotateY((float) Math.toRadians(90)).mul(speed));
        }

        if (Keybinds.SPACE.isPressed()) {
            this.moved = true;
            this.position.y += speed;
        }
        if (Keybinds.SHIFT.isPressed()) {
            this.moved = true;
            this.position.y -= speed;
        }

        Window window = Window.getWindow();
        Input input = window.getInput();
        if (Keybinds.RIGHT_CLICK.isPressed()) {
            this.moved = true;
            float width = window.getWidth();
            float height = window.getHeight();


            float yaw = (float) (input.mousePosX - width / 2) / height;
            float pitch = (float) (input.mousePosY - height / 2) / height;

            rotationVector.x += pitch;
            rotationVector.x = (float) Math.clamp(rotationVector.x, -Math.PI/2.0, Math.PI/2.0);

            rotationVector.y += yaw;

            input.lockCursor(window, width / 2, height / 2);
        } else {
            input.unlockCursor(window);
        }

        this.modelViewMatrix.identity();
        this.modelViewMatrix.setRotationXYZ(this.getRotation().x, this.getRotation().y, 0.0f);
        this.modelViewMatrix.translate(this.getPosition().negate());
        this.projectionMatrix.setPerspective((float) Math.toRadians(this.getFov()), (float) window.getWidth() / window.getHeight(), 0.01f, 1000.0f);

        this.modelViewMatrix.invert(this.invModelViewMatrix);
        this.projectionMatrix.invertPerspective(this.invProjectionMatrix);
    }

    public boolean hasMoved() {
        return this.moved;
    }

    private Vec3f getRotationVector() {
        Vector3f rotation = new Vector3f(0, 0, 1);
        rotation.rotateX(-rotationVector.x);
        rotation.rotateY(-rotationVector.y);
        rotation.normalize();
        return new Vec3f(rotation.x, 0.0f, rotation.z).normalize();
    }

    public Vec3f getRotation() {
        return rotationVector;
    }

    public Vector3f getPosition() {
        return position.toVector3f();
    }

    public Matrix4f getModelViewMatrix() {
        return modelViewMatrix;
    }

    public Matrix4f getInvModelViewMatrix() {
        return invModelViewMatrix;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public Matrix4f getInvProjectionMatrix() {
        return invProjectionMatrix;
    }
}
