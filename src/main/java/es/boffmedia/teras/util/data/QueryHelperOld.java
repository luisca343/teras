/*
package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.ClientProxy;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.server.*;
import es.boffmedia.teras.objects.post.PokedexEventResponse;
import es.boffmedia.teras.objects_old.serverdata.UserData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.montoyo.mcef.api.IBrowser;
import net.montoyo.mcef.api.IJSQueryCallback;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;

public class QueryHelperOld {

    public static boolean handleQuery(IBrowser iBrowser, long l, String query, boolean b, IJSQueryCallback callback) {
        Gson gson = new Gson();
        Teras.LOGGER.info("Query recibida: "+query);
        Requests 'GET'
        if(query.contains("getUserData")){
            Messages.INSTANCE.sendToServer(new SMessageDatosServer("query"));
            ClientProxy.callbackMisiones = callback;
            return true;
        }
        if(query.contains("getPlayers")){
            Collection<NetworkPlayerInfo> jugadores = Minecraft.getInstance().getConnection().getOnlinePlayers();
            ArrayList<UserData> usuarios = new ArrayList<>();
            for (NetworkPlayerInfo jugador : jugadores) {
                UserData userData = new UserData(jugador.getProfile().getId().toString(), jugador.getProfile().getName());
                usuarios.add(userData);
            }
            String respuesta = gson.toJson(usuarios);
            callback.success(respuesta);
            return true;
        }
        if(query.contains("cerrarRotom")){
            Teras.PROXY.closeSmartRotom();
            callback.success("");
            return true;
        }
        Requests 'POST'
        if(query.contains("entrarLlamada")){
            Messages.INSTANCE.sendToServer(new SMessageIniciarLlamada(query));
            callback.success("test");
            return true;
        }
        if(query.contains("colgarLlamada")){
            Messages.INSTANCE.sendToServer(new SMessageFinalizarLlamada(query));
            callback.success("test");
            return true;
        }
        if(query.contains("darObjetos")){
            Messages.INSTANCE.sendToServer(new SMessageDarObjetos(query));
            callback.success("test");
            return true;
        }
        if(query.contains("darCaja")){
            Messages.INSTANCE.sendToServer(new SMessageDarCaja(query));
            callback.success("test");
            return true;
        }
        if(query.contains("abrirPC")){
            PCStorage pcStorage = ClientStorageManager.openPC;

            Messages.INSTANCE.sendToServer(new SMessageEncenderPC(pcStorage.uuid.toString()));
            callback.success("test");
            return true;
        }
        if(query.contains("getMisiones")){
            ClientProxy.callbackMisiones = callback;
            Messages.INSTANCE.sendToServer(new SMessageVerMisiones(query));
            return true;
        }
        if(query.contains("entrarCarrera")){
            ClientProxy.callbackMisiones = callback;
            Messages.INSTANCE.sendToServer(new SMessageEntrarCarrera(query));
            return true;
        }
        if(query.contains("taxi")){
            ClientProxy.callbackMCEF = callback;
            Messages.INSTANCE.sendToServer(new SMessageTaxi(query));
            Teras.PROXY.closeSmartRotom();
            return true;
        }
        if(query.contains("getPC")){
            ClientProxy.callbackMCEF = callback;
            Messages.INSTANCE.sendToServer(new SMessageGetPC(query));
            return true;
        }
        if(query.contains("getEquipo")){
            ClientProxy.callbackMCEF = callback;
            Messages.INSTANCE.sendToServer(new SMessageGetEquipo(query));
            return true;
        }

        if(query.contains("setPC")){
            ClientProxy.callbackMCEF = callback;
            Messages.INSTANCE.sendToServer(new SMessageSetPC(query));
            return true;
        }
        if(query.contains("setEquipo")){
            ClientProxy.callbackMCEF = callback;
            Messages.INSTANCE.sendToServer(new SMessageCargarEquipo(query));
            return true;
        }
        return false;
    }

    public static void handlePOST(StringBuilder response, HttpURLConnection con) throws IOException {
        Teras.LOGGER.info("Se ha recibido una respuesta a una petición POST: ");
        Teras.LOGGER.info(response.toString());
        Teras.LOGGER.info("WingullAPI: " + con.getResponseCode());

        String responseString = response.toString();

        if(responseString.contains("pokedex_event")){
            PokedexEventResponse pokedexEventResponse = new Gson().fromJson(responseString, PokedexEventResponse.class);
            pokedexEventResponse.sendMessage();
        }
    }

}
*/