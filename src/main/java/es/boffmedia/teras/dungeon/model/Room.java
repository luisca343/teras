package es.boffmedia.teras.dungeon.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One room, however many grid cells it spans. The legacy model stored a separate {@code Room} per
 * cell linked by {@code parentX/parentY} fields, and door carving had to suppress parent/child/
 * sibling pairs by hand; here a multi-cell room is a single object referenced from every cell it
 * occupies, so "same room" is identity and those rules disappear.
 *
 * <p>{@code type} is mutable because special-room placement retypes carved NORMAL dead ends;
 * nothing mutates a room once its {@link DungeonLayout} is built.</p>
 */
public final class Room {

    private final GridPos anchor;
    private final RoomShape shape;
    private RoomType type;

    public Room(RoomType type, GridPos anchor, RoomShape shape) {
        this.type = type;
        this.anchor = anchor;
        this.shape = shape;
    }

    public RoomType type() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public GridPos anchor() {
        return anchor;
    }

    public RoomShape shape() {
        return shape;
    }

    public boolean isSingle() {
        return shape == RoomShape.SINGLE;
    }

    /** The grid cells this room occupies, in shape-offset order. */
    public List<GridPos> cells() {
        List<GridPos> cells = new ArrayList<>(shape.cellCount());
        for (GridPos offset : shape.offsets()) {
            cells.add(anchor.offset(offset.x(), offset.y()));
        }
        return cells;
    }

    @Override
    public String toString() {
        return type + "@" + anchor.x() + "," + anchor.y() + "/" + shape;
    }
}
