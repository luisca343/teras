package es.boffmedia.teras.dungeon.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eight shapes, four things to build. The rotations here are the whole reason one authored L can
 * serve all four orientations — get them wrong and a room pastes with its missing quadrant on the
 * wrong side, which looks like a generation bug rather than a rotation one.
 */
class ShapeFamilyTest {

    @Test
    void everyShapeBelongsToExactlyOneFamily() {
        for (RoomShape shape : RoomShape.values()) {
            assertTrue(shape.family().shapes().contains(shape), shape.name());
        }
    }

    @Test
    void familiesPartitionTheShapes() {
        Set<RoomShape> seen = new HashSet<>();
        for (ShapeFamily family : ShapeFamily.values()) {
            for (RoomShape shape : family.shapes()) {
                assertTrue(seen.add(shape), shape + " is in two families");
            }
        }
        assertEquals(RoomShape.values().length, seen.size());
    }

    /**
     * The L cycle, derived from the offsets: turning a 2x2 block ninety degrees clockwise maps a
     * cell (x,z) to (1-z,x), which walks the missing quadrant TOP_LEFT -> TOP_RIGHT ->
     * BOTTOM_RIGHT -> BOTTOM_LEFT. The authored L is TOP_LEFT, so those are its four rotations.
     */
    @Test
    void theLCycleMatchesTheOffsets() {
        assertEquals(0, RoomShape.L_TOP_LEFT.baseRotation());
        assertEquals(90, RoomShape.L_TOP_RIGHT.baseRotation());
        assertEquals(180, RoomShape.L_BOTTOM_RIGHT.baseRotation());
        assertEquals(270, RoomShape.L_BOTTOM_LEFT.baseRotation());

        // Verified against the geometry rather than asserted by hand: rotating the missing quadrant
        // of each L must land on the missing quadrant of the next.
        RoomShape[] cycle = {RoomShape.L_TOP_LEFT, RoomShape.L_TOP_RIGHT,
                RoomShape.L_BOTTOM_RIGHT, RoomShape.L_BOTTOM_LEFT};
        for (int i = 0; i < cycle.length; i++) {
            GridPos gap = missingQuadrant(cycle[i]);
            GridPos turned = new GridPos(1 - gap.y(), gap.x());
            assertEquals(missingQuadrant(cycle[(i + 1) % cycle.length]), turned,
                    cycle[i] + " does not rotate onto " + cycle[(i + 1) % cycle.length]);
        }
    }

    /** A 2x1 is authored horizontal; vertical is the same template turned a quarter. */
    @Test
    void largeIsOneTemplateTurned() {
        assertEquals(ShapeFamily.LARGE, RoomShape.HORIZONTAL.family());
        assertEquals(ShapeFamily.LARGE, RoomShape.VERTICAL.family());
        assertEquals(0, RoomShape.HORIZONTAL.baseRotation());
        assertEquals(90, RoomShape.VERTICAL.baseRotation());
    }

    /** Square footprints survive any rotation, so they are authored once and used as authored. */
    @Test
    void squareShapesNeedNoRotation() {
        assertEquals(0, RoomShape.SINGLE.baseRotation());
        assertEquals(0, RoomShape.QUAD.baseRotation());
    }

    @Test
    void suffixesNameTheTemplates() {
        assertEquals("", ShapeFamily.SINGLE.suffix());
        assertEquals("_large", ShapeFamily.LARGE.suffix());
        assertEquals("_l", ShapeFamily.L.suffix());
        assertEquals("_big", ShapeFamily.BIG.suffix());
    }

    @Test
    void familiesExpandToConcreteShapes() {
        Set<RoomShape> shapes = ShapeFamily.shapesOf(
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE));
        assertEquals(EnumSet.of(RoomShape.SINGLE, RoomShape.HORIZONTAL, RoomShape.VERTICAL), shapes);
        assertEquals(4, ShapeFamily.L.shapes().size());
    }

    /**
     * Every legacy orientation name maps onto a family. Configs were written listing shapes by
     * orientation, and once families arrived none of those names parsed — a piso silently collapsed
     * to single cells, taking L rooms, large rooms and 2x2 boss chambers with it. The mapping that
     * rescues those files is this one.
     */
    @Test
    void everyOrientationNameMapsToAFamily() {
        assertEquals(ShapeFamily.SINGLE, RoomShape.SINGLE.family());
        assertEquals(ShapeFamily.LARGE, RoomShape.HORIZONTAL.family());
        assertEquals(ShapeFamily.LARGE, RoomShape.VERTICAL.family());
        assertEquals(ShapeFamily.BIG, RoomShape.QUAD.family());
        for (RoomShape shape : new RoomShape[]{RoomShape.L_TOP_LEFT, RoomShape.L_TOP_RIGHT,
                RoomShape.L_BOTTOM_LEFT, RoomShape.L_BOTTOM_RIGHT}) {
            assertEquals(ShapeFamily.L, shape.family(), shape.name());
        }
    }

    /**
     * The old full list must come back as all four families — not as SINGLE alone, which is what a
     * config written before the change parsed to and why nothing but single rooms generated.
     */
    @Test
    void theLegacyFullListRecoversEveryFamily() {
        Set<ShapeFamily> recovered = EnumSet.noneOf(ShapeFamily.class);
        for (String legacy : new String[]{"single", "horizontal", "vertical", "quad",
                "l_top_left", "l_top_right", "l_bottom_left", "l_bottom_right"}) {
            recovered.add(RoomShape.valueOf(legacy.toUpperCase(java.util.Locale.ROOT)).family());
        }
        assertEquals(EnumSet.allOf(ShapeFamily.class), recovered);
    }

    /** The 2x2 cell an L does not own — the thing rotation has to carry around correctly. */
    private static GridPos missingQuadrant(RoomShape shape) {
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                GridPos cell = new GridPos(x, z);
                if (!shape.offsets().contains(cell)) {
                    return cell;
                }
            }
        }
        throw new AssertionError(shape + " has no missing quadrant");
    }
}
