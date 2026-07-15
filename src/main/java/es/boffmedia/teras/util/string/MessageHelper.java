package es.boffmedia.teras.util.string;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Minimal chat-message helper, ported from 1.16.5 to the 1.21 API ({@code sendMessage(Component,
 * UUID)}→{@link ServerPlayer#sendSystemMessage(Component)}). Messages accept legacy {@code §} colour
 * codes, which {@link Component#literal} renders.
 */
public final class MessageHelper {
    private MessageHelper() {}

    public static void enviarMensaje(ServerPlayer player, String mensaje) {
        player.sendSystemMessage(Component.literal(mensaje));
    }

    public static void enviarMensajeGlobal(MinecraftServer server, String mensaje) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enviarMensaje(player, mensaje);
        }
    }
}
