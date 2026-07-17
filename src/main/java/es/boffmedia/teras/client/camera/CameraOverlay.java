package es.boffmedia.teras.client.camera;

import es.boffmedia.teras.client.gui.PantallaSmartRotom;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The viewfinder drawn while the camera is in hand <em>and its app is closed</em> — what tells the
 * player they are in camera mode, since the hands and the SmartRotom are hidden by then
 * ({@code ClientEvents.onRenderPlayerHand}). See {@code docs/CAMERA.md}.
 */
public final class CameraOverlay {
    private CameraOverlay() {}

    /** Mask around the frame. Translucent rather than solid so the player keeps peripheral vision. */
    private static final int MASK_COLOR = 0x66000000;
    private static final int BRACKET_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    /** Shown only while the flashlight is lit; amber so it reads as a lamp, not a label. */
    private static final Component FLASHLIGHT_MARK =
            Component.literal("◉ ").append(Component.translatable("gui.teras.camera_flashlight"));
    private static final int FLASHLIGHT_COLOR = 0xFFFFD24A;

    private static final int BRACKET_THICKNESS = 2;
    private static final int BRACKET_ARM = 18;

    /** Frame inset as a fraction of each axis; the clear area is what the photo is "of". */
    private static final float MARGIN_X = 1.0F / 12.0F;
    private static final float MARGIN_Y = 1.0F / 10.0F;

    /** Registered as a GUI layer by {@link CameraSetup}. */
    static void renderViewfinder(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        // The open app draws its own chrome, and the viewfinder frames a view that isn't there — the
        // same reason SmartRotomRenderer stands down for this screen.
        if (mc.screen instanceof PantallaSmartRotom) {
            return;
        }
        // The layer manager's hideGui gate covers only vanilla's own layers, so check it here.
        if (mc.options.hideGui || !CameraZoom.isActive()) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int marginX = Math.round(width * MARGIN_X);
        int marginY = Math.round(height * MARGIN_Y);
        int left = marginX;
        int top = marginY;
        int right = width - marginX;
        int bottom = height - marginY;

        graphics.fill(0, 0, width, top, MASK_COLOR);
        graphics.fill(0, bottom, width, height, MASK_COLOR);
        graphics.fill(0, top, left, bottom, MASK_COLOR);
        graphics.fill(right, top, width, bottom, MASK_COLOR);

        corner(graphics, left, top, 1, 1);
        corner(graphics, right, top, -1, 1);
        corner(graphics, left, bottom, 1, -1);
        corner(graphics, right, bottom, -1, -1);

        String zoom = String.format(Locale.ROOT, "%.1fx", CameraZoom.currentFactor());
        graphics.drawString(mc.font, zoom, right - mc.font.width(zoom) - 4, bottom + 5, TEXT_COLOR);

        // The hands are hidden, so the frame is the only place the flashlight's state is visible.
        if (CameraFlashlight.isOn()) {
            graphics.drawString(mc.font, FLASHLIGHT_MARK, left + 4, bottom + 5, FLASHLIGHT_COLOR);
        }
    }

    /** One L-shaped bracket, drawn inward from ({@code x},{@code y}) along the given signs. */
    private static void corner(GuiGraphics graphics, int x, int y, int signX, int signY) {
        int armX = x + BRACKET_ARM * signX;
        int armY = y + BRACKET_ARM * signY;
        graphics.fill(Math.min(x, armX), Math.min(y, y + BRACKET_THICKNESS * signY),
                Math.max(x, armX), Math.max(y, y + BRACKET_THICKNESS * signY), BRACKET_COLOR);
        graphics.fill(Math.min(x, x + BRACKET_THICKNESS * signX), Math.min(y, armY),
                Math.max(x, x + BRACKET_THICKNESS * signX), Math.max(y, armY), BRACKET_COLOR);
    }
}
