package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.SeededRng;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cell budget is an authored table plus jitter plus curses, so the reachable values per floor
 * are small closed sets — these pin them so a refactor cannot quietly change floor sizes.
 *
 * <p>Indexed by canonical floor, never by a run's stage: that is what makes floor ten the same size
 * in a twelve-floor descent and in a one-floor challenge that opens on it.</p>
 */
class RoomTargetsTest {

    private final GenConfig config = GenConfig.defaults();

    private void assertTargetAlwaysIn(int floor, Set<Curse> curses, Set<Integer> expected) {
        for (long seed = 0; seed < 200; seed++) {
            int target = DungeonGenerator.targetCells(
                    config, FloorDepth.of(config, floor), curses, new SeededRng(seed));
            assertTrue(expected.contains(target),
                    "floor " + floor + " curses " + curses + " produced " + target);
        }
    }

    /** Floor one is Basement one: the smallest floor in the game, in every dungeon that has one. */
    @Test
    void floorOneIsSmall() {
        assertTargetAlwaysIn(1, Set.of(), Set.of(10, 11, 12));
    }

    @Test
    void curseOfTheLostAddsFourCells() {
        assertTargetAlwaysIn(1, Set.of(Curse.LOST), Set.of(14, 15, 16));
    }

    @Test
    void theCurveClimbsFloorByFloor() {
        assertTargetAlwaysIn(2, Set.of(), Set.of(13, 14, 15));
        assertTargetAlwaysIn(3, Set.of(), Set.of(17, 18, 19));
        assertTargetAlwaysIn(6, Set.of(), Set.of(22, 23, 24));
    }

    /**
     * Where the table earns its keep: the old {@code min(20, …)} saturated at floor five, so ten
     * and five were identical. They must not be.
     */
    @Test
    void deepFloorsAreDeeperThanMidFloors() {
        assertTargetAlwaysIn(10, Set.of(), Set.of(30, 31, 32));
        assertTrue(config.cellsFor(10) > config.cellsFor(5));
    }

    @Test
    void labyrinthNearlyDoublesTheFloor() {
        assertTargetAlwaysIn(6, Set.of(Curse.LABYRINTH), Set.of(39, 41, 43));
    }

    /** The finale is the last entry of the curve, not a branch — and the labyrinth cap still bites. */
    @Test
    void theFinaleIsTheCurvesLastEntry() {
        assertTargetAlwaysIn(12, Set.of(), Set.of(40, 41, 42));
        assertTargetAlwaysIn(12, Set.of(Curse.LABYRINTH), Set.of(45));
    }

    @Test
    void deadEndMinimumsMatchLegacyRules() {
        assertEquals(5, DungeonGenerator.minDeadEnds(config, FloorDepth.of(config, 1), Set.of()));
        assertEquals(6, DungeonGenerator.minDeadEnds(config, FloorDepth.of(config, 3), Set.of()));
        assertEquals(7, DungeonGenerator.minDeadEnds(config, FloorDepth.of(config, 3), Set.of(Curse.LABYRINTH)));
        assertEquals(8, DungeonGenerator.minDeadEnds(config, FloorDepth.of(config, 12), Set.of()));
        assertEquals(9, DungeonGenerator.minDeadEnds(config, FloorDepth.of(config, 12), Set.of(Curse.LABYRINTH)));
    }
}
