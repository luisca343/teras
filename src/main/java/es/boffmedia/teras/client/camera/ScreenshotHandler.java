package es.boffmedia.teras.client.camera;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Serves the web's {@code takeScreenshot} query: photographs the frame, describes what is in view, and
 * replies with a {@code data:} URL.
 *
 * <p><b>Why this is spread across three threads.</b> The query arrives on a CEF thread, which may not
 * touch the game. Reading the level and the framebuffer must happen on the render thread. Encoding must
 * not: a 4K frame to PNG and Base64 takes long enough to stutter the game. So the request hops to the
 * client, the capture rides one {@link RenderFrameEvent.Post}, and the encode runs on
 * {@link Teras#EXECUTOR}.</p>
 *
 * <p>The frame is captured from {@code RenderFrameEvent.Post} rather than inline because
 * {@code includeUI:false} has to hide the GUI and then let a frame draw without it. Tasks queued onto
 * the client run before that frame renders, so the very next {@code Post} is already UI-free.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ScreenshotHandler {
    private ScreenshotHandler() {}

    private static final Gson GSON = new Gson();

    /** One at a time: a second request while one is pending would fight over {@code hideGui}. */
    private static final AtomicReference<Pending> PENDING = new AtomicReference<>();

    private record Pending(ScreenshotQuery query, JsQueryCallback callback, boolean restoreGui) {}

    /** Entry point from {@code QueryHelper}; may be called from any thread. */
    public static void handleTakeScreenshot(String query, JsQueryCallback callback) {
        final ScreenshotQuery parsed;
        try {
            JsonObject json = GSON.fromJson(query, JsonObject.class);
            parsed = ScreenshotQuery.from(json);
        } catch (Exception e) {
            callback.failure(0, "Malformed takeScreenshot query: " + e.getMessage());
            return;
        }
        Minecraft.getInstance().execute(() -> request(parsed, callback));
    }

    private static void request(ScreenshotQuery query, JsQueryCallback callback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            callback.failure(0, "Not in a world");
            return;
        }
        boolean hideGui = !query.includeUI() && !mc.options.hideGui;
        if (!PENDING.compareAndSet(null, new Pending(query, callback, hideGui))) {
            callback.failure(0, "A screenshot is already in progress");
            return;
        }
        if (hideGui) {
            mc.options.hideGui = true;
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        Pending pending = PENDING.getAndSet(null);
        if (pending == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            capture(mc, pending);
        } catch (Throwable t) {
            Teras.LOGGER.error("Screenshot failed", t);
            pending.callback().failure(0, "Screenshot failed: " + t.getMessage());
        } finally {
            // Restore before anything can throw again — leaving hideGui on would strand the player
            // with no HUD.
            if (pending.restoreGui()) {
                mc.options.hideGui = false;
            }
        }
    }

    private static void capture(Minecraft mc, Pending pending) throws Exception {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            pending.callback().failure(0, "Not in a world");
            return;
        }

        JsonObject location = ViewScanner.location(player, level);
        JsonArray entities = ViewScanner.entities(player, level);
        boolean zoomActive = CameraZoom.isActive();
        double zoomMultiplier = CameraZoom.currentMultiplier();

        CameraImage.Pixels pixels;
        NativeImage captured = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        try {
            pixels = CameraImage.readPixels(captured);
        } finally {
            // Off-heap: a missed close leaks the whole framebuffer, ~33 MB at 4K.
            captured.close();
        }

        Teras.EXECUTOR.execute(() -> encode(pixels, location, entities, zoomActive, zoomMultiplier, pending));
    }

    private static void encode(CameraImage.Pixels pixels, JsonObject location, JsonArray entities,
                               boolean zoomActive, double zoomMultiplier, Pending pending) {
        try {
            String dataUrl = CameraImage.toDataUrl(pixels, pending.query());
            JsonObject response = new JsonObject();
            response.addProperty("status", "ok");
            response.add("location", location);
            response.add("entities", entities);
            response.addProperty("image", dataUrl);
            response.addProperty("zoomActive", zoomActive);
            response.addProperty("zoomMultiplier", zoomMultiplier);
            Teras.LOGGER.info("Screenshot: {} entities in view, {} KB encoded",
                    entities.size(), dataUrl.length() / 1024);
            pending.callback().success(GSON.toJson(response));
        } catch (Throwable t) {
            Teras.LOGGER.error("Screenshot encoding failed", t);
            pending.callback().failure(0, "Screenshot encoding failed: " + t.getMessage());
        }
    }
}
