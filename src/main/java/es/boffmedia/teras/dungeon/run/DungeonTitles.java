package es.boffmedia.teras.dungeon.run;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Screen titles for the moments a chat line was too quiet for: arriving on a floor, the boss
 * falling, dying, finishing the run. Same three-packet shape the karts session uses — an animation
 * frame, then the title, then the subtitle.
 */
public final class DungeonTitles {
    private DungeonTitles() {}

    private static final int FADE_IN = 5;
    private static final int STAY = 30;
    private static final int FADE_OUT = 10;

    public static void send(ServerPlayer player, String title, String subtitle) {
        if (player == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN, STAY, FADE_OUT));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(subtitle == null ? "" : subtitle)));
    }

    /** The same title to every member of a party. */
    public static void sendAll(net.minecraft.server.MinecraftServer server,
                               Iterable<java.util.UUID> members, String title, String subtitle) {
        for (java.util.UUID member : members) {
            send(server.getPlayerList().getPlayer(member), title, subtitle);
        }
    }
}
