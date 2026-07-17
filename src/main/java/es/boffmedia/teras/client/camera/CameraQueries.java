package es.boffmedia.teras.client.camera;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import es.boffmedia.teras.mcef.JsQueryCallback;
import net.minecraft.client.Minecraft;

/**
 * Serves the camera page's zoom and flashlight queries against {@link CameraZoom} and
 * {@link CameraFlashlight}.
 *
 * <p>All of them hop to the client thread: the query arrives on a CEF thread, and the state they read
 * depends on the held item.</p>
 */
public final class CameraQueries {
    private CameraQueries() {}

    private static final Gson GSON = new Gson();

    /** Replies with the current level, the available levels, and what each is worth. */
    public static void handleGetZoomLevel(JsQueryCallback callback) {
        Minecraft.getInstance().execute(() -> {
            try {
                JsonObject response = zoomState();
                JsonObject levels = new JsonObject();
                for (int i = 0; i < CameraZoom.levelCount(); i++) {
                    levels.addProperty(String.valueOf(i), CameraZoom.zoomFactorForLevel(i) + "x");
                }
                response.add("availableLevels", levels);
                response.addProperty("zoomMultiplier", CameraZoom.currentMultiplier());
                callback.success(GSON.toJson(response));
            } catch (Exception e) {
                callback.failure(0, "Error getting zoom level: " + e.getMessage());
            }
        });
    }

    /** Sets the level from {@code {level:n}}; the value is clamped rather than rejected. */
    public static void handleSetZoomLevel(String query, JsQueryCallback callback) {
        final int level;
        try {
            level = GSON.fromJson(query, JsonObject.class).get("level").getAsInt();
        } catch (Exception e) {
            callback.failure(0, "setZoomLevel needs a numeric 'level': " + e.getMessage());
            return;
        }
        Minecraft.getInstance().execute(() -> {
            try {
                CameraZoom.setLevel(level);
                callback.success(GSON.toJson(zoomState()));
            } catch (Exception e) {
                callback.failure(0, "Error setting zoom level: " + e.getMessage());
            }
        });
    }

    public static void handleGetFlashlight(JsQueryCallback callback) {
        Minecraft.getInstance().execute(() -> {
            try {
                callback.success(GSON.toJson(flashlightState()));
            } catch (Exception e) {
                callback.failure(0, "Error getting flashlight: " + e.getMessage());
            }
        });
    }

    /** Sets the switch from {@code {on:bool}}. Fires no notification: the page asked, so it knows. */
    public static void handleSetFlashlight(String query, JsQueryCallback callback) {
        final boolean on;
        try {
            on = GSON.fromJson(query, JsonObject.class).get("on").getAsBoolean();
        } catch (Exception e) {
            callback.failure(0, "setFlashlight needs a boolean 'on': " + e.getMessage());
            return;
        }
        Minecraft.getInstance().execute(() -> {
            try {
                CameraFlashlight.setEnabled(on);
                callback.success(GSON.toJson(flashlightState()));
            } catch (Exception e) {
                callback.failure(0, "Error setting flashlight: " + e.getMessage());
            }
        });
    }

    private static JsonObject zoomState() {
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("zoomLevel", CameraZoom.level());
        response.addProperty("zoomLevelCount", CameraZoom.levelCount());
        response.addProperty("zoomFactor", CameraZoom.zoomFactorForLevel(CameraZoom.level()));
        return response;
    }

    /**
     * {@code on} is the switch; {@code active} is whether it is lighting anything, which also needs the
     * camera to still be in hand.
     */
    private static JsonObject flashlightState() {
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("on", CameraFlashlight.isEnabled());
        response.addProperty("active", CameraFlashlight.isOn());
        return response;
    }
}
