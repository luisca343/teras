package es.boffmedia.teras.karts.mode;

import es.boffmedia.teras.karts.engine.RaceCore;
import es.boffmedia.teras.karts.engine.RaceResult;
import es.boffmedia.teras.karts.engine.RaceSettings;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackCheckpoint;
import es.boffmedia.teras.karts.model.TrackPoint;
import es.boffmedia.teras.karts.engine.RecordingCallbacks;
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
 * Elimination: last place is cut when the leader completes a lap, and the survivor wins.
 */
class EliminationModeTest {

    private static final UUID ANA = UUID.nameUUIDFromBytes("ana".getBytes());
    private static final UUID BEA = UUID.nameUUIDFromBytes("bea".getBytes());
    private static final UUID CARLOS = UUID.nameUUIDFromBytes("carlos".getBytes());

    private RecordingCallbacks callbacks;
    private RaceCore race;
    private long tick;

    @BeforeEach
    void setUp() {
        callbacks = new RecordingCallbacks();
        tick = 0;

        KartTrack track = new KartTrack("circuito_test");
        track.setDimension("minecraft:overworld");
        track.addStartingPoint(new TrackPoint(0, 64, 0, 0f));
        track.addStartingPoint(new TrackPoint(0, 64, 4, 0f));
        track.addStartingPoint(new TrackPoint(0, 64, 8, 0f));
        track.addCheckpoint(gateAtX(10));
        track.addCheckpoint(gateAtX(20));
        track.addCheckpoint(gateAtX(30));

        race = new RaceCore(track, 10, RaceSettings.defaults(), new EliminationMode(), callbacks);
        race.join(ANA, "Ana");
        race.join(BEA, "Bea");
        race.join(CARLOS, "Carlos");
        race.beginCountdown(++tick);

        Map<UUID, TrackPoint> grid = new HashMap<>();
        grid.put(ANA, TrackPoint.at(0, 64, 0));
        grid.put(BEA, TrackPoint.at(0, 64, 4));
        grid.put(CARLOS, TrackPoint.at(0, 64, 8));
        for (int i = 0; i <= RaceSettings.defaults().countdownTicks(); i++) {
            race.tick(++tick, grid);
        }
        assertEquals(RaceCore.Phase.RUNNING, race.phase());
    }

    private static TrackCheckpoint gateAtX(double x) {
        return new TrackCheckpoint(TrackPoint.at(x, 60, -20), TrackPoint.at(x + 1, 70, 20));
    }

    /** Spreads the field so standings are well defined, then settles it. */
    private void spreadField(double anaX, double beaX, double carlosX) {
        Map<UUID, TrackPoint> positions = new HashMap<>();
        positions.put(ANA, TrackPoint.at(anaX, 64, 0));
        positions.put(BEA, TrackPoint.at(beaX, 64, 4));
        positions.put(CARLOS, TrackPoint.at(carlosX, 64, 8));
        for (int i = 0; i <= RaceSettings.defaults().rankingIntervalTicks(); i++) {
            race.tick(++tick, positions);
        }
    }

    /** Walks one racer through every gate so they complete a lap. */
    private void completeLap(UUID player, double laneZ) {
        for (double x : new double[]{10.5, 20.5, 30.5}) {
            race.tick(++tick, Map.of(player, TrackPoint.at(x, 64, laneZ)));
        }
    }

    @Test
    @DisplayName("the leader completing a lap eliminates whoever is last")
    void leaderLapEliminatesLastPlace() {
        // Ana ahead, Bea in the middle, Carlos trailing.
        spreadField(25, 15, 2);
        assertEquals(CARLOS, race.lastPlaceActive().playerId());

        completeLap(ANA, 0);

        assertTrue(race.participant(CARLOS).eliminated(), "the trailing racer is cut");
        assertFalse(race.participant(BEA).eliminated());
        assertTrue(callbacks.removedKarts.contains(CARLOS));
        assertTrue(callbacks.sawTitleContaining(CARLOS, "ELIMINADO"));
    }

    @Test
    @DisplayName("a lap completed by someone other than the leader eliminates nobody")
    void nonLeaderLapEliminatesNobody() {
        spreadField(25, 15, 2);
        // Carlos is last; his own lap must not trigger a cut.
        completeLap(CARLOS, 8);

        assertFalse(race.participant(CARLOS).eliminated());
        assertFalse(race.participant(BEA).eliminated());
    }

    @Test
    @DisplayName("the last racer standing wins, without having to finish the remaining laps")
    void survivorWins() {
        spreadField(25, 15, 2);
        completeLap(ANA, 0);
        assertTrue(race.participant(CARLOS).eliminated());

        spreadField(45, 20, 0);
        completeLap(ANA, 0);

        assertEquals(RaceCore.Phase.FINISHED, race.phase());
        RaceResult result = callbacks.result;
        assertNotNull(result);
        assertEquals("Ana", result.winner().playerName(), "the survivor is the winner");
        assertFalse(result.placements().get(0).dnf(),
                "the survivor never crossed the line but must not be recorded as a retirement");
    }

    @Test
    @DisplayName("a survivor who never completed a lap reports no best lap, not a negative one")
    void survivorWithoutALapHasNoBestLap() {
        // Cut the field without anyone finishing a lap, so the survivor is credited with winning
        // having never crossed the line.
        spreadField(25, 15, 2);
        completeLap(ANA, 0);
        spreadField(45, 20, 0);
        completeLap(ANA, 0);

        RaceResult result = callbacks.result;
        assertNotNull(result);
        for (RaceResult.Placement placement : result.placements()) {
            assertTrue(placement.bestLapMs() == -1 || placement.bestLapMs() > 0,
                    placement.playerName() + " reported a nonsensical best lap of "
                            + placement.bestLapMs());
        }
    }

    @Test
    @DisplayName("the result records laps actually completed, not the race's lap count")
    void resultRecordsLapsActuallyCompleted() {
        spreadField(25, 15, 2);
        completeLap(ANA, 0);
        spreadField(45, 20, 0);
        completeLap(ANA, 0);

        RaceResult result = callbacks.result;
        assertNotNull(result);
        // The race was set to ten laps; nobody drove ten. A leaderboard must be able to tell.
        assertEquals(10, result.laps());
        for (RaceResult.Placement placement : result.placements()) {
            assertTrue(placement.lapsCompleted() < result.laps(),
                    "nobody covered the full distance in this elimination race");
        }
    }

    @Test
    @DisplayName("the race is not over before it has started")
    void notFinishedBeforeStart() {
        RaceCore fresh = new RaceCore(new KartTrack("x"), 3, RaceSettings.defaults(),
                new EliminationMode(), new RecordingCallbacks());
        assertFalse(new EliminationMode().isFinished(fresh),
                "an empty lobby has no active racers, but the race has not started");
    }
}
