package es.boffmedia.teras.karts.editor;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackCheckpoint;
import es.boffmedia.teras.karts.model.TrackPoint;
import es.boffmedia.teras.karts.store.TrackStore;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Draws the circuit an admin is editing: checkpoint boxes, the grid slots, and the racing line the
 * ranking spline follows.
 *
 * <p>Particles are sent only to the admin who asked, so an editing session never litters the world
 * for players nearby. Redrawn on a timer rather than every tick — particles linger long enough that
 * more often would only cost bandwidth.</p>
 *
 * <p>Ported from the 1.16.5 {@code TrackEditor}, whose visualisation was the one part of the editor
 * that worked well.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class TrackVisualizer {
    private TrackVisualizer() {}

    /** Redraw cadence. Particles outlive this, so the outline looks continuous. */
    private static final int REDRAW_INTERVAL_TICKS = 20;
    /** Beyond this the outline is unreadable anyway, and drawing it is pure waste. */
    private static final double MAX_RENDER_DISTANCE = 128;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
    /** Spacing between particles along a box edge. */
    private static final double EDGE_STEP = 1.0;

    private static int ticks;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < REDRAW_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;

        Map<UUID, String> watching = TrackEditorSessions.visualising();
        if (watching.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, String> entry : watching.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            KartTrack track = TrackStore.get(entry.getValue());
            if (track == null) {
                TrackEditorSessions.hideTrack(entry.getKey());
                continue;
            }
            draw(player, track);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TrackEditorSessions.clear(event.getEntity().getUUID());
    }

    /** Draws the whole circuit for one admin. Public so {@code preview} can draw a single element. */
    public static void draw(ServerPlayer player, KartTrack track) {
        ServerLevel level = player.serverLevel();
        for (TrackCheckpoint checkpoint : track.checkpoints()) {
            drawBox(level, player, checkpoint, ParticleTypes.END_ROD);
        }
        for (TrackPoint slot : track.startingPoints()) {
            drawMarker(level, player, slot, ParticleTypes.HAPPY_VILLAGER);
        }
        for (TrackPoint sample : track.path().samplePoints()) {
            spawn(level, player, ParticleTypes.FLAME, sample.x(), sample.y() + 0.2, sample.z());
        }
    }

    public static void drawCheckpoint(ServerPlayer player, TrackCheckpoint checkpoint) {
        drawBox(player.serverLevel(), player, checkpoint, ParticleTypes.END_ROD);
    }

    public static void drawStartingPoint(ServerPlayer player, TrackPoint point) {
        drawMarker(player.serverLevel(), player, point, ParticleTypes.HAPPY_VILLAGER);
    }

    /** The twelve edges of the checkpoint's padded box. */
    private static void drawBox(ServerLevel level, ServerPlayer player, TrackCheckpoint checkpoint,
                                ParticleOptions particle) {
        double minX = checkpoint.minX();
        double maxX = checkpoint.maxX();
        double minY = checkpoint.minY();
        double maxY = checkpoint.maxY();
        double minZ = checkpoint.minZ();
        double maxZ = checkpoint.maxZ();

        for (double x = minX; x <= maxX; x += EDGE_STEP) {
            spawn(level, player, particle, x, minY, minZ);
            spawn(level, player, particle, x, minY, maxZ);
            spawn(level, player, particle, x, maxY, minZ);
            spawn(level, player, particle, x, maxY, maxZ);
        }
        for (double y = minY; y <= maxY; y += EDGE_STEP) {
            spawn(level, player, particle, minX, y, minZ);
            spawn(level, player, particle, minX, y, maxZ);
            spawn(level, player, particle, maxX, y, minZ);
            spawn(level, player, particle, maxX, y, maxZ);
        }
        for (double z = minZ; z <= maxZ; z += EDGE_STEP) {
            spawn(level, player, particle, minX, minY, z);
            spawn(level, player, particle, minX, maxY, z);
            spawn(level, player, particle, maxX, minY, z);
            spawn(level, player, particle, maxX, maxY, z);
        }
    }

    /** A short vertical column, so a grid slot is visible from across the track. */
    private static void drawMarker(ServerLevel level, ServerPlayer player, TrackPoint point,
                                   ParticleOptions particle) {
        for (double dy = 0; dy <= 2.0; dy += 0.5) {
            spawn(level, player, particle, point.x(), point.y() + dy, point.z());
        }
    }

    private static void spawn(ServerLevel level, ServerPlayer player, ParticleOptions particle,
                              double x, double y, double z) {
        if (player.distanceToSqr(x, y, z) > MAX_RENDER_DISTANCE_SQ) {
            return;
        }
        level.sendParticles(player, particle, true, x, y, z, 1, 0, 0, 0, 0);
    }
}
