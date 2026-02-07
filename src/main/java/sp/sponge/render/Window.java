package sp.sponge.render;

import imgui.ImGui;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import sp.sponge.input.Input;

import static org.lwjgl.glfw.GLFW.*;

public class Window implements AutoCloseable {
    private static Window INSTANCE;

    private final ImGuiImplGlfw implGlfw;
    private final ImGuiImplGl3 implGl3;
    private long handle;
    private int width;
    private int height;
    private Input input;

    public static Window getWindow() {
        if (INSTANCE == null) {
            INSTANCE = new Window();
        }

        return INSTANCE;
    }

    private Window() {
        if (!glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        this.implGlfw = new ImGuiImplGlfw();
        this.implGl3 = new ImGuiImplGl3();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        this.width = 1920;
        this.height = 1080;
        this.handle = glfwCreateWindow(this.width, this.height, "Sponge", 0L, 0L);

        if (this.handle == 0L) {
            throw new RuntimeException("Failed to create window");
        }

        glfwMakeContextCurrent(this.handle);
        glfwSwapInterval(0);
        glfwShowWindow(this.handle);
        glfwSetWindowSizeCallback(this.handle, this::resize);

        this.input = new Input(this);

        GL.createCapabilities();

        ImGui.createContext();
        implGlfw.init(this.handle, true);
        implGl3.init("#version 410 core");
    }

    public void resize(long handle, int width, int height) {
        GL11.glViewport(0, 0, width, height);
        this.width = width;
        this.height = height;
    }

    public long getHandle() {
        return this.handle;
    }

    public void pollEvents() {
        glfwSwapBuffers(this.handle);
        glfwPollEvents();
    }

    public void startImGuiFrame() {
        implGlfw.newFrame();
        implGl3.newFrame();
        ImGui.newFrame();
    }

    public void endImGuiFrame() {
        ImGui.render();
        implGl3.renderDrawData(ImGui.getDrawData());

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            final long backupCurrentContext = GLFW.glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
            GLFW.glfwMakeContextCurrent(backupCurrentContext);
        }
    }

    public boolean isRunning() {
        return !glfwWindowShouldClose(this.handle);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Input getInput() {
        return input;
    }

    @Override
    public void close() throws Exception {
        glfwDestroyWindow(this.handle);
        glfwTerminate();
    }
}
