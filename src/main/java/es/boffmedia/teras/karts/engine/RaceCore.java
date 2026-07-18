package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackCheckpoint;
import es.boffmedia.teras.karts.model.TrackPoint;
import es.boffmedia.teras.karts.mode.RaceMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One race, from lobby to results. Pure logic: no Minecraft, no Immersive Vehicles, no threads and
 * no wall clock — it is driven by {@link #tick} and reaches the world only through
 * {@link RaceCallbacks}, which is what makes the whole thing unit-testable.
 *
 * <p><b>Ticks, not threads.</b> The 1.16.5 race ran its countdown on a daemon thread with
 * {@code Thread.sleep(1000)} and its standings on a second thread polling every second, both
 * touching entities and sending packets from off the server thread. Everything here happens in
 * {@link #tick}, called from the server tick.</p>
 *
 * <p><b>Checkpoint order.</b> Gates must be crossed in the order they were authored; the last one
 * is the finish line, so completing it completes a lap. Crossing is tested against the segment the
 * kart travelled since the previous tick — see {@link TrackCheckpoint#crossed} for why containment
 * alone was not enough.</p>
 */
public final class RaceCore {

    public enum Phase { LOBBY, COUNTDOWN, RUNNING, FINISHED, CANCELLED }

    /**
     * How far progress may jump between samples before it is read as a lap wrap rather than real
     * travel. Progress runs 0→1 per lap, so a large negative step is the seam, not a reversal.
     */
    private static final double WRAP_THRESHOLD = 0.5;
    /** How far backwards a racer must go before it counts as driving the wrong way. */
    private static final double WRONG_WAY_THRESHOLD = 0.002;

    private final String trackName;
    private final KartTrack track;
    private final int laps;
    private final RaceSettings settings;
    private final RaceMode mode;
    private final RaceCallbacks callbacks;

    private final Map<UUID, RaceParticipantState> participants = new LinkedHashMap<>();
    private final List<RaceParticipantState> finishOrder = new ArrayList<>();
    private final Map<UUID, Integer> standings = new LinkedHashMap<>();

    private Phase phase = Phase.LOBBY;
    private long countdownEndsAtTick;
    private long startTick;
    /** The tick most recently processed, so a race ending mid-tick can time its survivors. */
    private long lastTick;
    private long lastCountdownPipShown = -1;
    private int ticksSinceRanking;
    private int ticksSinceHud;

    public RaceCore(KartTrack track, int laps, RaceSettings settings, RaceMode mode,
                    RaceCallbacks callbacks) {
        this.track = track;
        this.trackName = track.name();
        this.laps = Math.max(1, laps);
        this.settings = settings;
        this.mode = mode;
        this.callbacks = callbacks;
    }

    // --- lobby ------------------------------------------------------------------------------

    public boolean join(UUID player, String name) {
        if (phase != Phase.LOBBY || participants.containsKey(player)) {
            return false;
        }
        if (participants.size() >= Math.max(1, track.gridSize())) {
            return false;
        }
        participants.put(player, new RaceParticipantState(player, name));
        return true;
    }

    /**
     * Removes a racer. Before the start that is just leaving the lobby; during the race it is a
     * retirement, and their kart goes with them.
     */
    public boolean leave(UUID player) {
        RaceParticipantState participant = participants.get(player);
        if (participant == null) {
            return false;
        }
        if (phase == Phase.LOBBY) {
            participants.remove(player);
            return true;
        }
        if (participant.isActive()) {
            retire(participant);
        }
        return true;
    }

    public boolean vote(UUID player, boolean voting) {
        RaceParticipantState participant = participants.get(player);
        if (participant == null || phase != Phase.LOBBY) {
            return false;
        }
        participant.setVoted(voting);
        return true;
    }

    public int votes() {
        return (int) participants.values().stream().filter(RaceParticipantState::voted).count();
    }

    /** How many entrants must vote before the race will start. */
    public int votesNeeded() {
        int required = (int) Math.ceil(participants.size() * settings.voteThresholdPct() / 100.0);
        return Math.max(1, required);
    }

    /**
     * Whether the vote has carried. Requires the minimum field as well as the share, so one player
     * voting for themselves cannot start a race alone — which is exactly what 1.16.5 allowed.
     */
    public boolean shouldStart() {
        if (phase != Phase.LOBBY || !mode.usesVoting()) {
            return false;
        }
        return participants.size() >= mode.minPlayers(settings.minPlayers())
                && votes() >= votesNeeded();
    }

    /** Puts everyone on the grid and starts the countdown. */
    public void beginCountdown(long tick) {
        if (phase != Phase.LOBBY || participants.isEmpty()) {
            return;
        }
        phase = Phase.COUNTDOWN;
        countdownEndsAtTick = tick + settings.countdownTicks();
        lastCountdownPipShown = -1;

        List<TrackPoint> grid = track.startingPoints();
        int slot = 0;
        for (RaceParticipantState participant : participants.values()) {
            callbacks.placeOnGrid(participant.playerId(), grid.get(slot % grid.size()));
            slot++;
        }
        broadcast("La carrera en " + track.displayName() + " va a empezar.");
    }

    /** Skips the remaining countdown. Used by the admin force-start. */
    public void forceStart(long tick) {
        if (phase == Phase.LOBBY) {
            beginCountdown(tick);
        }
        countdownEndsAtTick = tick;
    }

    public void cancel(String reason) {
        if (phase == Phase.FINISHED || phase == Phase.CANCELLED) {
            return;
        }
        phase = Phase.CANCELLED;
        for (RaceParticipantState participant : participants.values()) {
            callbacks.removeKart(participant.playerId());
            callbacks.hud(participant.playerId(), RaceHudState.hidden());
            if (reason != null) {
                callbacks.message(participant.playerId(), reason);
            }
        }
    }

    // --- tick -------------------------------------------------------------------------------

    /**
     * Advances the race one server tick.
     *
     * @param tick      the server's tick counter — the race's only clock
     * @param positions where each racer's kart is; a racer missing from the map is not in a kart,
     *                  which starts the dismount grace period
     */
    public void tick(long tick, Map<UUID, TrackPoint> positions) {
        lastTick = tick;
        switch (phase) {
            case COUNTDOWN -> tickCountdown(tick);
            case RUNNING -> tickRunning(tick, positions);
            default -> { }
        }
    }

    private void tickCountdown(long tick) {
        long remaining = countdownEndsAtTick - tick;
        if (remaining <= 0) {
            start(tick);
            return;
        }
        // One pip per second, and only when the second actually changes.
        long secondsLeft = (remaining + RaceSettings.TICKS_PER_SECOND - 1) / RaceSettings.TICKS_PER_SECOND;
        if (secondsLeft != lastCountdownPipShown) {
            lastCountdownPipShown = secondsLeft;
            for (RaceParticipantState participant : participants.values()) {
                if (!participant.isActive()) {
                    continue;
                }
                callbacks.title(participant.playerId(), String.valueOf(secondsLeft), "");
                callbacks.sound(participant.playerId(), RaceSound.COUNTDOWN_TICK);
                callbacks.hud(participant.playerId(), hudFor(participant, tick, (int) secondsLeft));
            }
        }
    }

    private void start(long tick) {
        phase = Phase.RUNNING;
        startTick = tick;
        callbacks.releaseAll();
        for (RaceParticipantState participant : participants.values()) {
            // Someone who left during the countdown keeps their entry, but must not be told the
            // race they walked out of has started.
            if (!participant.isActive()) {
                continue;
            }
            participant.beginRacing(tick);
            callbacks.title(participant.playerId(), "¡YA!", "");
            callbacks.sound(participant.playerId(), RaceSound.COUNTDOWN_GO);
            callbacks.hud(participant.playerId(), hudFor(participant, tick, 0));
        }
    }

    private void tickRunning(long tick, Map<UUID, TrackPoint> positions) {
        for (RaceParticipantState participant : List.copyOf(participants.values())) {
            if (!participant.isActive()) {
                continue;
            }
            TrackPoint position = positions.get(participant.playerId());
            if (position == null) {
                handleMissing(participant);
                continue;
            }
            participant.clearMissingTicks();
            advance(participant, position, tick);
        }

        if (settings.timeoutTicks() > 0 && tick - startTick >= settings.timeoutTicks()) {
            timeOut();
        }

        if (++ticksSinceRanking >= settings.rankingIntervalTicks()) {
            ticksSinceRanking = 0;
            recomputeStandings();
        }
        if (++ticksSinceHud >= settings.hudIntervalTicks()) {
            ticksSinceHud = 0;
            for (RaceParticipantState participant : participants.values()) {
                if (participant.isActive()) {
                    callbacks.hud(participant.playerId(), hudFor(participant, tick, -1));
                }
            }
        }

        if (mode.isFinished(this)) {
            finish();
        }
    }

    /** How often to retry putting a racer back in their kart while they are out of it. */
    private static final int RESEAT_RETRY_TICKS = 10;

    /**
     * A racer who is not in their kart: keep trying to put them back, and retire them only once the
     * grace period runs out.
     *
     * <p>Retried rather than attempted once, because the common reasons a reseat fails are
     * transient — the kart momentarily unresolvable at a chunk edge, or the player mid-teleport —
     * and a single failed attempt would otherwise guarantee a retirement several seconds later even
     * though the kart was available again on the next tick.</p>
     */
    private void handleMissing(RaceParticipantState participant) {
        int missing = participant.addMissingTick();
        if (missing >= settings.dismountGraceTicks()) {
            callbacks.message(participant.playerId(), "Has estado demasiado tiempo fuera del kart.");
            retire(participant);
            return;
        }
        if (missing != 1 && missing % RESEAT_RETRY_TICKS != 0) {
            return;
        }
        // Deliberately does not clear the grace counter on a successful call: what proves a racer is
        // back is their kart reporting a position again, which tickRunning already acts on. Trusting
        // the return value instead would leave anyone whose reseat claims success but never takes
        // effect stuck in the race forever, immune to the timeout.
        if (!callbacks.reseat(participant.playerId()) && missing == 1) {
            callbacks.message(participant.playerId(), "Vuelve a tu kart para seguir en carrera.");
        }
    }

    /**
     * Credits at most one gate per tick, deliberately: a racer flung across the circuit — by a
     * glitch, a teleport, or a hostile plugin — advances a single checkpoint rather than banking a
     * whole lap, however many gates the segment happened to pass through.
     */
    private void advance(RaceParticipantState participant, TrackPoint position, long tick) {
        List<TrackCheckpoint> checkpoints = track.checkpoints();
        TrackPoint previous = participant.lastPosition();
        TrackCheckpoint next = checkpoints.get(participant.checkpointIndex());

        if (next.crossed(previous, position)) {
            boolean wasFinalGate = participant.checkpointIndex() == checkpoints.size() - 1;
            if (wasFinalGate) {
                participant.completeLap(tick);
                onLapCompleted(participant, tick);
            } else {
                participant.advanceCheckpoint();
            }
        }

        participant.setLastPosition(position);
        updateWrongWay(participant, position);
    }

    private void onLapCompleted(RaceParticipantState participant, long tick) {
        if (participant.lapsCompleted() >= laps) {
            crossFinishLine(participant, tick);
            return;
        }
        callbacks.message(participant.playerId(),
                "Vuelta " + participant.lapsCompleted() + "/" + laps + " completada.");
        callbacks.sound(participant.playerId(), RaceSound.LAP_COMPLETED);
        mode.onLapCompleted(this, participant);
    }

    private void crossFinishLine(RaceParticipantState participant, long tick) {
        int place = finishOrder.size() + 1;
        participant.finish(tick, place);
        finishOrder.add(participant);

        long elapsed = RaceSettings.ticksToMillis(tick - startTick);
        if (place == 1) {
            callbacks.title(participant.playerId(), "¡VICTORIA!", "Primero en " + formatTime(elapsed));
            callbacks.sound(participant.playerId(), RaceSound.FINISH_WINNER);
            broadcast(participant.playerName() + " ha ganado en " + formatTime(elapsed) + ".");
        } else {
            callbacks.title(participant.playerId(), "CARRERA TERMINADA",
                    place + "º en " + formatTime(elapsed));
            callbacks.sound(participant.playerId(), RaceSound.FINISH_OTHER);
        }
        callbacks.removeKart(participant.playerId());
        callbacks.hud(participant.playerId(), RaceHudState.hidden());
    }

    /**
     * Tracks whether a racer is going backwards, ignoring the lap seam: progress running 0.98→0.02
     * is a completed lap, not a reversal.
     */
    private void updateWrongWay(RaceParticipantState participant, TrackPoint position) {
        double progress = track.path().progressOf(position);
        if (!participant.hasProgress()) {
            participant.setProgress(progress);
            return;
        }
        double delta = progress - participant.lastProgress();
        if (delta < -WRAP_THRESHOLD) {
            delta += 1;
        } else if (delta > WRAP_THRESHOLD) {
            delta -= 1;
        }
        participant.setProgress(progress);

        if (delta < -WRONG_WAY_THRESHOLD) {
            int backwards = participant.addWrongWayTick();
            if (backwards == settings.wrongWayGraceTicks()) {
                callbacks.title(participant.playerId(), "", "¡SENTIDO CONTRARIO!");
                callbacks.sound(participant.playerId(), RaceSound.WRONG_WAY);
            }
        } else {
            participant.clearWrongWayTicks();
        }
    }

    private void timeOut() {
        for (RaceParticipantState participant : List.copyOf(participants.values())) {
            if (participant.isActive()) {
                callbacks.message(participant.playerId(), "Se acabó el tiempo de la carrera.");
                retire(participant);
            }
        }
    }

    private void retire(RaceParticipantState participant) {
        participant.setDnf(true);
        callbacks.removeKart(participant.playerId());
        callbacks.hud(participant.playerId(), RaceHudState.hidden());
    }

    /** Knocks a racer out. Used by elimination; separate from retiring so results can tell them apart. */
    public void eliminate(RaceParticipantState participant) {
        if (!participant.isActive()) {
            return;
        }
        participant.setEliminated(true);
        callbacks.title(participant.playerId(), "¡ELIMINADO!", "");
        callbacks.sound(participant.playerId(), RaceSound.ELIMINATED);
        callbacks.removeKart(participant.playerId());
        callbacks.hud(participant.playerId(), RaceHudState.hidden());
    }

    private void finish() {
        if (phase == Phase.FINISHED) {
            return;
        }
        // Anyone still driving when the race ends is credited with finishing now. In a classic race
        // there is nobody left by definition; in elimination this is the survivor, who wins by being
        // the last one standing rather than by crossing the line, and would otherwise be recorded as
        // a retirement.
        for (RaceParticipantState participant : List.copyOf(participants.values())) {
            if (participant.isActive()) {
                crossFinishLine(participant, lastTick);
            }
        }
        phase = Phase.FINISHED;
        recomputeStandings();
        callbacks.finished(buildResult());
    }

    /** Broadcasts to everyone in the race. Used by modes to explain what just happened. */
    public void announce(String message) {
        broadcast(message);
    }

    /** Whether the race is under way — modes use this so an empty lobby is not "finished". */
    public boolean hasStarted() {
        return phase == Phase.RUNNING || phase == Phase.FINISHED;
    }

    // --- standings --------------------------------------------------------------------------

    /**
     * Ranks everyone still driving: more laps first, then further round the current lap. Finishers
     * keep the place they crossed the line in and are always ahead of anyone still out there.
     */
    private void recomputeStandings() {
        standings.clear();
        int place = 1;
        for (RaceParticipantState finisher : finishOrder) {
            standings.put(finisher.playerId(), place++);
        }
        List<RaceParticipantState> running = new ArrayList<>(activeParticipants());
        running.sort(Comparator
                .comparingInt(RaceParticipantState::lapsCompleted).reversed()
                .thenComparing(Comparator.comparingDouble(RaceParticipantState::lastProgress).reversed()));
        for (RaceParticipantState participant : running) {
            standings.put(participant.playerId(), place++);
        }
        for (RaceParticipantState participant : participants.values()) {
            standings.putIfAbsent(participant.playerId(), place++);
        }
    }

    private RaceResult buildResult() {
        List<RaceResult.Placement> placements = new ArrayList<>();
        int place = 1;
        for (RaceParticipantState finisher : finishOrder) {
            placements.add(new RaceResult.Placement(
                    finisher.playerId(), finisher.playerName(), place++,
                    RaceSettings.ticksToMillis(finisher.finishTick() - startTick),
                    // A finisher can still have no lap time: an elimination survivor is credited
                    // with winning without having completed one.
                    finisher.bestLapTicks() < 0 ? -1 : RaceSettings.ticksToMillis(finisher.bestLapTicks()),
                    finisher.lapsCompleted(),
                    false));
        }
        // Everyone who did not finish, ordered by how far they got.
        List<RaceParticipantState> unfinished = participants.values().stream()
                .filter(participant -> !participant.hasFinished())
                .sorted(Comparator
                        .comparingInt(RaceParticipantState::lapsCompleted).reversed()
                        .thenComparing(Comparator.comparingDouble(RaceParticipantState::lastProgress).reversed()))
                .toList();
        for (RaceParticipantState participant : unfinished) {
            placements.add(new RaceResult.Placement(
                    participant.playerId(), participant.playerName(), place++,
                    -1,
                    participant.bestLapTicks() < 0 ? -1 : RaceSettings.ticksToMillis(participant.bestLapTicks()),
                    participant.lapsCompleted(),
                    true));
        }
        return new RaceResult(trackName, mode.id(), laps, placements);
    }

    private RaceHudState hudFor(RaceParticipantState participant, long tick, int countdown) {
        return new RaceHudState(
                phase,
                countdown,
                standings.getOrDefault(participant.playerId(), 0),
                participants.size(),
                Math.min(participant.currentLap(), laps),
                laps,
                phase == Phase.RUNNING ? RaceSettings.ticksToMillis(tick - startTick) : 0,
                participant.bestLapTicks() < 0 ? -1 : RaceSettings.ticksToMillis(participant.bestLapTicks()),
                participant.wrongWayTicks() >= settings.wrongWayGraceTicks()
                        && settings.wrongWayGraceTicks() > 0);
    }

    private void broadcast(String message) {
        callbacks.broadcast(message);
    }

    private static String formatTime(long millis) {
        long minutes = millis / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long hundredths = (millis % 1000) / 10;
        return String.format("%d:%02d.%02d", minutes, seconds, hundredths);
    }

    // --- queries ----------------------------------------------------------------------------

    public Phase phase() {
        return phase;
    }

    public String trackName() {
        return trackName;
    }

    public KartTrack track() {
        return track;
    }

    public int laps() {
        return laps;
    }

    public RaceMode mode() {
        return mode;
    }

    public boolean isOver() {
        return phase == Phase.FINISHED || phase == Phase.CANCELLED;
    }

    public boolean contains(UUID player) {
        return participants.containsKey(player);
    }

    public RaceParticipantState participant(UUID player) {
        return participants.get(player);
    }

    public List<RaceParticipantState> participants() {
        return List.copyOf(participants.values());
    }

    /** Everyone still driving — not finished, retired or eliminated. */
    public List<RaceParticipantState> activeParticipants() {
        return participants.values().stream().filter(RaceParticipantState::isActive).toList();
    }

    public List<RaceParticipantState> finishOrder() {
        return List.copyOf(finishOrder);
    }

    /** Current standing for a racer, 1-based, or 0 if unknown. */
    public int positionOf(UUID player) {
        return standings.getOrDefault(player, 0);
    }

    /** The racer currently last among those still driving, or null. Used by elimination. */
    public RaceParticipantState lastPlaceActive() {
        return activeParticipants().stream()
                .min(Comparator
                        .comparingInt(RaceParticipantState::lapsCompleted)
                        .thenComparing(Comparator.comparingDouble(RaceParticipantState::lastProgress)))
                .orElse(null);
    }
}
