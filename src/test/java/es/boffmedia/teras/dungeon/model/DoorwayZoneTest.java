package es.boffmedia.teras.dungeon.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The volume in front of every doorway. Reserved on <b>all four</b> sides of every exterior wall,
 * because a template cannot know which sides the layout will put doors on.
 *
 * <p>This exists because a nest block authored in an entrance bricks the door it stands in, and a
 * spawn marker there puts an enemy inside the wall the moment the opening is carved. The editor,
 * the materializer and the room generator all ask this same question — three independent derivations
 * is how they drift apart.</p>
 */
class DoorwayZoneTest {

    private static final int SIZE = 21;
    private static final int WIDTH = 3;
    private static final int HEIGHT = 3;
    private static final GridPos ORIGIN = new GridPos(0, 0);

    private static boolean in(int x, int y, int z) {
        return DoorwayZone.contains(ORIGIN, RoomShape.SINGLE, x, y, z, SIZE, WIDTH, HEIGHT);
    }

    /** The band is centred: inset 9, so blocks 9, 10 and 11. */
    @Test
    void allFourSidesAreReserved() {
        assertTrue(in(10, 1, 1), "north");
        assertTrue(in(10, 1, SIZE - 2), "south");
        assertTrue(in(1, 1, 10), "west");
        assertTrue(in(SIZE - 2, 1, 10), "east");
    }

    @Test
    void theWholeBandIsReservedNotJustTheCentre() {
        for (int x = 9; x <= 11; x++) {
            assertTrue(in(x, 1, 1), "x=" + x);
        }
        assertFalse(in(8, 1, 1), "8 is outside the band");
        assertFalse(in(12, 1, 1), "12 is outside the band");
    }

    /** The middle of a room is always free — the zone must not swallow the usable floor. */
    @Test
    void theInteriorIsFree() {
        assertFalse(in(10, 1, 10));
        assertFalse(in(5, 1, 5));
        assertFalse(in(15, 1, 15));
    }

    /** A marker just above a doorway still obstructs it, so the headroom is reserved too. */
    @Test
    void headroomAboveTheOpeningIsReserved() {
        assertTrue(in(10, 0, 1), "the floor a player walks in on");
        assertTrue(in(10, HEIGHT, 1));
        assertTrue(in(10, HEIGHT + 1, 1), "one block of headroom");
        assertFalse(in(10, HEIGHT + 2, 1), "above that is a ledge, not a doorway");
    }

    /**
     * A perch is above the doorway height, which is what lets ranged spawns sit on shelves near a
     * wall without ever fouling an entrance.
     */
    @Test
    void perchesClearTheZone() {
        assertFalse(in(10, 5, 2), "a shelf at y=5 is clear even in the band");
    }

    /**
     * Interior boundaries are not doorways. A 2×1's neck is a feature and no door is ever cut
     * there, so reserving it would forbid marking the most interesting part of the room.
     */
    @Test
    void interiorBoundariesAreNotReserved() {
        GridPos left = new GridPos(0, 0);
        // The east side of the left cell faces the right cell, which the room owns.
        assertFalse(DoorwayZone.contains(left, RoomShape.HORIZONTAL,
                SIZE - 2, 1, 10, SIZE, WIDTH, HEIGHT), "the neck between two owned cells");
        // Its west side faces outward and is still reserved.
        assertTrue(DoorwayZone.contains(left, RoomShape.HORIZONTAL,
                1, 1, 10, SIZE, WIDTH, HEIGHT), "the outward wall");
    }

    /**
     * The volumes the editor draws are exactly the volumes the editor enforces.
     *
     * <p>{@code contains} answers per block and {@code zonesOf} hands back regions; the editor tests
     * with the first and outlines with the second. Two derivations of one shape drift, and a hint
     * that shows something other than what is refused is worse than no hint — so they are held
     * against each other over every shape, every cell and every block of it.</p>
     */
    @Test
    void theDrawnZonesAreTheEnforcedOnes() {
        for (RoomShape shape : RoomShape.values()) {
            for (GridPos cell : shape.offsets()) {
                var zones = DoorwayZone.zonesOf(cell, shape, SIZE, WIDTH, HEIGHT);
                for (int x = 0; x < SIZE; x++) {
                    for (int z = 0; z < SIZE; z++) {
                        for (int y = 0; y <= HEIGHT + 2; y++) {
                            boolean drawn = false;
                            for (var zone : zones) {
                                if (x >= zone.minX() && x <= zone.maxX()
                                        && y >= zone.minY() && y <= zone.maxY()
                                        && z >= zone.minZ() && z <= zone.maxZ()) {
                                    drawn = true;
                                    break;
                                }
                            }
                            assertEquals(DoorwayZone.contains(cell, shape, x, y, z,
                                            SIZE, WIDTH, HEIGHT), drawn,
                                    shape + " cell " + cell + " at " + x + "," + y + "," + z);
                        }
                    }
                }
            }
        }
    }

    /** An L's missing quadrant is outside the room, so the side facing it is a real exterior wall. */
    @Test
    void anLReservesTheSidesFacingItsGap() {
        GridPos topRight = new GridPos(1, 0);
        // L_TOP_LEFT owns (0,0),(1,0),(0,1) — so (1,0)'s south side faces the gap at (1,1).
        assertTrue(DoorwayZone.contains(topRight, RoomShape.L_TOP_LEFT,
                10, 1, SIZE - 2, SIZE, WIDTH, HEIGHT));
        // Its west side faces (0,0), which the room owns.
        assertFalse(DoorwayZone.contains(topRight, RoomShape.L_TOP_LEFT,
                1, 1, 10, SIZE, WIDTH, HEIGHT));
    }
}
