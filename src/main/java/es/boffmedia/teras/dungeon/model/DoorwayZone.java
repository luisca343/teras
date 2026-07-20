package es.boffmedia.teras.dungeon.model;

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

    private static boolean owns(RoomShape shape, GridPos cell, int dx, int dz) {
        return shape.offsets().contains(new GridPos(cell.x() + dx, cell.y() + dz));
    }
}
