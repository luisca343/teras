package es.boffmedia.teras.karts.mode;

import es.boffmedia.teras.karts.engine.RaceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Championship scoring. The rules that matter are who scores (only finishers), how ties break
 * (wins), and that a series can be restored mid-championship after a restart.
 */
class GrandPrixSeriesTest {

    private static final UUID ANA = UUID.nameUUIDFromBytes("ana".getBytes());
    private static final UUID BEA = UUID.nameUUIDFromBytes("bea".getBytes());
    private static final UUID CARLOS = UUID.nameUUIDFromBytes("carlos".getBytes());

    private static final int[] POINTS = {10, 8, 6, 4, 2, 1};

    private static GrandPrixSeries series() {
        return new GrandPrixSeries("copa", List.of("playa", "montana", "ciudad"), 3, POINTS);
    }

    /** A round where the given players finished in the order listed. */
    private static RaceResult round(String track, UUID... finishOrder) {
        List<RaceResult.Placement> placements = new java.util.ArrayList<>();
        for (int i = 0; i < finishOrder.length; i++) {
            placements.add(new RaceResult.Placement(
                    finishOrder[i], nameOf(finishOrder[i]), i + 1, 60_000L + i, 20_000L, 3, false));
        }
        return new RaceResult(track, ClassicMode.ID, 3, placements);
    }

    private static String nameOf(UUID player) {
        if (player.equals(ANA)) return "Ana";
        if (player.equals(BEA)) return "Bea";
        return "Carlos";
    }

    @Test
    @DisplayName("points are awarded down the finishing order")
    void awardsPointsByPosition() {
        GrandPrixSeries series = series();
        series.start();
        series.recordRound(round("playa", ANA, BEA, CARLOS));

        assertEquals(10, series.pointsFor(ANA));
        assertEquals(8, series.pointsFor(BEA));
        assertEquals(6, series.pointsFor(CARLOS));
    }

    @Test
    @DisplayName("points accumulate across rounds")
    void pointsAccumulate() {
        GrandPrixSeries series = series();
        series.start();
        series.recordRound(round("playa", ANA, BEA));
        series.recordRound(round("montana", BEA, ANA));

        assertEquals(18, series.pointsFor(ANA));
        assertEquals(18, series.pointsFor(BEA));
    }

    @Test
    @DisplayName("a retirement scores nothing")
    void retirementScoresNothing() {
        GrandPrixSeries series = series();
        series.start();
        series.recordRound(new RaceResult("playa", ClassicMode.ID, 3, List.of(
                new RaceResult.Placement(ANA, "Ana", 1, 60_000L, 20_000L, 3, false),
                new RaceResult.Placement(BEA, "Bea", 2, -1, -1, 1, true))));

        assertEquals(10, series.pointsFor(ANA));
        assertEquals(0, series.pointsFor(BEA));
    }

    @Test
    @DisplayName("finishing outside the points table scores nothing but is still recorded")
    void finishingOutsideThePointsTableScoresNothing() {
        GrandPrixSeries series = new GrandPrixSeries("mini", List.of("playa"), 3, new int[]{10, 8});
        series.start();
        series.recordRound(round("playa", ANA, BEA, CARLOS));

        assertEquals(0, series.pointsFor(CARLOS));
        assertEquals(3, series.standings().size(), "everyone who finished appears in the table");
    }

    @Test
    @DisplayName("a tie on points is broken by race wins")
    void tiesBreakOnWins() {
        GrandPrixSeries series = series();
        series.start();
        // Ana wins one and comes second in another; Bea does the reverse. Both on 18.
        series.recordRound(round("playa", ANA, BEA));
        series.recordRound(round("montana", BEA, ANA));
        series.recordRound(round("ciudad", ANA, BEA));

        List<GrandPrixSeries.Standing> table = series.standings();
        assertEquals(ANA, table.get(0).playerId());
        assertEquals(2, table.get(0).wins());
        assertEquals(1, table.get(0).position());
        assertEquals(BEA, table.get(1).playerId());
    }

    @Test
    @DisplayName("the series advances a round at a time and finishes after the last circuit")
    void advancesThroughRounds() {
        GrandPrixSeries series = series();
        series.start();
        assertEquals("playa", series.currentTrack());

        series.recordRound(round("playa", ANA, BEA));
        assertEquals("montana", series.currentTrack());
        assertEquals(1, series.currentRound());
        assertTrue(series.hasNextRound());

        series.recordRound(round("montana", ANA, BEA));
        series.recordRound(round("ciudad", ANA, BEA));

        assertFalse(series.hasNextRound());
        assertNull(series.currentTrack());
        assertEquals(GrandPrixSeries.Status.FINISHED, series.status());
    }

    @Test
    @DisplayName("the leader is the champion once the series is over")
    void leaderIsTheChampion() {
        GrandPrixSeries series = series();
        series.start();
        series.recordRound(round("playa", BEA, ANA));
        series.recordRound(round("montana", BEA, ANA));
        series.recordRound(round("ciudad", ANA, BEA));

        assertEquals(BEA, series.leader().playerId());
        assertEquals(28, series.leader().points());
    }

    @Test
    @DisplayName("a series with no rounds run yet has no leader")
    void noLeaderBeforeAnyRound() {
        assertNull(series().leader());
    }

    @Test
    @DisplayName("a half-finished series can be restored after a restart")
    void restoresMidSeries() {
        GrandPrixSeries original = series();
        original.start();
        original.recordRound(round("playa", ANA, BEA));

        GrandPrixSeries restored = series();
        restored.restore(original.currentRound(), original.status(),
                original.pointsSnapshot(), original.namesSnapshot(), original.winsSnapshot());

        assertEquals("montana", restored.currentTrack());
        assertEquals(10, restored.pointsFor(ANA));
        assertEquals(1, restored.winsFor(ANA));
        assertEquals("Ana", restored.leader().playerName());
    }

    @Test
    @DisplayName("an empty points table falls back to a default rather than scoring nothing")
    void emptyPointsTableFallsBack() {
        GrandPrixSeries series = new GrandPrixSeries("copa", List.of("playa"), 3, new int[0]);
        series.start();
        series.recordRound(round("playa", ANA, BEA));

        assertTrue(series.pointsFor(ANA) > 0);
    }
}
