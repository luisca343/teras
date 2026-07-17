package es.boffmedia.teras.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import es.boffmedia.teras.Teras;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.UUID;

/**
 * The camera's keybinds — {@code +}/{@code -} zoom and {@code L} flashlight — active while it is in
 * hand. Rebindable {@link KeyMapping}s, scoped to {@link KeyConflictContext#IN_GAME} so they don't fire
 * while a screen has the keyboard; inside the app the page's own controls apply.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class CameraKeys {
    private CameraKeys() {}

    private static final String CATEGORY = "key.categories.teras";

    /** The unshifted key that carries {@code +}; {@code -} is its neighbour. Registered by {@link CameraSetup}. */
    static final KeyMapping ZOOM_IN = new KeyMapping(
            "key.teras.camera_zoom_in", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, CATEGORY);

    static final KeyMapping ZOOM_OUT = new KeyMapping(
            "key.teras.camera_zoom_out", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, CATEGORY);

    /** {@code L} for light — free in vanilla, and the same initial in Spanish ({@code linterna}). */
    static final KeyMapping FLASHLIGHT = new KeyMapping(
            "key.teras.camera_flashlight", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Drain every queue each tick even when the camera is away: consumeClick() returns presses
        // buffered since the last call, so leaving them queued would fire on the next pickup.
        int zoomDelta = 0;
        while (ZOOM_IN.consumeClick()) {
            zoomDelta++;
        }
        while (ZOOM_OUT.consumeClick()) {
            zoomDelta--;
        }
        boolean toggleFlashlight = false;
        while (FLASHLIGHT.consumeClick()) {
            toggleFlashlight = !toggleFlashlight;
        }
        if (zoomDelta == 0 && !toggleFlashlight) {
            return;
        }

        UUID camera = CameraZoom.activeCameraId();
        if (camera == null) {
            return;
        }
        if (zoomDelta != 0 && CameraZoom.step(zoomDelta)) {
            notifyZoom(camera);
        }
        if (toggleFlashlight) {
            notifyFlashlight(camera, CameraFlashlight.toggle());
        }
    }

    /** {@code window.onZoomChanged(level, factor)} / {@code teras:zoomchanged}. */
    private static void notifyZoom(UUID camera) {
        int level = CameraZoom.level();
        double factor = CameraZoom.currentFactor();
        CameraPage.notify(camera, "onZoomChanged",
                String.format(Locale.ROOT, "%d,%s", level, factor),
                "teras:zoomchanged",
                String.format(Locale.ROOT, "{level:%d,factor:%s}", level, factor));
    }

    /** {@code window.onFlashlightChanged(on)} / {@code teras:flashlightchanged}. */
    private static void notifyFlashlight(UUID camera, boolean on) {
        CameraPage.notify(camera, "onFlashlightChanged", String.valueOf(on),
                "teras:flashlightchanged", String.format(Locale.ROOT, "{on:%s}", on));
    }
}
