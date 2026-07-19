package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.SeededRng;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room-count formula is Isaac's plus the legacy pad, so the reachable values per stage/curse
 * are small closed sets — these pin them so a refactor cannot quietly change floor sizes.
 */
class RoomTargetsTest {

    private final GenConfig config = GenConfig.defaults();

    private void assertTargetAlwaysIn(int stage, Set<Curse> curses, Set<Integer> expected) {
        for (long seed = 0; seed < 200; seed++) {
            int target = DungeonGenerator.targetCells(config, stage, curses, new SeededRng(seed));
            assertTrue(expected.contains(target),
                    "stage " + stage + " curses " + curses + " produced " + target);
        }
    }

    @Test
    void stageOneFloorsAreSmall() {
        assertTargetAlwaysIn(1, Set.of(), Set.of(10, 11, 12));
    }

    @Test
    void curseOfTheLostAddsFourRooms() {
        assertTargetAlwaysIn(1, Set.of(Curse.LOST), Set.of(14, 15, 16));
    }

    @Test
    void midGameFloorsHitTheBaseCap() {
        assertTargetAlwaysIn(6, Set.of(), Set.of(22, 23));
    }

    @Test
    void labyrinthNearlyDoublesTheFloor() {
        assertTargetAlwaysIn(6, Set.of(Curse.LABYRINTH), Set.of(38, 39));
    }

    @Test
    void finalStageIsFixedSize() {
        assertTargetAlwaysIn(12, Set.of(), Set.of(52, 53));
        assertTargetAlwaysIn(12, Set.of(Curse.LABYRINTH), Set.of(52, 53));
    }

    @Test
    void deadEndMinimumsMatchLegacyRules() {
        assertEquals(5, DungeonGenerator.minDeadEnds(config, 1, Set.of()));
        assertEquals(6, DungeonGenerator.minDeadEnds(config, 3, Set.of()));
        assertEquals(7, DungeonGenerator.minDeadEnds(config, 3, Set.of(Curse.LABYRINTH)));
        assertEquals(8, DungeonGenerator.minDeadEnds(config, 12, Set.of()));
        assertEquals(9, DungeonGenerator.minDeadEnds(config, 12, Set.of(Curse.LABYRINTH)));
    }
}
