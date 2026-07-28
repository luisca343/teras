package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.instance.DungeonRun;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The three things every part of the run loop does to a party: find one member, collect the ones
 * who are online, tell them all something.
 *
 * <p>Written once because it was written twice — {@code DungeonRunManager} and {@code RunEngine}
 * each carried an identical {@code message} and an identical present-player loop, and "look the
 * player up, skip them if they are offline" appeared some thirty times between them. Offline is the
 * normal case here, not an error: a party member can drop at any point in a run and every one of
 * these has to keep working for whoever is left.</p>
 */
public final class RunPartyHelper {
    private RunPartyHelper() {}

    /** The player, or null if offline. */
    public static ServerPlayer playerOf(MinecraftServer server, UUID uuid) {
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    /** Every online member of the run's party. */
    public static List<ServerPlayer> onlineMembers(MinecraftServer server, DungeonRun run) {
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID member : run.party().keySet()) {
            ServerPlayer player = playerOf(server, member);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /** Whether anyone at all is still connected — the watchdog's desertion question. */
    public static boolean anyOnline(MinecraftServer server, DungeonRun run) {
        for (UUID member : run.party().keySet()) {
            if (playerOf(server, member) != null) {
                return true;
            }
        }
        return false;
    }

    /** A line to every online member. */
    public static void message(MinecraftServer server, DungeonRun run, String text) {
        for (ServerPlayer player : onlineMembers(server, run)) {
            player.sendSystemMessage(Component.literal(text));
        }
    }
}
