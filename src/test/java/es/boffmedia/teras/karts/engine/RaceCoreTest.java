package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackCheckpoint;
import es.boffmedia.teras.karts.model.TrackPoint;
import es.boffmedia.teras.karts.mode.ClassicMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The race state machine, driven tick by tick with scripted kart positions.
 *
 * <p>The test circuit is three gates in a line at x=10, 20 and 30, with the grid back at x=0.
 * Driving in +x crosses them in order and completes a lap; positions are supplied directly, so a
 * test can also teleport a kart to set up a case that would be hard to drive.</p>
 */
class RaceCoreTest {

    private static final UUID ANA = UUID.nameUUIDFromBytes("ana".getBytes());
    private static final UUID BEA = UUID.nameUUIDFromBytes("bea".getBytes());
    private static final UUID CARLOS = UUID.nameUUIDFromBytes("carlos".getBytes());

    private RecordingCallbacks callbacks;
    private long tick;

    @BeforeEach
    void setUp() {
        callbacks = new RecordingCallbacks();
        tick = 0;
    }

    private static KartTrack testTrack() {
        KartTrack track = new KartTrack("circuito_test");
        track.setDimension("minecraft:overworld");
        track.addStartingPoint(new TrackPoint(0, 64, 0, 0f));
        track.addStartingPoint(new TrackPoint(0, 64, 4, 0f));
        track.addStartingPoint(new TrackPoint(0, 64, 8, 0f));
        track.addCheckpoint(gateAtX(10));
        track.addCheckpoint(gateAtX(20));
        track.addCheckpoint(gateAtX(30));
        return track;
    }

    private static TrackCheckpoint gateAtX(double x) {
        return new TrackCheckpoint(TrackPoint.at(x, 60, -20), TrackPoint.at(x + 1, 70, 20));
    }

    private RaceCore race(int laps, RaceSettings settings) {
        return new RaceCore(testTrack(), laps, settings, new ClassicMode(), callbacks);
    }

    private RaceCore race(int laps) {
        return race(laps, RaceSettings.defaults());
    }

    /** Advances one tick with every named racer parked at the same spot. */
    private void tickAll(RaceCore race, Map<UUID, TrackPoint> positions) {
        race.tick(++tick, positions);
    }

    private void tickTimes(RaceCore race, int times, Map<UUID, TrackPoint> positions) {
        for (int i = 0; i < times; i++) {
            tickAll(race, positions);
        }
    }

    /** Joins, votes, and runs the countdown out so the race is RUNNING. */
    private RaceCore startedRace(int laps, UUID... players) {
        RaceCore race = race(laps);
        for (UUID player : players) {
            race.join(player, nameOf(player));
            race.vote(player, true);
        }
        race.beginCountdown(++tick);
        Map<UUID, TrackPoint> grid = new HashMap<>();
        for (UUID player : players) {
            grid.put(player, TrackPoint.at(0, 64, 0));
        }
        tickTimes(race, RaceSettings.defaults().countdownTicks() + 1, grid);
        assertEquals(RaceCore.Phase.RUNNING, race.phase());
        return race;
    }

    private static String nameOf(UUID player) {
        if (player.equals(ANA)) return "Ana";
        if (player.equals(BEA)) return "Bea";
        return "Carlos";
    }

    /** Drives one racer through every gate, completing a lap, then back to the grid. */
    private void driveLap(RaceCore race, UUID player) {
        for (double x : new double[]{10.5, 20.5, 30.5}) {
            tickAll(race, Map.of(player, TrackPoint.at(x, 64, 0)));
        }
        tickAll(race, Map.of(player, TrackPoint.at(0, 64, 0)));
    }

    // --- lobby and voting -------------------------------------------------------------------

    @Test
    @DisplayName("a lone player cannot vote a race into starting")
    void singlePlayerCannotSelfStart() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        race.vote(ANA, true);

        // 1.16.5 started here: one vote is "more than half" of one entrant.
        assertFalse(race.shouldStart());
        assertEquals(RaceCore.Phase.LOBBY, race.phase());
    }

    @Test
    @DisplayName("the race starts once enough entrants have voted")
    void startsWhenVoteCarries() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.vote(ANA, true);
        assertFalse(race.shouldStart(), "one of two is below the 60% threshold");

        race.vote(BEA, true);
        assertTrue(race.shouldStart());
    }

    @Test
    @DisplayName("a vote can be withdrawn")
    void voteCanBeWithdrawn() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.vote(ANA, true);
        race.vote(BEA, true);
        assertTrue(race.shouldStart());

        race.vote(BEA, false);
        assertFalse(race.shouldStart());
        assertEquals(1, race.votes());
    }

    @Test
    @DisplayName("entrants are capped by the number of grid slots")
    void gridSizeCapsEntrants() {
        RaceCore race = race(1);
        assertTrue(race.join(ANA, "Ana"));
        assertTrue(race.join(BEA, "Bea"));
        assertTrue(race.join(CARLOS, "Carlos"));
        // The test circuit has three slots.
        assertFalse(race.join(UUID.randomUUID(), "Cuarto"));
    }

    @Test
    @DisplayName("joining twice is refused")
    void cannotJoinTwice() {
        RaceCore race = race(1);
        assertTrue(race.join(ANA, "Ana"));
        assertFalse(race.join(ANA, "Ana"));
    }

    @Test
    @DisplayName("leaving the lobby just removes the entrant")
    void leavingLobbyRemovesEntrant() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        assertTrue(race.leave(ANA));
        assertFalse(race.contains(ANA));
    }

    // --- countdown --------------------------------------------------------------------------

    @Test
    @DisplayName("the countdown puts every racer on a grid slot")
    void countdownPlacesEveryoneOnTheGrid() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.beginCountdown(++tick);

        assertEquals(RaceCore.Phase.COUNTDOWN, race.phase());
        assertEquals(2, callbacks.gridPlacements.size());
        // Different racers get different slots.
        assertEquals(2, callbacks.gridPlacements.values().stream().distinct().count());
    }

    @Test
    @DisplayName("the countdown shows 3, 2, 1 and then starts")
    void countdownCountsDownThenStarts() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.beginCountdown(++tick);

        Map<UUID, TrackPoint> grid = Map.of(ANA, TrackPoint.at(0, 64, 0), BEA, TrackPoint.at(0, 64, 4));
        tickTimes(race, RaceSettings.defaults().countdownTicks() + 1, grid);

        assertTrue(callbacks.titlesFor(ANA).containsAll(java.util.List.of("3", "2", "1")),
                "expected three pips, got " + callbacks.titlesFor(ANA));
        assertTrue(callbacks.titlesFor(ANA).contains("¡YA!"));
        assertEquals(RaceCore.Phase.RUNNING, race.phase());
        assertEquals(1, callbacks.releaseCount, "karts are released exactly once");
    }

    @Test
    @DisplayName("karts stay held until the countdown ends")
    void kartsStayHeldDuringCountdown() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.beginCountdown(++tick);

        tickTimes(race, 10, Map.of(ANA, TrackPoint.at(0, 64, 0), BEA, TrackPoint.at(0, 64, 4)));
        assertEquals(0, callbacks.releaseCount);
        assertEquals(RaceCore.Phase.COUNTDOWN, race.phase());
    }

    @Test
    @DisplayName("a forced start skips the rest of the countdown")
    void forceStartSkipsCountdown() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.beginCountdown(++tick);
        race.forceStart(tick);

        tickAll(race, Map.of(ANA, TrackPoint.at(0, 64, 0), BEA, TrackPoint.at(0, 64, 4)));
        assertEquals(RaceCore.Phase.RUNNING, race.phase());
    }

    // --- laps and checkpoints ---------------------------------------------------------------

    @Test
    @DisplayName("crossing every gate in order completes a lap")
    void completesLapThroughGatesInOrder() {
        RaceCore race = startedRace(2, ANA, BEA);
        driveLap(race, ANA);

        assertEquals(1, race.participant(ANA).lapsCompleted());
        assertTrue(callbacks.sawMessageContaining(ANA, "Vuelta 1/2"));
    }

    @Test
    @DisplayName("gates crossed out of order do not count")
    void ignoresGatesOutOfOrder() {
        RaceCore race = startedRace(2, ANA, BEA);

        // Loop around the outside of the gates (the circuit spans z ∈ [-21, 21]) and come at the
        // last one from behind, so it is reached without the first two ever being crossed.
        tickAll(race, Map.of(ANA, TrackPoint.at(0, 64, -50)));
        tickAll(race, Map.of(ANA, TrackPoint.at(30.5, 64, -50)));
        assertEquals(0, race.participant(ANA).checkpointIndex(), "nothing crossed on the way round");

        tickAll(race, Map.of(ANA, TrackPoint.at(30.5, 64, 0)));

        assertEquals(0, race.participant(ANA).lapsCompleted());
        assertEquals(0, race.participant(ANA).checkpointIndex(),
                "the last gate does not count while the first is still pending");
    }

    @Test
    @DisplayName("only one gate is credited per tick, so a teleport cannot skip a lap")
    void creditsAtMostOneGatePerTick() {
        RaceCore race = startedRace(2, ANA, BEA);

        // A single step spanning all three gates. Crossing them counts as reaching the first: a
        // racer flung across the circuit by a glitch or a hostile teleport gains one gate, not a lap.
        tickAll(race, Map.of(ANA, TrackPoint.at(40, 64, 0)));

        assertEquals(1, race.participant(ANA).checkpointIndex());
        assertEquals(0, race.participant(ANA).lapsCompleted());
    }

    @Test
    @DisplayName("a kart fast enough to jump a whole gate in one tick still counts it")
    void countsGateDespiteTunneling() {
        RaceCore race = startedRace(1, ANA, BEA);

        // From well before the first gate to well past it in a single tick: with the 1.16.5
        // containment test this racer would have been stuck on checkpoint 0 forever.
        tickAll(race, Map.of(ANA, TrackPoint.at(0, 64, 0)));
        tickAll(race, Map.of(ANA, TrackPoint.at(15, 64, 0)));

        assertEquals(1, race.participant(ANA).checkpointIndex(), "the first gate was crossed");
    }

    @Test
    @DisplayName("finishing the set laps ends that racer's race and removes their kart")
    void finishingRemovesTheKart() {
        RaceCore race = startedRace(1, ANA, BEA);
        driveLap(race, ANA);

        assertTrue(race.participant(ANA).hasFinished());
        assertTrue(callbacks.removedKarts.contains(ANA));
        assertTrue(callbacks.sawTitleContaining(ANA, "VICTORIA"));
    }

    @Test
    @DisplayName("two laps take two trips round")
    void needsEveryLap() {
        RaceCore race = startedRace(2, ANA, BEA);

        driveLap(race, ANA);
        assertFalse(race.participant(ANA).hasFinished(), "one lap of two is not a finish");

        driveLap(race, ANA);
        assertTrue(race.participant(ANA).hasFinished());
    }

    @Test
    @DisplayName("finishing order decides placings")
    void finishOrderDecidesPlacings() {
        RaceCore race = startedRace(1, ANA, BEA);

        driveLap(race, BEA);
        driveLap(race, ANA);

        assertEquals(1, race.participant(BEA).finishPlace());
        assertEquals(2, race.participant(ANA).finishPlace());
        assertTrue(callbacks.sawTitleContaining(BEA, "VICTORIA"));
        assertTrue(callbacks.sawTitleContaining(ANA, "CARRERA TERMINADA"));
    }

    @Test
    @DisplayName("the race ends and reports a result once nobody is still driving")
    void endsWhenEveryoneIsDone() {
        RaceCore race = startedRace(1, ANA, BEA);
        driveLap(race, BEA);
        driveLap(race, ANA);

        assertEquals(RaceCore.Phase.FINISHED, race.phase());
        assertNotNull(callbacks.result);
        assertEquals(2, callbacks.result.placements().size());
        assertEquals("Bea", callbacks.result.winner().playerName());
        assertEquals(1, callbacks.result.placements().get(0).position());
        assertFalse(callbacks.result.placements().get(0).dnf());
    }

    // --- retirement, timeout, dismount ------------------------------------------------------

    @Test
    @DisplayName("a racer who leaves mid-race is retired and loses their kart")
    void leavingMidRaceRetires() {
        RaceCore race = startedRace(3, ANA, BEA);
        race.leave(ANA);

        assertTrue(race.participant(ANA).dnf());
        assertTrue(callbacks.removedKarts.contains(ANA));
    }

    @Test
    @DisplayName("a racer out of their kart is put back before being retired")
    void reseatsBeforeRetiring() {
        RaceCore race = startedRace(3, ANA, BEA);

        // Ana's position stops being reported: she is no longer in a kart.
        tickAll(race, Map.of(BEA, TrackPoint.at(1, 64, 4)));

        assertTrue(callbacks.reseatAttempts.contains(ANA));
        assertFalse(race.participant(ANA).dnf(), "one tick out is not a retirement");
    }

    @Test
    @DisplayName("staying out of the kart past the grace period is a retirement")
    void retiresAfterDismountGrace() {
        RaceCore race = startedRace(3, ANA, BEA);
        int grace = RaceSettings.defaults().dismountGraceTicks();

        tickTimes(race, grace + 1, Map.of(BEA, TrackPoint.at(1, 64, 4)));

        assertTrue(race.participant(ANA).dnf());
        assertTrue(callbacks.sawMessageContaining(ANA, "fuera del kart"));
    }

    @Test
    @DisplayName("reseating is retried while a racer is out, not attempted once and given up on")
    void retriesReseatWhileMissing() {
        RaceCore race = startedRace(3, ANA, BEA);
        callbacks.reseatSucceeds = false;

        // Ana stays out of her kart for a while, but not past the grace period.
        tickTimes(race, 41, Map.of(BEA, TrackPoint.at(1, 64, 4)));

        assertTrue(callbacks.reseatAttempts.size() > 1,
                "expected repeated attempts, saw " + callbacks.reseatAttempts.size());
        assertFalse(race.participant(ANA).dnf(), "still inside the grace window");
    }

    @Test
    @DisplayName("a reseat that claims success does not extend the grace period indefinitely")
    void reseatSuccessDoesNotResetTheGraceWindow() {
        RaceCore race = startedRace(3, ANA, BEA);
        callbacks.reseatSucceeds = true;
        int grace = RaceSettings.defaults().dismountGraceTicks();

        // The callback reports success every time, but Ana's kart never reports a position again.
        // Only a real position clears the counter, so she must still time out.
        tickTimes(race, grace + 1, Map.of(BEA, TrackPoint.at(1, 64, 4)));

        assertTrue(race.participant(ANA).dnf());
    }

    @Test
    @DisplayName("a racer who gets back in their kart is not retired")
    void returningToTheKartClearsTheGrace() {
        RaceCore race = startedRace(3, ANA, BEA);
        int grace = RaceSettings.defaults().dismountGraceTicks();

        tickTimes(race, grace - 10, Map.of(BEA, TrackPoint.at(1, 64, 4)));
        // Ana's kart reports a position again: she is back aboard.
        tickAll(race, Map.of(ANA, TrackPoint.at(2, 64, 0), BEA, TrackPoint.at(1, 64, 4)));
        tickTimes(race, grace - 10, Map.of(ANA, TrackPoint.at(3, 64, 0), BEA, TrackPoint.at(1, 64, 4)));

        assertFalse(race.participant(ANA).dnf(), "the grace window restarts once she is back");
    }

    @Test
    @DisplayName("someone who leaves during the countdown is not told the race started")
    void leavingDuringCountdownStopsTitles() {
        RaceCore race = race(1);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.beginCountdown(++tick);

        race.leave(ANA);
        int titlesAtLeave = callbacks.titlesFor(ANA).size();

        Map<UUID, TrackPoint> grid = Map.of(BEA, TrackPoint.at(0, 64, 4));
        tickTimes(race, RaceSettings.defaults().countdownTicks() + 1, grid);

        assertEquals(titlesAtLeave, callbacks.titlesFor(ANA).size(),
                "a racer who walked out should get no further countdown or GO titles");
        assertTrue(callbacks.titlesFor(BEA).contains("¡YA!"), "the remaining racer still starts");
    }

    @Test
    @DisplayName("a race that runs past its time limit retires whoever is left")
    void timeoutRetiresStragglers() {
        RaceSettings quickTimeout = new RaceSettings(2, 60, 60, 40, 10, 20, 100, 60);
        RaceCore race = race(3, quickTimeout);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.beginCountdown(++tick);

        Map<UUID, TrackPoint> parked = Map.of(ANA, TrackPoint.at(0, 64, 0), BEA, TrackPoint.at(0, 64, 4));
        tickTimes(race, quickTimeout.countdownTicks() + 1, parked);
        assertEquals(RaceCore.Phase.RUNNING, race.phase());

        // 1.16.5 had this limit too, but only ever printed it in a status message.
        tickTimes(race, quickTimeout.timeoutTicks() + 2, parked);

        assertEquals(RaceCore.Phase.FINISHED, race.phase());
        assertTrue(race.participant(ANA).dnf());
        assertTrue(race.participant(BEA).dnf());
        assertNotNull(callbacks.result);
        assertEquals(0, callbacks.result.finisherCount());
    }

    @Test
    @DisplayName("retired racers appear in the result marked as such, behind the finishers")
    void resultsIncludeRetirements() {
        RaceCore race = startedRace(1, ANA, BEA);
        driveLap(race, ANA);
        race.leave(BEA);
        tickAll(race, Map.of());

        RaceResult result = callbacks.result;
        assertNotNull(result);
        assertEquals(2, result.placements().size());
        assertEquals("Ana", result.placements().get(0).playerName());
        assertFalse(result.placements().get(0).dnf());
        assertTrue(result.placements().get(1).dnf());
        assertEquals(-1, result.placements().get(1).timeMs(), "a retirement has no time");
    }

    // --- standings --------------------------------------------------------------------------

    @Test
    @DisplayName("more laps beats further round the same lap")
    void standingsRankLapsFirst() {
        RaceCore race = startedRace(3, ANA, BEA);

        driveLap(race, ANA);
        // Bea gets a long way round her first lap, but it is still her first.
        tickAll(race, Map.of(BEA, TrackPoint.at(10.5, 64, 4)));
        tickAll(race, Map.of(BEA, TrackPoint.at(20.5, 64, 4)));
        tickTimes(race, RaceSettings.defaults().rankingIntervalTicks() + 1,
                Map.of(ANA, TrackPoint.at(1, 64, 0), BEA, TrackPoint.at(25, 64, 4)));

        assertTrue(race.positionOf(ANA) < race.positionOf(BEA),
                "Ana is a lap up, so she leads");
    }

    @Test
    @DisplayName("a finisher outranks anyone still driving")
    void finishersOutrankRunners() {
        RaceCore race = startedRace(2, ANA, BEA);
        driveLap(race, ANA);
        driveLap(race, ANA);

        tickTimes(race, RaceSettings.defaults().rankingIntervalTicks() + 1,
                Map.of(BEA, TrackPoint.at(15, 64, 4)));

        assertEquals(1, race.positionOf(ANA));
        assertTrue(race.positionOf(BEA) > 1);
    }

    @Test
    @DisplayName("last place among those still driving is identified for elimination")
    void identifiesLastPlace() {
        RaceCore race = startedRace(3, ANA, BEA);

        tickAll(race, Map.of(ANA, TrackPoint.at(25, 64, 0), BEA, TrackPoint.at(5, 64, 4)));
        tickTimes(race, RaceSettings.defaults().rankingIntervalTicks() + 1,
                Map.of(ANA, TrackPoint.at(25, 64, 0), BEA, TrackPoint.at(5, 64, 4)));

        RaceParticipantState last = race.lastPlaceActive();
        assertNotNull(last);
        assertEquals(BEA, last.playerId());
    }

    // --- cancelling -------------------------------------------------------------------------

    @Test
    @DisplayName("cancelling clears every kart and tells everyone why")
    void cancellingCleansUp() {
        RaceCore race = startedRace(3, ANA, BEA);
        race.cancel("La carrera ha sido cancelada por un administrador.");

        assertEquals(RaceCore.Phase.CANCELLED, race.phase());
        assertTrue(race.isOver());
        assertTrue(callbacks.removedKarts.contains(ANA));
        assertTrue(callbacks.removedKarts.contains(BEA));
        assertTrue(callbacks.sawMessageContaining(ANA, "cancelada"));
    }

    @Test
    @DisplayName("a cancelled race stops advancing")
    void cancelledRaceIgnoresTicks() {
        RaceCore race = startedRace(3, ANA, BEA);
        race.cancel(null);
        int lapsBefore = race.participant(ANA).lapsCompleted();

        driveLap(race, ANA);

        assertEquals(lapsBefore, race.participant(ANA).lapsCompleted());
    }
}
