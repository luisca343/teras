package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.KartsConfig;
import es.boffmedia.teras.karts.model.KartProvisioning;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackValidator;
import es.boffmedia.teras.karts.mode.ClassicMode;
import es.boffmedia.teras.karts.mode.RaceMode;
import es.boffmedia.teras.karts.store.KartPresetStore;
import es.boffmedia.teras.karts.store.TrackStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which races exist, and who is in them. Replaces the 1.16.5 {@code RaceManager} singleton.
 *
 * <p>One race per circuit at a time: a second race on the same track would need the same grid slots.
 * A player can be in one race at a time, checked on join rather than discovered later when their
 * kart is spawned twice.</p>
 *
 * <p>Server thread only.</p>
 */
public final class KartsRaceManager {
    private KartsRaceManager() {}

    private static final Map<String, RaceSession> RACES = new LinkedHashMap<>();

    /** Result of trying to put a player in a race, so callers can explain the failure in Spanish. */
    public enum JoinResult {
        OK,
        NO_TRACK,
        TRACK_NOT_RACEABLE,
        ALREADY_RACING,
        RACE_IN_PROGRESS,
        GRID_FULL,
        NO_VEHICLES,
        WRONG_DIMENSION
    }

    public static Optional<RaceSession> race(String trackName) {
        return Optional.ofNullable(RACES.get(trackName));
    }

    public static List<RaceSession> activeRaces() {
        return List.copyOf(RACES.values());
    }

    /** The race a player is in, if any. */
    public static Optional<RaceSession> raceOf(UUID player) {
        return RACES.values().stream().filter(session -> session.race().contains(player)).findFirst();
    }

    /**
     * Puts a player into a race on {@code trackName}, creating the lobby if this is the first
     * entrant.
     *
     * @param kartResolver decides which kart model each racer gets; supplied by the caller so the
     *                     provisioning rules live outside the engine
     */
    public static JoinResult join(MinecraftServer server, ServerPlayer player, String trackName,
                                  Integer laps, RaceMode mode, KartProvisioning provisioning) {
        if (!es.boffmedia.teras.karts.vehicle.KartVehicles.get().available()) {
            return JoinResult.NO_VEHICLES;
        }
        if (raceOf(player.getUUID()).isPresent()) {
            return JoinResult.ALREADY_RACING;
        }
        KartTrack track = TrackStore.get(trackName);
        if (track == null) {
            return JoinResult.NO_TRACK;
        }
        if (!TrackValidator.isRaceable(track)) {
            return JoinResult.TRACK_NOT_RACEABLE;
        }

        RaceSession session = RACES.get(trackName);
        if (session != null && session.race().phase() != RaceCore.Phase.LOBBY) {
            return JoinResult.RACE_IN_PROGRESS;
        }
        if (session == null) {
            ServerLevel level = levelFor(server, track);
            if (level == null) {
                return JoinResult.WRONG_DIMENSION;
            }
            RaceMode chosen = mode == null ? new ClassicMode() : mode;
            RaceSettings settings = KartsConfig.raceSettings();
            if (!chosen.usesVoting()) {
                settings = settings.soloed();
            }
            KartProvisioning rule = provisioning == null ? defaultProvisioning() : provisioning;
            session = new RaceSession(server, level, track,
                    laps == null ? track.defaultLaps() : laps,
                    settings, chosen, new KartResolver(rule));
            RACES.put(trackName, session);
        }

        if (!session.race().join(player.getUUID(), player.getGameProfile().getName())) {
            return JoinResult.GRID_FULL;
        }
        return JoinResult.OK;
    }

    /** Removes a player from whatever race they are in. */
    public static boolean leave(UUID player) {
        Optional<RaceSession> session = raceOf(player);
        if (session.isEmpty()) {
            return false;
        }
        RaceSession race = session.get();
        race.race().leave(player);
        discardIfEmpty(race);
        return true;
    }

    public static boolean vote(UUID player, boolean voting, long tick) {
        Optional<RaceSession> found = raceOf(player);
        if (found.isEmpty()) {
            return false;
        }
        RaceSession session = found.get();
        if (!session.race().vote(player, voting)) {
            return false;
        }
        if (session.race().shouldStart()) {
            session.race().beginCountdown(tick);
        }
        return true;
    }

    /** Admin force-start, skipping the vote. */
    public static boolean forceStart(String trackName, long tick) {
        RaceSession session = RACES.get(trackName);
        if (session == null || session.race().phase() != RaceCore.Phase.LOBBY) {
            return false;
        }
        session.race().beginCountdown(tick);
        session.race().forceStart(tick);
        return true;
    }

    /** Admin countdown, which respects the normal grid hold. */
    public static boolean beginCountdown(String trackName, long tick) {
        RaceSession session = RACES.get(trackName);
        if (session == null || session.race().phase() != RaceCore.Phase.LOBBY) {
            return false;
        }
        session.race().beginCountdown(tick);
        return true;
    }

    public static boolean cancel(String trackName, String reason) {
        RaceSession session = RACES.remove(trackName);
        if (session == null) {
            return false;
        }
        session.race().cancel(reason);
        session.teardown();
        return true;
    }

    /** Ticks every live race and clears out the ones that have ended. */
    static void tickAll(long tick) {
        if (RACES.isEmpty()) {
            return;
        }
        for (RaceSession session : List.copyOf(RACES.values())) {
            try {
                session.tick(tick);
            } catch (Exception e) {
                Teras.LOGGER.error("Karts: race on '{}' failed and was cancelled",
                        session.race().trackName(), e);
                session.race().cancel("La carrera se ha cancelado por un error interno.");
                session.teardown();
            }
            if (session.race().isOver()) {
                RACES.remove(session.race().trackName());
            }
        }
    }

    /** Drops a lobby nobody is left in, so the circuit is free again. */
    private static void discardIfEmpty(RaceSession session) {
        if (session.race().phase() == RaceCore.Phase.LOBBY && session.race().participants().isEmpty()) {
            RACES.remove(session.race().trackName());
            session.teardown();
        }
    }

    /** Removes every race, used when the server stops. */
    static void clearAll(String reason) {
        for (RaceSession session : List.copyOf(RACES.values())) {
            session.race().cancel(reason);
            session.teardown();
        }
        RACES.clear();
    }

    /**
     * What a race uses when the caller names no rule: the first preset an admin defined, as a spec
     * race. With no presets at all there is nothing sensible to hand out, and the racer is told so
     * at the grid rather than the race failing to form.
     */
    private static KartProvisioning defaultProvisioning() {
        return KartPresetStore.names().stream().sorted().findFirst()
                .map(KartProvisioning::spec)
                .orElseGet(() -> KartProvisioning.spec(""));
    }

    private static ServerLevel levelFor(MinecraftServer server, KartTrack track) {
        if (track.dimension() == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(track.dimension());
        if (id == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(id)) {
                return level;
            }
        }
        return null;
    }
}
