package sp.sponge;

import sp.sponge.render.MainRenderer;
import sp.sponge.render.Window;
import sp.sponge.render.imgui.ImGuiScreen;
import sp.sponge.render.vulkan.image.texture.TextureManager;
import sp.sponge.util.manager.ManagerManager;

import java.io.File;
import java.util.logging.Logger;

public class Sponge {
    private static Sponge INSTANCE;
    public final Logger SPONGE_LOGGER;
    private final MainRenderer mainRenderer;
    private RunFiles runFiles;
    private final TextureManager textureManager;

    public Sponge() {
        INSTANCE = this;
        this.SPONGE_LOGGER = Logger.getLogger("sponge");
        this.mainRenderer = new MainRenderer();

        this.textureManager = ManagerManager.register(new TextureManager());
        ManagerManager.initAssets();

        this.mainRenderer.init();
    }

    public void mainLoop() {
        Window window = Window.getWindow();

        this.mainRenderer.render();

        window.startImGuiFrame();
        ImGuiScreen.render();
        window.endImGuiFrame();

        window.pollEvents();
    }

    public static Sponge getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new Sponge();
        }

        return INSTANCE;
    }

    public void setArgs(File runFile) {
        File scenesFile = new File(runFile, "scenes");

        scenesFile.mkdir();
        this.runFiles = new RunFiles(runFile, scenesFile);
    }

    public static File getAssetFile(String path) {
        return new File("src/main/resources/assets/" + path);
    }

    public MainRenderer getMainRenderer() {
        return mainRenderer;
    }

    public TextureManager getTextureManager() {
        return textureManager;
    }

    public RunFiles getRunFiles() {
        return this.runFiles;
    }

    public Logger getLogger() {
        return this.SPONGE_LOGGER;
    }

    public void free() {
        this.mainRenderer.getVulkanCtx().getLogicalDevice().waitIdle();
        this.textureManager.free(this.mainRenderer.getVulkanCtx());
        this.mainRenderer.close();
    }

    public record RunFiles(File runFile, File scenesFile){}

}
