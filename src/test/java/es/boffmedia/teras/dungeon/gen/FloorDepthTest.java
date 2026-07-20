package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.SeededRng;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Variable-length dungeons, which is what {@link FloorDepth} exists for. The difficulty curve is
 * authored for a twelve-floor run; a dungeon of any other length maps onto it, so a two-floor
 * dungeon still reaches its own climax instead of generating two starter floors.
 *
 * <p>Also covers the piso shape opt-out, since both arrived with the same change to the generator.</p>
 */
class FloorDepthTest {

    private static final GenConfig CONFIG = GenConfig.defaults();

    /** A dungeon of the reference length must behave exactly as before, or every old test lied. */
    @Test
    void referenceLengthIsTheIdentityMapping() {
        for (int stage = 1; stage <= 12; stage++) {
            assertEquals(stage, new FloorDepth(stage, 12, 12).curveStage());
        }
    }

    /** A short dungeon compresses the same curve rather than only ever using its shallow end. */
    @Test
    void shortDungeonsCompressTheCurve() {
        assertEquals(6, new FloorDepth(1, 2, 12).curveStage());
        assertEquals(12, new FloorDepth(2, 2, 12).curveStage());
        assertEquals(2, new FloorDepth(1, 6, 12).curveStage());
        assertEquals(12, new FloorDepth(6, 6, 12).curveStage());
    }

    /** A long dungeon stretches it, instead of plateauing a quarter of the way in. */
    @Test
    void longDungeonsStretchTheCurve() {
        assertEquals(12, new FloorDepth(20, 20, 12).curveStage());
        assertTrue(new FloorDepth(1, 20, 12).curveStage() >= 1, "must never fall below 1");
    }

    /**
     * The property the mapping turns on: the last floor lands exactly at the end of the curve, so
     * "this dungeon's finale" and "the end of the difficulty curve" can never drift apart.
     */
    @Test
    void theLastFloorAlwaysReachesTheEndOfTheCurve() {
        for (int length = 1; length <= 30; length++) {
            FloorDepth last = new FloorDepth(length, length, 12);
            assertTrue(last.isFinal());
            assertEquals(12, last.curveStage(), "length " + length + " never reached the finale");
        }
    }

    @Test
    void firstAndFinalAreAboutPositionNotDifficulty() {
        FloorDepth firstOfTwo = new FloorDepth(1, 2, 12);
        assertTrue(firstOfTwo.isFirst(), "still the party's first floor, however deep the curve says");
        assertFalse(firstOfTwo.isFinal());
        assertEquals(6, firstOfTwo.curveStage());
    }

    @Test
    void validityIsAgainstTheDungeonNotTheReference() {
        assertTrue(new FloorDepth(2, 2, 12).isValid());
        assertFalse(new FloorDepth(3, 2, 12).isValid());
        assertTrue(new FloorDepth(20, 20, 12).isValid());
    }

    @Test
    void degenerateInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FloorDepth(0, 12, 12));
        assertThrows(IllegalArgumentException.class, () -> new FloorDepth(1, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> new FloorDepth(1, 12, 0));
    }

    // --- what it means downstream ----------------------------------------------------------------

    /** The finale of a two-floor dungeon must be a finale, not floor two of twelve. */
    @Test
    void aShortDungeonStillGetsItsFinale() {
        Set<Integer> finaleSizes = Set.of(52, 53);
        for (long seed = 0; seed < 50; seed++) {
            int target = DungeonGenerator.targetCells(
                    CONFIG, new FloorDepth(2, 2, 12), Set.of(), new SeededRng(seed));
            assertTrue(finaleSizes.contains(target),
                    "seed " + seed + " gave " + target + " for a two-floor dungeon's last floor");
        }
    }

    /** And its first floor is still treated as the party's first, with fewer required dead ends. */
    @Test
    void aShortDungeonsFirstFloorIsStillTheFirst() {
        assertEquals(5, DungeonGenerator.minDeadEnds(CONFIG, new FloorDepth(1, 2, 12), Set.of()),
                "floor 1 of 2 is still the party's first floor, so it keeps the base minimum");
        // 5 base, +1 for not being the first floor, +2 for being the finale.
        assertEquals(8, DungeonGenerator.minDeadEnds(CONFIG, new FloorDepth(2, 2, 12), Set.of()),
                "the last floor of a short dungeon needs the finale's dead ends");
    }

    @Test
    void curseModifiersStillApplyOnAShortDungeon() {
        int plain = DungeonGenerator.minDeadEnds(CONFIG, new FloorDepth(1, 2, 12), Set.of());
        int cursed = DungeonGenerator.minDeadEnds(
                CONFIG, new FloorDepth(1, 2, 12), Set.of(Curse.LABYRINTH));
        assertEquals(plain + 1, cursed);
    }

    // --- the shape opt-out -----------------------------------------------------------------------

    /**
     * The lever that cuts authoring cost: a piso that declares two shapes must never be handed a
     * layout containing a third, because it has no other piso to borrow that room from.
     */
    @Test
    void carvingHonoursTheDeclaredShapes() {
        Set<RoomShape> tight = EnumSet.of(RoomShape.SINGLE, RoomShape.HORIZONTAL);
        for (long seed = 0; seed < 200; seed++) {
            RoomGrid grid = RoomCarver.carve(CONFIG, 22, 6, tight, new SeededRng(seed));
            for (Room room : grid.rooms()) {
                assertTrue(tight.contains(room.shape()),
                        "seed " + seed + " produced " + room.shape() + " in a piso that declared "
                                + tight);
            }
        }
    }

    /** SINGLE alone is legal and must still carve a floor rather than deadlocking. */
    @Test
    void singleOnlyStillCarves() {
        Set<RoomShape> single = EnumSet.of(RoomShape.SINGLE);
        for (long seed = 0; seed < 100; seed++) {
            RoomGrid grid = RoomCarver.carve(CONFIG, 22, 6, single, new SeededRng(seed));
            assertTrue(grid.rooms().size() > 1, "seed " + seed + " carved nothing");
            for (Room room : grid.rooms()) {
                assertEquals(RoomShape.SINGLE, room.shape());
            }
        }
    }

    /** Declaring everything must leave the pre-opt-out behaviour untouched. */
    @Test
    void allShapesStillProducesLargeRooms() {
        boolean sawLarge = false;
        for (long seed = 0; seed < 100 && !sawLarge; seed++) {
            RoomGrid grid = RoomCarver.carve(CONFIG, 22, 6,
                    EnumSet.allOf(RoomShape.class), new SeededRng(seed));
            sawLarge = grid.rooms().stream().anyMatch(r -> r.shape().isLarge());
        }
        assertTrue(sawLarge, "no large room appeared in 100 seeds with every shape allowed");
    }
}
