package es.boffmedia.teras.http;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Vanilla statistics for {@code POST /stats}, read from {@code world/stats/<uuid>.json}.
 *
 * <p>Separate from {@link TerasHttpServer} for a linkage reason, not a tidiness one: calling
 * {@code PlayerList.getPlayerStats(Player)} makes the verifier check {@code ServerPlayer} against
 * {@code Player}, which loads a class the Minecraft-free test sourceset does not have — and that
 * turns every unit test of the HTTP server's pure helpers into a {@code NoClassDefFoundError}.</p>
 */
final class PlayerStatsService {
    private PlayerStatsService() {}

    /**
     * Writes an online player's counters to disk so the file is current. Server thread only. No-op
     * when the player is offline — vanilla already flushed them on logout.
     */
    static void flush(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            server.getPlayerList().getPlayerStats(player).save();
        }
    }

    /**
     * The stats file verbatim, or {@code null} if the player has none. Returned whole rather than
     * unwrapped to its {@code stats} object: consumers read {@code blob.stats["minecraft:custom"]}.
     */
    static String read(MinecraftServer server, UUID uuid) throws IOException {
        Path file = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(uuid + ".json");
        // Vanilla writes it on the first save, so a player who never joined has no file at all.
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
    }
}
