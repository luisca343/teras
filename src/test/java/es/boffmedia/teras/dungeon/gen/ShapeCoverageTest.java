package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every shape a piso declares actually turns up, and turns up in every orientation.
 *
 * <p>{@link RoomCarverTest} pins that all four L orientations <i>occur</i>; this pins that they
 * occur at comparable rates. The difference is the bug it was written for: the carver tried a shape
 * only where its anchor — its minimum corner — landed on the frontier cell, which is one placement
 * out of the two to four that fit. Every orientation still appeared, so the existence test passed,
 * while L_BOTTOM_RIGHT (the one shape whose minimum corner is the cell it does not own, so
 * anchoring cost it a fourth free cell) came out four times rarer than its siblings, and every large
 * room grew east and south of its frontier cell and never west or north.</p>
 */
class ShapeCoverageTest {

    private static final GenConfig CONFIG = GenConfig.defaults();
    private static final int FLOORS_PER_STAGE = 120;
    private static final int[] STAGES = {1, 3, 6, 12};

    /** Rooms of each shape across a sweep of stages, with every family declared. */
    private static Map<RoomShape, Integer> census(Set<RoomShape> shapes) {
        Map<RoomShape, Integer> counts = new EnumMap<>(RoomShape.class);
        for (RoomShape shape : RoomShape.values()) {
            counts.put(shape, 0);
        }
        for (int stage : STAGES) {
            for (int i = 0; i < FLOORS_PER_STAGE; i++) {
                DungeonLayout layout = DungeonGenerator.generate(CONFIG,
                        FloorDepth.of(CONFIG, stage), Set.<Curse>of(), shapes,
                        "coverage-" + stage + "-" + i);
                for (Room room : layout.rooms()) {
                    counts.merge(room.shape(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    @Test
    void everyShapeIsGeneratedWhenEveryFamilyIsDeclared() {
        Map<RoomShape, Integer> counts = census(EnumSet.allOf(RoomShape.class));
        for (RoomShape shape : RoomShape.values()) {
            assertTrue(counts.get(shape) > 0, shape + " never generated: " + counts);
        }
    }

    /**
     * The four L orientations are one authored template turned four ways with one shared chance, so
     * nothing in the design should prefer any of them. The bar is deliberately loose — these are
     * still random draws constrained by what fits — but a return to anchor-only placement puts
     * L_BOTTOM_RIGHT at roughly a quarter of the others and trips it.
     */
    @Test
    void theFourLOrientationsAppearAtComparableRates() {
        Map<RoomShape, Integer> counts = census(EnumSet.allOf(RoomShape.class));
        assertBalanced(counts, ShapeFamily.L.shapes());
    }

    /** A 2×1 is one template turned twice; horizontal and vertical share a chance in GenConfig. */
    @Test
    void bothLargeOrientationsAppearAtComparableRates() {
        Map<RoomShape, Integer> counts = census(EnumSet.allOf(RoomShape.class));
        assertBalanced(counts, ShapeFamily.LARGE.shapes());
    }

    private static void assertBalanced(Map<RoomShape, Integer> counts, Set<RoomShape> group) {
        int min = group.stream().mapToInt(counts::get).min().orElseThrow();
        int max = group.stream().mapToInt(counts::get).max().orElseThrow();
        assertTrue(min > 0, "an orientation never generated: " + counts);
        assertTrue(max <= min * 2,
                "orientations are lopsided (" + min + ".." + max + "): " + counts);
    }

    /**
     * A large shape must be able to grow in every direction from the cell the carve reached. The
     * floor is symmetric around its start and the start is a single cell, so counting which side of
     * it large rooms land on is what exposes a directional bias: anchor-only placement put every
     * one of them east and south.
     */
    @Test
    void largeRoomsGrowInEveryDirectionFromTheStart() {
        int west = 0;
        int east = 0;
        int north = 0;
        int south = 0;
        for (int stage : STAGES) {
            for (int i = 0; i < FLOORS_PER_STAGE; i++) {
                DungeonLayout layout = DungeonGenerator.generate(CONFIG,
                        FloorDepth.of(CONFIG, stage), Set.<Curse>of(),
                        EnumSet.allOf(RoomShape.class), "spread-" + stage + "-" + i);
                GridPos centre = layout.grid().center();
                for (Room room : layout.rooms()) {
                    if (room.isSingle()) {
                        continue;
                    }
                    for (GridPos cell : room.cells()) {
                        if (cell.x() < centre.x()) {
                            west++;
                        }
                        if (cell.x() > centre.x()) {
                            east++;
                        }
                        if (cell.y() < centre.y()) {
                            north++;
                        }
                        if (cell.y() > centre.y()) {
                            south++;
                        }
                    }
                }
            }
        }
        int min = Math.min(Math.min(west, east), Math.min(north, south));
        int max = Math.max(Math.max(west, east), Math.max(north, south));
        assertTrue(min > 0, "large rooms never reached one side of the start");
        assertTrue(max <= min * 2, "large rooms favour one direction: W=" + west + " E=" + east
                + " N=" + north + " S=" + south);
    }

    /**
     * A piso that declines a family must never be handed one of its shapes — the materializer would
     * ask for a template the piso was excused from authoring and leave the cell empty.
     */
    @Test
    void decliningAFamilyKeepsItsShapesOffTheFloor() {
        Set<RoomShape> narrow = ShapeFamily.shapesOf(
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE));
        Map<RoomShape, Integer> counts = census(narrow);
        for (RoomShape shape : RoomShape.values()) {
            if (narrow.contains(shape)) {
                assertTrue(counts.get(shape) > 0, shape + " was declared but never generated");
            } else {
                assertTrue(counts.get(shape) == 0, shape + " generated on a piso that declined it");
            }
        }
    }
}
