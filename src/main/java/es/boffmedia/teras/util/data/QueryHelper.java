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
import es.boffmedia.teras.net.server.serverOld.SMessageVerMisiones;
import es.boffmedia.teras.util.objects.post.PokedexEventResponse;
import es.boffmedia.teras.util.objects._old.serverdata.UserData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.montoyo.mcef.api.IBrowser;
import net.montoyo.mcef.api.IJSQueryCallback;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;

public class QueryHelper {
    private static final String SUCCESS = "{\"status\": \"ok\"}";
    private static final Gson gson = new Gson();

    private enum QueryType {
        GET_USER_DATA,
        OPEN_PC,
        GET_SPAWNS,
        SET_CALL,
        LEAVE_CALL,

        GET_PLAYERS,
        GET_MISIONES,
        DAR_CAJA,

        CHAT_MESSAGE,
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
        Messages.INSTANCE.sendToServer(new SMessageVerMisiones(query));
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
