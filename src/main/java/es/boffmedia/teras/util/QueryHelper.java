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
     * Holder for a callback that will be resolved later by an async (server round-trip) response,
     * mirroring 1.16.5 {@code ClientProxy.callbackMCEF}. Wired up when networking is ported.
     */
    public static volatile JsQueryCallback pendingCallback;

    private enum QueryType {
        ADD_WAYPOINT,
        GET_USER_DATA,
        GET_WAYPOINTS,
        OPEN_PC,
        GET_SPAWNS,
        SET_CALL,
        LEAVE_CALL,
        CHAT_MESSAGE,
        GET_PLAYERS,
        GET_MISIONES,
        DAR_CAJA,
        TAKE_SCREENSHOT,
        GET_ZOOM_LEVEL,
        SET_ZOOM_LEVEL,
    }

    public static boolean handleQuery(CefBrowser browser, long id, String query,
                                      boolean persistent, JsQueryCallback callback) {
        Teras.LOGGER.info("SmartRotom query received: {}", query);
        pendingCallback = callback;

        final JsonObject json;
        final String type;
        try {
            json = GSON.fromJson(query, JsonObject.class);
            type = json.get("query").getAsString();
        } catch (Exception e) {
            callback.failure(0, "Malformed query: " + query);
            return true;
        }

        final QueryType queryType;
        try {
            queryType = QueryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
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
                    // Async: the server replies with an McefResponsePayload that resolves
                    // `pendingCallback` (set above) via ClientNetHandler.
                    es.boffmedia.teras.net.TerasNet.requestUserData();
                    return true;

                // --- Handlers below need more networking / Pixelmon / JourneyMap: ported next phase ---
                case OPEN_PC:
                case DAR_CAJA:
                case SET_CALL:
                case LEAVE_CALL:
                case GET_MISIONES:
                case GET_SPAWNS:
                    return notPorted(queryType, callback, "server networking");
                case ADD_WAYPOINT:
                case GET_WAYPOINTS:
                    return notPorted(queryType, callback, "JourneyMap integration");
                case TAKE_SCREENSHOT:
                    return notPorted(queryType, callback, "ScreenshotHandler");
                case GET_ZOOM_LEVEL:
                case SET_ZOOM_LEVEL:
                    return notPorted(queryType, callback, "CameraZoomHandler");
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
