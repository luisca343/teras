package es.boffmedia.teras.dungeon.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A validated, finished floor: what the generator hands to the materializer and the run session.
 * Rooms, door edges and metadata are fixed here — nothing downstream re-derives structure from
 * adjacency, and {@code floor + curses + seedString} rebuilds this exact layout, room variants
 * included ({@link #baseSeed()} is what variant selection derives from).
 *
 * <p><b>Finished, not defensively immutable.</b> {@link #grid()} hands out the live {@link RoomGrid}
 * and {@link Room#setType} is public, because room <i>identity</i> is load-bearing downstream: door
 * edges hold room references, {@code doorsOf} compares them with {@code ==}, and the materializer
 * keys its marker map by room. Copying the grid on the way out would hand callers rooms that are
 * equal to nothing they already hold. The contract is by convention — once this object exists, its
 * grid is read and never written.</p>
 */
public final class DungeonLayout {

    private final int floor;
    private final Set<Curse> curses;
    private final String seedString;
    private final long baseSeed;
    private final int attempt;
    private final RoomGrid grid;
    private final List<DoorEdge> doors;
    private final List<String> warnings;

    public DungeonLayout(int floor, Set<Curse> curses, String seedString, long baseSeed,
                         int attempt, RoomGrid grid, List<DoorEdge> doors, List<String> warnings) {
        this.floor = floor;
        this.curses = Set.copyOf(curses);
        this.seedString = seedString;
        this.baseSeed = baseSeed;
        this.attempt = attempt;
        this.grid = grid;
        this.doors = List.copyOf(doors);
        this.warnings = List.copyOf(warnings);
    }

    /** The canonical floor this is, not the run's stage — see {@code FloorDepth}. */
    public int floor() {
        return floor;
    }

    public Set<Curse> curses() {
        return curses;
    }

    /** The user-facing seed; feeding it back with the same floor and curses reproduces it. */
    public String seedString() {
        return seedString;
    }

    public long baseSeed() {
        return baseSeed;
    }

    /** Which generation attempt produced this layout (0-based; rerolls consume attempts). */
    public int attempt() {
        return attempt;
    }

    public RoomGrid grid() {
        return grid;
    }

    public List<DoorEdge> doors() {
        return doors;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<Room> rooms() {
        return grid.rooms();
    }

    public Optional<Room> roomOfType(RoomType type) {
        return grid.rooms().stream().filter(r -> r.type() == type).findFirst();
    }

    public Room start() {
        return roomOfType(RoomType.START).orElseThrow();
    }

    public List<DoorEdge> doorsOf(Room room) {
        return doors.stream().filter(d -> d.from() == room || d.to() == room).toList();
    }
}
