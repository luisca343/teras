package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.SeededRng;
import es.boffmedia.teras.dungeon.piso.DungeonDef;
import es.boffmedia.teras.dungeon.piso.TierDef;
import es.boffmedia.teras.dungeon.piso.WeightedRef;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariant {@link FloorDepth} exists for: a floor is the same floor wherever it is played
 * from. A dungeon is a window onto one canonical sequence — it declares where it opens, and its
 * length says only when the run ends, never how its floors generate.
 *
 * <p>The predecessor mapped a run's stage onto the curve proportionally, so a two-floor dungeon's
 * opening floor generated as floor six and its second as the twelfth. These tests pin the opposite.
 * </p>
 *
 * <p>Also covers the piso shape opt-out, since both arrived with the same change to the generator.
 * </p>
 */
class FloorDepthTest {

    private static final GenConfig CONFIG = GenConfig.defaults();

    private static DungeonDef window(String id, int primerPiso, int largo) {
        return new DungeonDef(id, id, primerPiso, List.of(
                new TierDef(largo, 1.0, List.of(new WeightedRef("cuevas", 1)),
                        List.of("jefe"), List.of("minijefe"))));
    }

    // --- floor identity ---------------------------------------------------------------------------

    /** The whole point: stage 1 of a window opening on floor 10 IS floor 10. */
    @Test
    void aWindowsStageMapsOntoItsAbsoluteFloor() {
        assertEquals(1, window("cripta", 1, 2).floorFor(1));
        assertEquals(2, window("cripta", 1, 2).floorFor(2));
        assertEquals(10, window("abismo", 10, 1).floorFor(1));
        assertEquals(5, window("socavon", 5, 2).floorFor(1));
        assertEquals(6, window("socavon", 5, 2).floorFor(2));
    }

    /**
     * The property the whole redesign buys: floor ten built by a one-floor challenge and floor ten
     * reached at stage ten of the full descent are the same floor — same budget, same rules, same
     * seed. Not merely the same size.
     */
    @Test
    void aFloorIsTheSameFloorWhicheverWindowAsksForIt() {
        DungeonDef challenge = window("abismo", 10, 1);
        DungeonDef descent = window("cripta", 1, 12);

        FloorDepth viaChallenge = FloorDepth.ofStage(CONFIG, challenge.primerPiso(), 1);
        FloorDepth viaDescent = FloorDepth.ofStage(CONFIG, descent.primerPiso(), 10);
        assertEquals(viaDescent, viaChallenge, "the same canonical floor, reached two ways");

        for (long seed = 0; seed < 50; seed++) {
            assertEquals(
                    DungeonGenerator.targetCells(CONFIG, viaDescent, Set.of(), new SeededRng(seed)),
                    DungeonGenerator.targetCells(CONFIG, viaChallenge, Set.of(), new SeededRng(seed)));
        }
        assertEquals(DungeonGenerator.minDeadEnds(CONFIG, viaDescent, Set.of()),
                DungeonGenerator.minDeadEnds(CONFIG, viaChallenge, Set.of()));
        assertEquals(DungeonSeeds.baseSeed(viaDescent.floor(), Set.of(), "s"),
                DungeonSeeds.baseSeed(viaChallenge.floor(), Set.of(), "s"),
                "same rules but a different seed would still be a different floor");
    }

    /** And the corollary: two different floors of one run must not collide. */
    @Test
    void differentFloorsStayDifferent() {
        assertNotEquals(DungeonSeeds.baseSeed(1, Set.of(), "s"),
                DungeonSeeds.baseSeed(2, Set.of(), "s"));
    }

    /**
     * Lengthening a dungeon must not touch the floors it already had. The old mapping resized every
     * one of them, which made "adding a tramo changes nothing else" false.
     */
    @Test
    void addingATramoLeavesEarlierFloorsAlone() {
        DungeonDef twoFloors = window("cripta", 1, 2);
        DungeonDef fourFloors = window("cripta", 1, 4);
        for (int stage = 1; stage <= 2; stage++) {
            FloorDepth before = FloorDepth.ofStage(CONFIG, twoFloors.primerPiso(), stage);
            FloorDepth after = FloorDepth.ofStage(CONFIG, fourFloors.primerPiso(), stage);
            assertEquals(before, after, "stage " + stage + " moved when the dungeon grew");
            for (long seed = 0; seed < 25; seed++) {
                assertEquals(
                        DungeonGenerator.targetCells(CONFIG, before, Set.of(), new SeededRng(seed)),
                        DungeonGenerator.targetCells(CONFIG, after, Set.of(), new SeededRng(seed)));
            }
        }
    }

    // --- first and final are properties of the floor ----------------------------------------------

    @Test
    void firstAndFinalDescribeTheSequenceNotTheRun() {
        assertTrue(FloorDepth.of(CONFIG, 1).isFirst());
        assertFalse(FloorDepth.of(CONFIG, 2).isFirst());
        assertTrue(FloorDepth.of(CONFIG, CONFIG.canonicalFloors()).isFinal());
        assertFalse(FloorDepth.of(CONFIG, CONFIG.canonicalFloors() - 1).isFinal());
    }

    /**
     * A short dungeon's last floor is where its run ends, not a finale — the old code gave it the
     * twelfth floor's 50-cell budget, which is how a two-floor dungeon got a twelve-floor climax.
     */
    @Test
    void aShortDungeonsLastFloorIsNotTheSequencesFinale() {
        FloorDepth lastOfCripta = FloorDepth.ofStage(CONFIG, 1, 2);
        assertFalse(lastOfCripta.isFinal(), "floor 2 is floor 2, whoever stops there");
        for (long seed = 0; seed < 50; seed++) {
            int cells = DungeonGenerator.targetCells(CONFIG, lastOfCripta, Set.of(), new SeededRng(seed));
            assertTrue(cells >= 13 && cells <= 15, "floor 2 gave " + cells + " cells");
        }
        assertEquals(6, DungeonGenerator.minDeadEnds(CONFIG, lastOfCripta, Set.of()),
                "not the first floor and not the finale");
    }

    /** A challenge opening deep does not inherit floor one's withholding. */
    @Test
    void aDeepWindowsOpeningFloorIsNotAFirstFloor() {
        FloorDepth opening = FloorDepth.ofStage(CONFIG, 10, 1);
        assertFalse(opening.isFirst(), "floor 10 is floor 10 even when it is played first");
        assertEquals(6, DungeonGenerator.minDeadEnds(CONFIG, opening, Set.of()));
    }

    // --- the curve is authored, not derived -------------------------------------------------------

    /** Deeper floors are bigger — the property the saturating formula could not express. */
    @Test
    void theCurveClimbsAllTheWayDown() {
        int previous = 0;
        for (int floor = 1; floor <= CONFIG.canonicalFloors(); floor++) {
            int cells = CONFIG.cellsFor(floor);
            assertTrue(cells >= previous, "floor " + floor + " is smaller than floor " + (floor - 1));
            previous = cells;
        }
        assertTrue(CONFIG.cellsFor(10) > CONFIG.cellsFor(5),
                "floor 10 must be able to be deeper than floor 5");
    }

    @Test
    void canonicalDepthIsTheLengthOfTheCurve() {
        assertEquals(CONFIG.celdas().size(), CONFIG.canonicalFloors());
    }

    @Test
    void floorsPastTheCurveTakeItsLastEntry() {
        int last = CONFIG.canonicalFloors();
        assertEquals(CONFIG.cellsFor(last), CONFIG.cellsFor(last + 5));
        assertEquals(CONFIG.cellsFor(1), CONFIG.cellsFor(0));
    }

    @Test
    void degenerateInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FloorDepth(0, 12));
        assertThrows(IllegalArgumentException.class, () -> new FloorDepth(1, 0));
        assertThrows(IllegalArgumentException.class, () -> CONFIG.withCurve(List.of(10, 0, 17), 2));
    }

    // --- windows that do not fit ------------------------------------------------------------------

    /**
     * Caught at load rather than clamped in the curve lookup: a window past the sequence would
     * otherwise build its last floor over and over and look like it worked.
     */
    @Test
    void aWindowReachingPastTheSequenceIsRefused() {
        List<String> problems = window("largo", 10, 5).problems(java.util.Map.of(), 12);
        assertTrue(problems.stream().anyMatch(p -> p.contains("10-14") && p.contains("past the 12")),
                "expected a window complaint, got " + problems);

        assertTrue(window("justo", 10, 3).problems(java.util.Map.of(), 12).stream()
                        .noneMatch(p -> p.contains("past the")),
                "floors 10-12 fit exactly and must be accepted");
    }

    @Test
    void aWindowOpeningBeforeTheSequenceIsRefused() {
        assertTrue(window("cero", 0, 2).problems(java.util.Map.of(), 12).stream()
                .anyMatch(p -> p.contains("1-based")));
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

    @Test
    void curseModifiersStillApply() {
        FloorDepth floor = FloorDepth.of(CONFIG, 1);
        int plain = DungeonGenerator.minDeadEnds(CONFIG, floor, Set.of());
        int cursed = DungeonGenerator.minDeadEnds(CONFIG, floor, Set.of(Curse.LABYRINTH));
        assertEquals(plain + 1, cursed);
    }
}
