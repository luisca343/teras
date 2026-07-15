package es.boffmedia.teras.util.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.ClientProxy;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.server.*;
import es.boffmedia.teras.net.server.SMessageEncenderPC;
import es.boffmedia.teras.net.server.serverOld.SMessageFinalizarLlamada;
import es.boffmedia.teras.net.server.serverOld.SMessageIniciarLlamada;
import es.boffmedia.teras.util.objects.post.PokedexEventResponse;
import es.boffmedia.teras.util.ScreenshotHandler;
import es.boffmedia.teras.model.config.UserData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.montoyo.mcef.api.IBrowser;
import net.montoyo.mcef.api.IJSQueryCallback;
import journeymap.client.waypoint.Waypoint;
import journeymap.client.waypoint.WaypointStore;
import journeymap.common.helper.DimensionHelper;
import java.awt.Color;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;

public class QueryHelper {
    private static final String SUCCESS = "{\"status\": \"ok\"}";
    private static final Gson gson = new Gson();

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

    public static boolean handleQuery(IBrowser iBrowser, long l, String query, boolean b, IJSQueryCallback callback) {
        Teras.LOGGER.info("Query received: " + query);
        ClientProxy.callbackMCEF = callback;

        // Query is an object with a "query" field, and multiple other fields
        // The "query" field is the type of query, and the other fields are the parameters
        JsonObject json = gson.fromJson(query, JsonObject.class);
        String type = json.get("query").getAsString();

        List<String> params = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!entry.getKey().equals("query")) {
                params.add(entry.getKey());
            }
        }

        try {
            QueryType queryType = QueryType.valueOf(type.toUpperCase());
            switch (queryType) {
                case GET_WAYPOINTS:
                    handleGetWaypoints(callback);
                    break;
                case ADD_WAYPOINT:
                    handleAddWaypoint(query, callback);
                    break;
                case GET_USER_DATA:
                    handleGetUserData(callback);
                    break;
                case GET_SPAWNS:
                    handleGetSpawns();
                    break;
                case SET_CALL:
                    handleSetCall(query);
                    break;
                case LEAVE_CALL:
                    handleLeaveCall(query);
                    break;
                case OPEN_PC:
                    handleOpenPC(callback);
                    break;
                case CHAT_MESSAGE:
                    Teras.LOGGER.info("Handling chatMessage query");
                    Messages.INSTANCE.sendToServer(new SMessageChatMessage(query));
                    callback.success(SUCCESS);
                    break;
                case TAKE_SCREENSHOT:
                    ScreenshotHandler.handleTakeScreenshot(query, callback);
                    break;
                case GET_ZOOM_LEVEL:
                    handleGetZoomLevel(callback);
                    break;
                case SET_ZOOM_LEVEL:
                    handleSetZoomLevel(query, callback);
                    break;
                // Unused
                case GET_PLAYERS:
                    handleGetPlayers(callback);
                    break;
                case GET_MISIONES:
                    handleGetMisiones(query, callback);
                    break;
                case DAR_CAJA:
                    handleDarCaja(query, callback);
                    break;
                default:
                    return false;
            }
            return true;
        } catch (IllegalArgumentException e) {
            Teras.LOGGER.error("Unknown query type: {}", query, e);
            callback.failure(0, "Unknown query type: " + query);
            return false;
        } catch (Exception e) {
            Teras.LOGGER.error("Error handling query: {}", query, e);
            callback.failure(0, "Error handling query: " + query);
            return false;
        }
    }

    private static void handleOpenPC(IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling openPC query");
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if(player == null) {
            Teras.LOGGER.error("Player is null");
            callback.failure(0, "Player is null");
            return;
        }

        callback.success(SUCCESS);
        Messages.INSTANCE.sendToServer(new SMessageEncenderPC(player.getStringUUID()));
    }

    private static void handleGetUserData(IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling getUserData query");
        ClientProxy.callbackMisiones = callback;
        Messages.INSTANCE.sendToServer(new SMessageDatosServer("query"));
    }

    private static void handleGetPlayers(IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling getPlayers query");
        Collection<NetworkPlayerInfo> players = Objects.requireNonNull(Minecraft.getInstance().getConnection()).getOnlinePlayers();
        ArrayList<UserData> users = new ArrayList<>();
        for (NetworkPlayerInfo player : players) {
            UserData userData = new UserData(player.getProfile().getId().toString(), player.getProfile().getName());
            users.add(userData);
        }
        String response = gson.toJson(users);
        callback.success(response);
    }

    private static void handleGetSpawns() {
        Teras.LOGGER.info("Handling getSpawns query");
        Messages.INSTANCE.sendToServer(new SMessageCheckSpawns("getSpawns"));
    }

    private static void handleGetMisiones(String query, IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling getMisiones query");
        ClientProxy.callbackMisiones = callback;
        // SMessageVerMisiones removed (was unregistered, never reached server)
    }

    private static void handleDarCaja(String query, IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling darCaja query");
        Messages.INSTANCE.sendToServer(new SMessageDarCaja(query));
        callback.success(SUCCESS);
    }

    private static void handleSetCall(String query) {
        Teras.LOGGER.info("Handling setCall query");
        Messages.INSTANCE.sendToServer(new SMessageIniciarLlamada(query));
    }

    private static void handleLeaveCall(String query) {
        Teras.LOGGER.info("Handling leaveCall query");
        Messages.INSTANCE.sendToServer(new SMessageFinalizarLlamada(query));
    }

    private static void handleGetZoomLevel(IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling getZoomLevel query");
        try {
            JsonObject response = new JsonObject();
            response.addProperty("status", "ok");
            response.addProperty("zoomLevel", es.boffmedia.teras.client.CameraZoomHandler.getZoomLevel());
            response.addProperty("zoomLevelCount", es.boffmedia.teras.client.CameraZoomHandler.getZoomLevelCount());
            response.addProperty("zoomMultiplier", es.boffmedia.teras.client.CameraZoomHandler.getCurrentZoomMultiplier());
            response.addProperty("zoomFactor", es.boffmedia.teras.client.CameraZoomHandler.getZoomFactorForLevel(
                es.boffmedia.teras.client.CameraZoomHandler.getZoomLevel()));
            
            // Add all available zoom levels
            JsonObject levels = new JsonObject();
            for (int i = 0; i < es.boffmedia.teras.client.CameraZoomHandler.getZoomLevelCount(); i++) {
                levels.addProperty(String.valueOf(i), 
                    es.boffmedia.teras.client.CameraZoomHandler.getZoomFactorForLevel(i) + "x");
            }
            response.add("availableLevels", levels);
            
            callback.success(gson.toJson(response));
        } catch (Exception e) {
            Teras.LOGGER.error("Error getting zoom level", e);
            callback.failure(0, "Error getting zoom level: " + e.getMessage());
        }
    }

    private static void handleGetWaypoints(IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling getWaypoints query");
        try {
            Collection<Waypoint> waypoints = WaypointStore.INSTANCE.getAll();
            com.google.gson.JsonObject response = new com.google.gson.JsonObject();
            response.addProperty("status", "ok");
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            

            for (Waypoint wp : waypoints) {
                try {
                    com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                    // name
                    try {
                        obj.addProperty("name", (String) Waypoint.class.getMethod("getName").invoke(wp));
                    } catch (Exception e) {
                        obj.addProperty("name", "");
                    }

                    // coords: try getBlockPos(), getPos(), or getX/Y/Z
                    Integer x = null, y = null, z = null;
                    try {
                        java.lang.reflect.Method m = Waypoint.class.getMethod("getBlockPos");
                        Object blockPos = m.invoke(wp);
                        if (blockPos instanceof BlockPos) {
                            BlockPos bp = (BlockPos) blockPos;
                            x = bp.getX(); y = bp.getY(); z = bp.getZ();
                        }
                    } catch (NoSuchMethodException ignored) {}

                    if (x == null) {
                        try {
                            java.lang.reflect.Method m = Waypoint.class.getMethod("getPos");
                            Object blockPos = m.invoke(wp);
                            if (blockPos instanceof BlockPos) {
                                BlockPos bp = (BlockPos) blockPos;
                                x = bp.getX(); y = bp.getY(); z = bp.getZ();
                            }
                        } catch (NoSuchMethodException ignored) {}
                    }

                    if (x == null) {
                        try {
                            java.lang.reflect.Method mx = Waypoint.class.getMethod("getX");
                            java.lang.reflect.Method my = Waypoint.class.getMethod("getY");
                            java.lang.reflect.Method mz = Waypoint.class.getMethod("getZ");
                            x = ((Number) mx.invoke(wp)).intValue();
                            y = ((Number) my.invoke(wp)).intValue();
                            z = ((Number) mz.invoke(wp)).intValue();
                        } catch (NoSuchMethodException ignored) {}
                    }

                    obj.addProperty("x", x == null ? 0 : x);
                    obj.addProperty("y", y == null ? 0 : y);
                    obj.addProperty("z", z == null ? 0 : z);

                    // color
                    try {
                        java.lang.reflect.Method mc = Waypoint.class.getMethod("getColor");
                        Object color = mc.invoke(wp);
                        if (color != null) {
                            java.lang.reflect.Method mget = color.getClass().getMethod("getRGB");
                            int rgb = (Integer) mget.invoke(color);
                            String hex = String.format("#%06X", (0xFFFFFF & rgb));
                            obj.addProperty("color", hex);
                        }
                    } catch (Exception ignored) {
                    }

                    // dimension/world - try several method names
                    try {
                        java.lang.reflect.Method md = Waypoint.class.getMethod("getDimName");
                        Object dim = md.invoke(wp);
                        if (dim != null) obj.addProperty("dimension", dim.toString());
                    } catch (Exception e1) {
                        try {
                            java.lang.reflect.Method md2 = Waypoint.class.getMethod("getDimension");
                            Object dim = md2.invoke(wp);
                            if (dim != null) obj.addProperty("dimension", dim.toString());
                        } catch (Exception ignored) {}
                    }

                    arr.add(obj);
                } catch (Exception e) {
                    Teras.LOGGER.warn("Failed to parse waypoint", e);
                }
            }

            response.add("waypoints", arr);
            callback.success(gson.toJson(response));
        } catch (Exception e) {
            Teras.LOGGER.error("Error handling getWaypoints", e);
            callback.failure(0, "Error handling getWaypoints: " + e.getMessage());
        }
    }

    private static void handleAddWaypoint(String query, IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling addWaypoint query: {}", query);
        try {
            JsonObject json = gson.fromJson(query, JsonObject.class);

            String name = json.has("name") ? json.get("name").getAsString() : "waypoint";
            int x = json.has("x") ? json.get("x").getAsInt() : 0;
            int y = json.has("y") ? json.get("y").getAsInt() : 64;
            int z = json.has("z") ? json.get("z").getAsInt() : 0;
            String colorStr = json.has("color") ? json.get("color").getAsString() : "#FFFFFF";

            String dimension;
            if (json.has("dimension")) {
                dimension = json.get("dimension").getAsString();
            } else {
                // default to player's current dimension key
                if (Minecraft.getInstance().player != null) {
                    dimension = DimensionHelper.getDimKeyName(Minecraft.getInstance().player.level.dimension());
                } else {
                    dimension = "minecraft:overworld";
                }
            }

            // Parse color safely
            Color color;
            try {
                color = Color.decode(colorStr);
            } catch (Exception e) {
                color = Color.WHITE;
            }

            // Create BlockPos and Waypoint
            BlockPos pos = new BlockPos(x, y, z);
            Waypoint wp = new Waypoint(name, pos, color, Waypoint.Type.Normal, dimension, false);

            // Avoid duplicates by name
            Collection<Waypoint> existing = WaypointStore.INSTANCE.getAll();
            boolean exists = existing.stream().anyMatch(w -> {
                try { return w.getName().equals(wp.getName()); } catch (Exception ex) { return false; }
            });

            if (exists) {
                JsonObject resp = new JsonObject();
                resp.addProperty("status", "exists");
                resp.addProperty("message", "Waypoint with that name already exists");
                callback.success(gson.toJson(resp));
                return;
            }

            WaypointStore.INSTANCE.add(wp);

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "ok");
            resp.addProperty("name", name);
            resp.addProperty("x", x);
            resp.addProperty("y", y);
            resp.addProperty("z", z);
            resp.addProperty("dimension", dimension);
            callback.success(gson.toJson(resp));

        } catch (Exception e) {
            Teras.LOGGER.error("Error handling addWaypoint", e);
            callback.failure(0, "Error handling addWaypoint: " + e.getMessage());
        }
    }

    private static void handleSetZoomLevel(String query, IJSQueryCallback callback) {
        Teras.LOGGER.info("Handling setZoomLevel query");
        try {
            JsonObject json = gson.fromJson(query, JsonObject.class);
            int level = json.get("level").getAsInt();
            
            es.boffmedia.teras.client.CameraZoomHandler.setZoomLevel(level);
            
            JsonObject response = new JsonObject();
            response.addProperty("status", "ok");
            response.addProperty("zoomLevel", es.boffmedia.teras.client.CameraZoomHandler.getZoomLevel());
            response.addProperty("zoomFactor", es.boffmedia.teras.client.CameraZoomHandler.getZoomFactorForLevel(
                es.boffmedia.teras.client.CameraZoomHandler.getZoomLevel()));
            
            callback.success(gson.toJson(response));
            Teras.LOGGER.info("Zoom level set to: " + level);
        } catch (Exception e) {
            Teras.LOGGER.error("Error setting zoom level", e);
            callback.failure(0, "Error setting zoom level: " + e.getMessage());
        }
    }

    public static void handlePOST(StringBuilder response, HttpURLConnection con) throws IOException {
        Teras.LOGGER.info("Received a response to a POST request: ");
        Teras.LOGGER.info(response.toString());
        Teras.LOGGER.info("WingullAPI: " + con.getResponseCode());

        String responseString = response.toString();

        try {
            if (responseString.contains("pokedex_event")) {
                PokedexEventResponse pokedexEventResponse = gson.fromJson(responseString, PokedexEventResponse.class);
                pokedexEventResponse.sendMessage();
            }
        } catch (Exception e) {
            Teras.LOGGER.error("Error processing POST response: " + responseString, e);
            throw new IOException("Error processing POST response", e);
        }
    }
}

