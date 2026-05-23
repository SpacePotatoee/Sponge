package sp.sponge.render.imgui;

import imgui.ImGui;
import sp.sponge.Sponge;
import sp.sponge.render.MainRenderer;
import sp.sponge.render.vulkan.raytracing.LightProbeManager;

public class ImGuiScreen {
    private static int fpsValue = 0;
    private static int fpsCounter = 0;
    private static long updateTime = 0L;

    public static void render() {
        if (ImGui.begin("Ray Tracer")) {
            fpsCounter++;
            if (System.currentTimeMillis() >= updateTime + 1000L) {
                fpsValue = fpsCounter;
                fpsCounter = 0;
                updateTime = System.currentTimeMillis();
            }
            ImGui.text(fpsValue + " fps");

            boolean changed = false;
            float[] baseSize = new float[]{LightProbeManager.BASE_SIZE};
            if (ImGui.sliderFloat("Probes Base", baseSize, 0, 30)) {
                LightProbeManager.BASE_SIZE = baseSize[0];
                changed = true;
            }

            float[] ySize = new float[]{LightProbeManager.Y_SIZE};
            if (ImGui.sliderFloat("Probes Height", ySize, 0, 30)) {
                LightProbeManager.Y_SIZE = ySize[0];
                changed = true;
            }


            if (changed) {
                Sponge.getInstance().getMainRenderer().markDirty();
            }

            ImGui.end();
        }
    }

}
