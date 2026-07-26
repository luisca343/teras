package es.boffmedia.teras.dungeon.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The volume in front of every doorway that must stay clear.
 *
 * <p>A template cannot know which of its four sides the layout will put a door on, so all four are
 * reserved — the band the doorway is cut through, plus enough depth inward to walk in. Anything
 * placed here is at best in the way and at worst load-bearing: a nest block dropped in an entrance
 * blocks the door it stands in, and a spawn marker there puts an enemy inside the wall the moment
 * the doorway is carved.</p>
 *
 * <p>Interior boundaries between two cells of the same room are <b>not</b> reserved: a 2×1's neck is
 * a feature, not a doorway, and no door is ever cut there.</p>
 *
 * <p>Pure geometry so the editor, the materializer and the room generator all answer the question
 * the same way. Three places deriving it separately is how they drift.</p>
 */
public final class DoorwayZone {
    private DoorwayZone() {}

    /** How far inward from a wall stays clear. Enough to stand in the opening and step through. */
    public static final int DEPTH = 4;

    /**
     * Whether a position local to {@code cell} lies in a reserved doorway volume.
     *
     * @param cell     which cell of the room, as a shape offset
     * @param shape    the room's footprint, so interior boundaries are excluded
     * @param lx,ly,lz position within that cell, {@code 0..roomSize-1} and {@code 0..} upward
     */
    public static boolean contains(GridPos cell, RoomShape shape, int lx, int ly, int lz,
                                   int roomSize, int doorWidth, int doorHeight) {
        // One block of headroom above the opening: a marker just over a door still obstructs it.
        if (ly > doorHeight + 1) {
            return false;
        }
        int inset = (roomSize - doorWidth) / 2;
        boolean inBandX = lx >= inset && lx < inset + doorWidth;
        boolean inBandZ = lz >= inset && lz < inset + doorWidth;
        int far = roomSize - 1 - DEPTH;

        if (inBandX && lz <= DEPTH && !owns(shape, cell, 0, -1)) {
            return true;
        }
        if (inBandX && lz >= far && !owns(shape, cell, 0, 1)) {
            return true;
        }
        if (inBandZ && lx <= DEPTH && !owns(shape, cell, -1, 0)) {
            return true;
        }
        return inBandZ && lx >= far && !owns(shape, cell, 1, 0);
    }

    /**
     * One reserved volume, in cell-local coordinates, both ends inclusive.
     *
     * <p>{@link #contains} answers "is this block reserved" and this answers "where are the reserved
     * regions" — the editor needs the second to draw them. Deriving the outline separately from the
     * test is how a hint ends up showing something other than what is enforced, so
     * {@link #zonesOf} is held against {@code contains} by test.</p>
     */
    public record Zone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    /**
     * Every reserved volume of {@code cell}, bounded to the cell — one per exterior side, since a
     * side facing another cell of the same room is a neck rather than a doorway.
     */
    public static List<Zone> zonesOf(GridPos cell, RoomShape shape, int roomSize,
                                     int doorWidth, int doorHeight) {
        int inset = (roomSize - doorWidth) / 2;
        int bandLow = inset;
        int bandHigh = inset + doorWidth - 1;
        int far = roomSize - 1 - DEPTH;
        int top = doorHeight + 1;
        List<Zone> zones = new ArrayList<>(4);
        if (!owns(shape, cell, 0, -1)) {
            zones.add(new Zone(bandLow, 0, 0, bandHigh, top, DEPTH));
        }
        if (!owns(shape, cell, 0, 1)) {
            zones.add(new Zone(bandLow, 0, far, bandHigh, top, roomSize - 1));
        }
        if (!owns(shape, cell, -1, 0)) {
            zones.add(new Zone(0, 0, bandLow, DEPTH, top, bandHigh));
        }
        if (!owns(shape, cell, 1, 0)) {
            zones.add(new Zone(far, 0, bandLow, roomSize - 1, top, bandHigh));
        }
        return zones;
    }

    /**
     * The columns a door <b>frame</b> lands in — the two beside each exterior opening, one block
     * deep, where {@code DoorDressing} stands its jambs.
     *
     * <p>Not reserved: nothing here is load-bearing and a room may build in them. They are drawn in
     * the editor anyway, because the frame is written after the paste and therefore silently wins,
     * and a lantern that vanishes between the pad and the floor is the exact failure this file
     * exists to prevent. Reserved means "you may not"; this means "you will be overwritten".</p>
     *
     * <p>The two column offsets are {@code inset-1} and {@code inset+doorWidth} — one either side of
     * the opening — which is the same pair the dressing derives from the same three numbers.</p>
     */
    public static List<Zone> frameZonesOf(GridPos cell, RoomShape shape, int roomSize,
                                          int doorWidth, int doorHeight) {
        int inset = (roomSize - doorWidth) / 2;
        int low = inset - 1;
        int high = inset + doorWidth;
        int top = doorHeight + 2;
        List<Zone> zones = new ArrayList<>(8);
        if (!owns(shape, cell, 0, -1)) {
            zones.add(new Zone(low, 0, 1, low, top, 1));
            zones.add(new Zone(high, 0, 1, high, top, 1));
        }
        if (!owns(shape, cell, 0, 1)) {
            zones.add(new Zone(low, 0, roomSize - 2, low, top, roomSize - 2));
            zones.add(new Zone(high, 0, roomSize - 2, high, top, roomSize - 2));
        }
        if (!owns(shape, cell, -1, 0)) {
            zones.add(new Zone(1, 0, low, 1, top, low));
            zones.add(new Zone(1, 0, high, 1, top, high));
        }
        if (!owns(shape, cell, 1, 0)) {
            zones.add(new Zone(roomSize - 2, 0, low, roomSize - 2, top, low));
            zones.add(new Zone(roomSize - 2, 0, high, roomSize - 2, top, high));
        }
        return zones;
    }

    private static boolean owns(RoomShape shape, GridPos cell, int dx, int dz) {
        return shape.offsets().contains(new GridPos(cell.x() + dx, cell.y() + dz));
    }
}
