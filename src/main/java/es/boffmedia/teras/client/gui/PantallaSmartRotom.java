package es.boffmedia.teras.client.gui;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;

/**
 * Full-screen SmartRotom browser, ported from the 1.16.5 montoyo-based screen onto CinemaMod MCEF.
 * The browser texture is drawn as a full-window quad; mouse/keyboard events are forwarded to CEF.
 */
public class PantallaSmartRotom extends Screen {

    private final MCEFBrowser browser;
    private int lastWidth = -1, lastHeight = -1;

    /** Opens the screen bound to a specific SmartRotom item's browser instance. */
    public PantallaSmartRotom(MCEFBrowser browser) {
        super(Component.literal("SmartRotom"));
        this.browser = browser;
    }

    @Override
    protected void init() {
        super.init();
        if (browser == null) {
            Teras.LOGGER.error("SmartRotom screen opened with no browser");
            return;
        }
        resizeBrowser();
    }

    // ---- Coordinate helpers: Screen coords are GUI-scaled; CEF wants physical pixels ----
    private int px(double v) {
        return (int) (v * minecraft.getWindow().getGuiScale());
    }

    private void resizeBrowser() {
        if (browser == null) return;
        int w = minecraft.getWindow().getWidth();
        int h = minecraft.getWindow().getHeight();
        if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight)) {
            browser.resize(w, h);
            lastWidth = w;
            lastHeight = h;
        }
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        resizeBrowser();
    }

    @Override
    public void tick() {
        super.tick();
        resizeBrowser();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (browser == null) return;

        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());

        Tesselator t = Tesselator.getInstance();
        BufferBuilder buffer = t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // v=1 at the bottom, v=0 at the top -> upright (CEF texture origin is top-left)
        buffer.addVertex(0, height, 0).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255);
        buffer.addVertex(width, height, 0).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255);
        buffer.addVertex(width, 0, 0).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255);
        buffer.addVertex(0, 0, 0).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255);
        BufferUploader.drawWithShader(buffer.build());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    @Override
    public void onClose() {
        // Keep the browser instance alive across opens (mirrors the 1.16.5 per-item SmartRotom persistence).
        super.onClose();
    }

    // ---------------- Mouse ----------------
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser != null) {
            browser.sendMousePress(px(mouseX), px(mouseY), button);
            browser.setFocus(true);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser != null) {
            browser.sendMouseRelease(px(mouseX), px(mouseY), button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) {
            browser.sendMouseMove(px(mouseX), px(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (browser != null) {
            browser.sendMouseWheel(px(mouseX), px(mouseY), scrollY, 0);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---------------- Keyboard ----------------
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (browser != null) {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
            browser.setFocus(true);
        }
        // Let Esc still close the screen.
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser != null) {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint == (char) 0) return false;
        if (browser != null) {
            browser.sendKeyTyped(codePoint, modifiers);
            browser.setFocus(true);
        }
        return super.charTyped(codePoint, modifiers);
    }
}
