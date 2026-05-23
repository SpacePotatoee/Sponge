package sp.sponge.render.vulkan.raytracing;

import sp.sponge.scene.SceneManager;
import sp.sponge.scene.objects.custom.obj.Sphere;
import sp.sponge.util.math.Vec3f;

import java.util.ArrayList;
import java.util.List;

public class LightProbeManager {
    private final List<Sphere> probes = new ArrayList<>();
    public static float BASE_SIZE = 7;
    public static float Y_SIZE = 7;

    public LightProbeManager() {

    }

    public void update() {
        probes.clear();
        float baseSize = BASE_SIZE - 1;
        float ySize = Y_SIZE - 1;

        float baseSpacing = 1;
        float heightSpacing = 1;


        float xResult = (float) Math.ceil(baseSize / baseSpacing);
        if (baseSize % baseSpacing == 0) {
            xResult++;
        }

        float yResult = (float) Math.ceil(ySize / heightSpacing);
        if (ySize % heightSpacing == 0) {
            yResult++;
        }

        for (int i = 0; i < xResult; i++) {
            for (int j = 0; j < yResult; j++) {
                Sphere sphere = new Sphere(false);
                sphere.getTransformations().setPosition(i - baseSize / 2, j - ySize / 2, 0);
                sphere.getTransformations().scale(0.1f);
                probes.add(sphere);
                SceneManager.addObject(sphere);
            }
        }

    }

    public List<Sphere> getProbes() {
        return probes;
    }
}
