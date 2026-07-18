package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackPoint;
import es.boffmedia.teras.karts.mode.RaceMode;
import es.boffmedia.teras.karts.store.RaceVehicleLedger;
import es.boffmedia.teras.karts.vehicle.KartLoadout;
import es.boffmedia.teras.karts.vehicle.KartSpec;
import es.boffmedia.teras.karts.vehicle.KartVehicleService;
import es.boffmedia.teras.karts.vehicle.KartVehicles;
import es.boffmedia.teras.karts.vehicle.VehicleRef;
import es.boffmedia.teras.net.RaceHudPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One live race: binds a {@link RaceCore} to the world, supplying it kart positions each tick and
 * carrying out what it asks for.
 *
 * <p>This is the only place in the race path that touches Minecraft or Immersive Vehicles, which is
 * what keeps {@link RaceCore} testable. Every exit — finishing, cancelling, timing out, the last
 * racer disconnecting — funnels through {@link #teardown()}, so there is a single place responsible
 * for making sure no kart is left behind.</p>
 */
public final class RaceSession implements RaceCallbacks {

    private final RaceCore race;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final Map<UUID, VehicleRef> karts = new LinkedHashMap<>();
    private final Map<UUID, KartSpec> assignedKarts = new LinkedHashMap<>();
    private final KartResolver kartResolver;

    private boolean tornDown;

    public RaceSession(MinecraftServer server, ServerLevel level, KartTrack track, int laps,
                       RaceSettings settings, RaceMode mode, KartResolver kartResolver) {
        this.server = server;
        this.level = level;
        this.kartResolver = kartResolver;
        this.race = new RaceCore(track, laps, settings, mode, this);
    }

    public RaceCore race() {
        return race;
    }

    /** How karts are handed out in this race, and where a racer's own pick is recorded. */
    public KartResolver resolver() {
        return kartResolver;
    }

    public ServerLevel level() {
        return level;
    }

    /** Feeds the engine a tick, with the current position of every racer who is in a kart. */
    public void tick(long tick) {
        race.tick(tick, currentPositions());
        if (race.isOver() && !tornDown) {
            teardown();
        }
    }

    /**
     * Where every racer's kart is. A racer whose kart has gone, or who is not sitting in it, is
     * left out — that is what starts the engine's dismount grace period.
     *
     * <p>Read from the kart itself rather than from the player, so it is the server's own view of
     * the vehicle and not something a client could influence.</p>
     */
    private Map<UUID, TrackPoint> currentPositions() {
        KartVehicleService vehicles = KartVehicles.get();
        Map<UUID, TrackPoint> positions = new HashMap<>();
        for (RaceParticipantState participant : race.activeParticipants()) {
            UUID id = participant.playerId();
            VehicleRef kart = karts.get(id);
            if (kart == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            // Only count them as racing while they are actually aboard their own kart.
            Optional<VehicleRef> riding = vehicles.vehicleOf(player);
            if (riding.isEmpty() || !riding.get().id().equals(kart.id())) {
                continue;
            }
            Optional<Vec3> position = vehicles.positionOf(kart);
            if (position.isEmpty()) {
                continue;
            }
            Vec3 at = position.get();
            positions.put(id, TrackPoint.at(at.x, at.y, at.z));
        }
        return positions;
    }

    // --- RaceCallbacks ----------------------------------------------------------------------

    @Override
    public void placeOnGrid(UUID playerId, TrackPoint slot) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        KartLoadout loadout = kartResolver.resolve(playerId).orElse(null);
        if (loadout == null || !loadout.isValid()) {
            message(playerId, "No se pudo asignarte un kart ("
                    + kartResolver.provisioning().describe() + ").");
            return;
        }
        Optional<VehicleRef> spawned = KartVehicles.get()
                .spawn(level, loadout, player, slot.x(), slot.y(), slot.z(), slot.yaw());
        if (spawned.isEmpty()) {
            message(playerId, "No se pudo crear tu kart (" + loadout.describe() + ").");
            return;
        }
        VehicleRef kart = spawned.get();
        // Recorded before the race is told, so a crash between here and the race ending still
        // leaves a trail the next startup can clean up.
        RaceVehicleLedger.track(kart);
        karts.put(playerId, kart);
        assignedKarts.put(playerId, loadout.model());

        KartVehicles.get().freeze(kart);
        if (!KartVehicles.get().seat(kart, player)) {
            message(playerId, "No se pudo sentarte en el kart.");
        }
    }

    @Override
    public void releaseAll() {
        karts.values().forEach(kart -> KartVehicles.get().release(kart));
    }

    @Override
    public void removeKart(UUID playerId) {
        VehicleRef kart = karts.remove(playerId);
        if (kart == null) {
            return;
        }
        KartVehicles.get().remove(kart);
        RaceVehicleLedger.untrack(kart);
    }

    @Override
    public boolean reseat(UUID playerId) {
        VehicleRef kart = karts.get(playerId);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (kart == null || player == null || !KartVehicles.get().exists(kart)) {
            return false;
        }
        return KartVehicles.get().seat(kart, player);
    }

    @Override
    public void title(UUID playerId, String title, String subtitle) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
    }

    @Override
    public void message(UUID playerId, String message) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public void broadcast(String message) {
        for (RaceParticipantState participant : race.participants()) {
            message(participant.playerId(), message);
        }
    }

    @Override
    public void sound(UUID playerId, RaceSound sound) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                soundEventFor(sound), SoundSource.PLAYERS, 1.0f, pitchFor(sound));
    }

    @Override
    public void hud(UUID playerId, RaceHudState state) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new RaceHudPayload(
                state.phase().ordinal(),
                state.countdown(),
                state.position(),
                state.totalRacers(),
                state.lap(),
                state.totalLaps(),
                (int) Math.min(Integer.MAX_VALUE, state.elapsedMs()),
                state.bestLapMs() < 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, state.bestLapMs()),
                state.wrongWay()));
    }

    @Override
    public void finished(RaceResult result) {
        RaceResults.publish(result);
    }

    /**
     * Releases everything this race owns. Safe to call more than once — every exit path leads here,
     * and some of them overlap (a race can finish on the same tick its last racer disconnects).
     */
    public void teardown() {
        if (tornDown) {
            return;
        }
        tornDown = true;
        for (VehicleRef kart : karts.values()) {
            try {
                KartVehicles.get().remove(kart);
                RaceVehicleLedger.untrack(kart);
            } catch (Exception e) {
                Teras.LOGGER.warn("Karts: failed to clean up kart {}: {}", kart.id(), e.toString());
            }
        }
        karts.clear();
        RaceVehicleLedger.flush();
    }

    /** Which kart model each racer drove, for the results report. */
    public Map<UUID, KartSpec> assignedKarts() {
        return Map.copyOf(assignedKarts);
    }

    private static SoundEvent soundEventFor(RaceSound sound) {
        return switch (sound) {
            case COUNTDOWN_TICK, COUNTDOWN_GO -> SoundEvents.NOTE_BLOCK_PLING.value();
            case LAP_COMPLETED -> SoundEvents.NOTE_BLOCK_BELL.value();
            case FINISH_WINNER -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case FINISH_OTHER -> SoundEvents.PLAYER_LEVELUP;
            case ELIMINATED -> SoundEvents.ANVIL_LAND;
            case WRONG_WAY -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
        };
    }

    /** The "GO" pip is the same sound an octave up, which is how the 1.16.5 countdown read. */
    private static float pitchFor(RaceSound sound) {
        return sound == RaceSound.COUNTDOWN_GO ? 2.0f : 1.0f;
    }
}
