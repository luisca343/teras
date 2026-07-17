package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.cef.browser.CefBrowser;

import java.util.Collection;

/**
 * JS -> Java dispatch for {@code window.mcefQuery({query:"...", ...})}, ported from the 1.16.5
 * montoyo {@code QueryHelper}. The query-type switch is preserved 1:1 so the remaining handlers
 * can be filled in mechanically as their dependencies (networking, Pixelmon, JourneyMap) are ported.
 *
 * <p>Callbacks may arrive on a CEF thread; any handler that touches Minecraft client state marshals
 * onto the main thread via {@link Minecraft#execute(Runnable)} before doing so.</p>
 */
public final class QueryHelper {
    private QueryHelper() {}

    private static final String SUCCESS = "{\"status\": \"ok\"}";
    private static final Gson GSON = new Gson();

    /**
     * Callbacks awaiting an async (server round-trip) response, keyed by the request id echoed back in
     * {@code McefResponsePayload}. Replaces 1.16.5's per-query static callbacks and lets several async
     * queries be in flight without their responses colliding. Resolved in {@code ClientNetHandler}.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Long, JsQueryCallback> PENDING =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong NEXT_REQUEST_ID =
            new java.util.concurrent.atomic.AtomicLong();

    /** Registers {@code callback} for a new async request and returns its id (for the request payload). */
    private static long register(JsQueryCallback callback) {
        long id = NEXT_REQUEST_ID.incrementAndGet();
        PENDING.put(id, callback);
        return id;
    }

    /** Resolves and removes the callback for {@code requestId}, or {@code null} if none (called by ClientNetHandler). */
    public static JsQueryCallback takePending(long requestId) {
        return PENDING.remove(requestId);
    }

    public static boolean handleQuery(CefBrowser browser, long id, String query,
                                      boolean persistent, JsQueryCallback callback) {
        Teras.LOGGER.info("SmartRotom query received: {}", query);

        final JsonObject json;
        final String type;
        try {
            json = GSON.fromJson(query, JsonObject.class);
            type = json.get("query").getAsString();
        } catch (Exception e) {
            callback.failure(0, "Malformed query: " + query);
            return true;
        }

        // Tolerant of case and underscores: the web sends camelCase ("getMisiones"), which the old
        // valueOf(type.toUpperCase()) could never match against GET_MISIONES. See QueryType.fromQuery.
        final QueryType queryType = QueryType.fromQuery(type);
        if (queryType == null) {
            Teras.LOGGER.error("Unknown query type: {}", query);
            callback.failure(0, "Unknown query type: " + query);
            return true;
        }

        try {
            switch (queryType) {
                case GET_PLAYERS:
                    handleGetPlayers(callback);
                    return true;
                case CHAT_MESSAGE:
                    es.boffmedia.teras.net.TerasNet.sendChatToServer(query);
                    callback.success(SUCCESS);
                    return true;
                case GET_USER_DATA:
                    // Async: register the callback under a fresh id; the server echoes the id in an
                    // McefResponsePayload, which ClientNetHandler routes back to this callback.
                    es.boffmedia.teras.net.TerasNet.requestUserData(register(callback));
                    return true;
                case GET_SPAWNS:
                    // Async (same round-trip): the server scans the player's Pixelmon spawner and
                    // replies with the spawn list under the same id.
                    es.boffmedia.teras.net.TerasNet.requestSpawns(register(callback));
                    return true;
                case GET_MISIONES:
                    es.boffmedia.teras.net.TerasNet.requestMisiones(register(callback));
                    return true;
                case TAKE_SCREENSHOT:
                    // Async: resolves a frame later (the capture rides a render frame) and then off
                    // the client thread entirely. See ScreenshotHandler.
                    es.boffmedia.teras.client.camera.ScreenshotHandler.handleTakeScreenshot(query, callback);
                    return true;
                case GET_ZOOM_LEVEL:
                    es.boffmedia.teras.client.camera.CameraQueries.handleGetZoomLevel(callback);
                    return true;
                case SET_ZOOM_LEVEL:
                    es.boffmedia.teras.client.camera.CameraQueries.handleSetZoomLevel(query, callback);
                    return true;
                case GET_FLASHLIGHT:
                    es.boffmedia.teras.client.camera.CameraQueries.handleGetFlashlight(callback);
                    return true;
                case SET_FLASHLIGHT:
                    es.boffmedia.teras.client.camera.CameraQueries.handleSetFlashlight(query, callback);
                    return true;

                // --- Handlers below need more networking / JourneyMap: ported next phase ---
                case OPEN_PC:
                case DAR_CAJA:
                case SET_CALL:
                case LEAVE_CALL:
                    return notPorted(queryType, callback, "server networking");
                case ADD_WAYPOINT:
                case GET_WAYPOINTS:
                    return notPorted(queryType, callback, "JourneyMap integration");
                default:
                    return false;
            }
        } catch (Exception e) {
            Teras.LOGGER.error("Error handling query: {}", query, e);
            callback.failure(0, "Error handling query: " + e.getMessage());
            return true;
        }
    }

    private static boolean notPorted(QueryType type, JsQueryCallback callback, String dependency) {
        Teras.LOGGER.warn("SmartRotom query '{}' not yet ported (needs {})", type, dependency);
        callback.failure(501, "Query '" + type + "' not yet available on 1.21.1 (needs " + dependency + ")");
        return true;
    }

    /** Fully-ported example handler: returns the online player list. Proves the JS round-trip. */
    private static void handleGetPlayers(JsQueryCallback callback) {
        Minecraft.getInstance().execute(() -> {
            try {
                if (Minecraft.getInstance().getConnection() == null) {
                    callback.failure(0, "Not connected");
                    return;
                }
                Collection<PlayerInfo> players = Minecraft.getInstance().getConnection().getOnlinePlayers();
                JsonArray arr = new JsonArray();
                for (PlayerInfo p : players) {
                    JsonObject u = new JsonObject();
                    u.addProperty("uuid", p.getProfile().getId().toString());
                    u.addProperty("name", p.getProfile().getName());
                    arr.add(u);
                }
                JsonObject resp = new JsonObject();
                resp.addProperty("status", "ok");
                resp.add("players", arr);
                callback.success(GSON.toJson(resp));
            } catch (Exception e) {
                callback.failure(0, "getPlayers failed: " + e.getMessage());
            }
        });
    }
}
