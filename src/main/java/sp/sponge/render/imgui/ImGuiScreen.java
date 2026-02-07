package sp.sponge.render.imgui;

import imgui.ImGui;

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

            if (ImGui.button("Test")) {
                System.out.println("Working");
            }

            ImGui.end();
        }
    }

}
