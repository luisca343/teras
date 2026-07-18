package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import es.boffmedia.teras.mcef.PendingQueries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.cef.browser.CefBrowser;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * JS -> Java dispatch for {@code window.mcefQuery({query:"...", ...})}, ported from the 1.16.5
 * montoyo {@code QueryHelper}.
 *
 * <p>Dispatch is a {@link QueryType}-keyed handler table rather than a switch, so adding a query is a
 * local edit (one table entry plus its handler) instead of a change to a hub every query shares.</p>
 *
 * <p>Callbacks may arrive on a CEF thread; any handler that touches Minecraft client state marshals
 * onto the main thread via {@link Minecraft#execute(Runnable)} before doing so.</p>
 *
 * <p>Whether a query may be answered at all is decided upstream by {@code mcef.TerasQueryRouter},
 * which rejects anything not coming from a Teras-owned browser on the server's home site. Nothing
 * here re-checks that; handlers may assume the caller is the trusted page.</p>
 */
public final class QueryHelper {
    private QueryHelper() {}

    private static final String SUCCESS = "{\"status\": \"ok\"}";
    private static final Gson GSON = new Gson();

    /** What a query does. The raw request string is passed through for the handlers that parse more. */
    @FunctionalInterface
    private interface Handler {
        void handle(JsonObject json, String rawQuery, JsQueryCallback callback);
    }

    private static final Map<QueryType, Handler> HANDLERS = new EnumMap<>(QueryType.class);

    static {
        HANDLERS.put(QueryType.GET_PLAYERS, (json, raw, cb) -> handleGetPlayers(cb));
        HANDLERS.put(QueryType.CHAT_MESSAGE, (json, raw, cb) -> {
            es.boffmedia.teras.net.TerasNet.sendChatToServer(raw);
            cb.success(SUCCESS);
        });
        // Async queries register their callback under a fresh id; the server echoes the id back in an
        // McefResponsePayload, which ClientNetHandler routes to the waiting callback.
        HANDLERS.put(QueryType.GET_USER_DATA, (json, raw, cb) ->
                es.boffmedia.teras.net.TerasNet.requestUserData(PendingQueries.register(cb)));
        // Async (same round-trip): the server scans the player's Pixelmon spawner and replies with the
        // spawn list under the same id.
        HANDLERS.put(QueryType.GET_SPAWNS, (json, raw, cb) ->
                es.boffmedia.teras.net.TerasNet.requestSpawns(PendingQueries.register(cb)));
        HANDLERS.put(QueryType.GET_MISIONES, (json, raw, cb) ->
                es.boffmedia.teras.net.TerasNet.requestMisiones(PendingQueries.register(cb)));
        // Async: resolves a frame later (the capture rides a render frame) and then off the client
        // thread entirely. See ScreenshotHandler.
        HANDLERS.put(QueryType.TAKE_SCREENSHOT, (json, raw, cb) ->
                es.boffmedia.teras.client.camera.ScreenshotHandler.handleTakeScreenshot(raw, cb));
        HANDLERS.put(QueryType.GET_ZOOM_LEVEL, (json, raw, cb) ->
                es.boffmedia.teras.client.camera.CameraQueries.handleGetZoomLevel(cb));
        HANDLERS.put(QueryType.SET_ZOOM_LEVEL, (json, raw, cb) ->
                es.boffmedia.teras.client.camera.CameraQueries.handleSetZoomLevel(raw, cb));
        HANDLERS.put(QueryType.GET_FLASHLIGHT, (json, raw, cb) ->
                es.boffmedia.teras.client.camera.CameraQueries.handleGetFlashlight(cb));
        HANDLERS.put(QueryType.SET_FLASHLIGHT, (json, raw, cb) ->
                es.boffmedia.teras.client.camera.CameraQueries.handleSetFlashlight(raw, cb));
        HANDLERS.put(QueryType.DAR_CAJA, QueryHelper::handleDarCaja);
        HANDLERS.put(QueryType.SET_CALL, QueryHelper::handleSetCall);
        // Async: the server removes this player from any voice group and replies under the id.
        HANDLERS.put(QueryType.LEAVE_CALL, (json, raw, cb) ->
                es.boffmedia.teras.net.TerasNet.requestLeaveCall(PendingQueries.register(cb)));
        // Async: the server opens the sender's own PC. No arguments — the player is the connection's.
        HANDLERS.put(QueryType.OPEN_PC, (json, raw, cb) ->
                es.boffmedia.teras.net.TerasNet.requestOpenPC(PendingQueries.register(cb)));
        // JourneyMap-backed. Fully-qualified and guarded so the journeymap.api classes are never
        // loaded on a client without JourneyMap installed.
        HANDLERS.put(QueryType.ADD_WAYPOINT, (json, raw, cb) -> {
            if (!journeyMapLoaded()) {
                noJourneyMap(QueryType.ADD_WAYPOINT, cb);
                return;
            }
            es.boffmedia.teras.client.region.journeymap.WaypointQueries.handleAddWaypoint(json, cb);
        });
        HANDLERS.put(QueryType.GET_WAYPOINTS, (json, raw, cb) -> {
            if (!journeyMapLoaded()) {
                noJourneyMap(QueryType.GET_WAYPOINTS, cb);
                return;
            }
            es.boffmedia.teras.client.region.journeymap.WaypointQueries.handleGetWaypoints(cb);
        });
    }

    /** The {@code source} of a darCaja query, or {@code null} if absent or not a plain string. */
    static String readSource(JsonObject json) {
        JsonElement source = json.get("source");
        if (source == null || !source.isJsonPrimitive() || !source.getAsJsonPrimitive().isString()) {
            return null;
        }
        return source.getAsString();
    }

    /** The {@code ids} row selector of a darCaja query — empty when absent (mine claims the whole source). */
    static java.util.List<Integer> readIds(JsonObject json) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        JsonElement raw = json.get("ids");
        if (raw != null && raw.isJsonArray()) {
            for (JsonElement element : raw.getAsJsonArray()) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    ids.add(element.getAsInt());
                }
            }
        }
        return ids;
    }

    public static boolean handleQuery(CefBrowser browser, long id, String query,
                                      boolean persistent, JsQueryCallback callback) {
        Teras.LOGGER.debug("SmartRotom query received: {}", query);

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
        final Handler handler = HANDLERS.get(queryType);
        if (handler == null) {
            // A QueryType with no table entry — reported apart from an unknown name so a constant added
            // without its handler is diagnosable instead of looking like a bad request from the page.
            Teras.LOGGER.error("Query type '{}' has no registered handler", queryType);
            callback.failure(501, "Query '" + queryType + "' has no handler");
            return true;
        }

        try {
            handler.handle(json, query, callback);
        } catch (Exception e) {
            Teras.LOGGER.error("Error handling query: {}", query, e);
            callback.failure(0, "Error handling query: " + e.getMessage());
        }
        return true;
    }

    /**
     * Async: the server asks the backend what this player is owed, grants it, and replies under the
     * same id. The page names a SOURCE and an optional row-id selector, never items — see
     * {@code DarCajaPayload}. A page still sending 1.16.5's {@code objetos} lands here with no source
     * and is refused.
     */
    private static void handleDarCaja(JsonObject json, String rawQuery, JsQueryCallback callback) {
        String source = readSource(json);
        if (!es.boffmedia.teras.util.net.HttpText.isValidIdentifier(source)) {
            Teras.LOGGER.error("darCaja without a valid source: {}", rawQuery);
            callback.failure(400, "darCaja requires a source");
            return;
        }
        es.boffmedia.teras.net.TerasNet.requestDarCaja(
                PendingQueries.register(callback), source, readIds(json));
    }

    /**
     * Async: the server places this player in the SVC voice group for chatId and replies under the same
     * id. Only chatId is forwarded — the participant is the connection's player, never the page (see
     * {@code SetCallPayload}). A blank chatId is refused up front.
     */
    private static void handleSetCall(JsonObject json, String rawQuery, JsQueryCallback callback) {
        JsonElement chatIdElement = json.get("chatId");
        String chatId = chatIdElement != null && chatIdElement.isJsonPrimitive()
                ? chatIdElement.getAsString() : null;
        if (chatId == null || chatId.isBlank()) {
            callback.failure(400, "setCall requires a chatId");
            return;
        }
        es.boffmedia.teras.net.TerasNet.requestSetCall(PendingQueries.register(callback), chatId);
    }

    private static boolean journeyMapLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("journeymap");
    }

    /** 501, not an error: the query is implemented, the client just has no JourneyMap to serve it. */
    private static void noJourneyMap(QueryType type, JsQueryCallback callback) {
        Teras.LOGGER.warn("SmartRotom query '{}' needs JourneyMap, which is not installed", type);
        callback.failure(501, "Query '" + type + "' requires JourneyMap");
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
