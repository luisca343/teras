package es.boffmedia.teras.util.string;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.STitlePacket;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

public class MessageHelper {

    public static void enviarMensajeGlobal(String mensaje) {
        for (ServerPlayerEntity player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            enviarMensaje(player, mensaje);
        }
    }

    public static void enviarMensaje(String uuid, String mensaje) {
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(UUID.fromString(uuid));
        if (player != null) {
            enviarMensaje(player, mensaje);
        }
    }

    public static void enviarMensaje(PlayerEntity player, String mensaje) {
        player.sendMessage(new StringTextComponent(mensaje), UUID.randomUUID());
    }


    public static void enviarMensaje(ArrayList<ServerPlayerEntity> jugadores, String mensaje) {
        for (ServerPlayerEntity jugador : jugadores) {
            enviarMensaje(jugador, mensaje);
        }
    }


    public static void enviarTitulo(ServerPlayerEntity player, String titulo, int entrada, int permanencia, int salida) {
        player.connection.send(new STitlePacket(STitlePacket.Type.TITLE, new StringTextComponent(titulo),entrada,permanencia,salida));
    }


    public static void enviarTitulo(ServerPlayerEntity player, String titulo) {
        enviarTitulo(player, titulo, 0, 20, 0);
    }

    public static String formatearTiempo(long tiempo){
        return new SimpleDateFormat("mm:ss:SSS").format(new Date(tiempo));
    }

    public static String formatearQuery(String query, String[] partes){
        String[] parametros = new String[partes.length - 1];
        for (int i = 0; i < parametros.length; i++) {
            parametros[i] = "\""+partes[i]+"\"";
        }

        return String.format("%s(%s)", query, parametros);
    }
}
