package es.boffmedia.teras.dungeon.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A validated, immutable floor: what the generator hands to the materializer and the run session.
 * Rooms, door edges and metadata are fixed here — nothing downstream re-derives structure from
 * adjacency, and {@code stage + curses + seedString} rebuilds this exact layout, room variants
 * included ({@link #baseSeed()} is what variant selection derives from).
 */
public final class DungeonLayout {

    private final int stage;
    private final Set<Curse> curses;
    private final String seedString;
    private final long baseSeed;
    private final int attempt;
    private final RoomGrid grid;
    private final List<DoorEdge> doors;
    private final List<String> warnings;

    public DungeonLayout(int stage, Set<Curse> curses, String seedString, long baseSeed,
                         int attempt, RoomGrid grid, List<DoorEdge> doors, List<String> warnings) {
        this.stage = stage;
        this.curses = Set.copyOf(curses);
        this.seedString = seedString;
        this.baseSeed = baseSeed;
        this.attempt = attempt;
        this.grid = grid;
        this.doors = List.copyOf(doors);
        this.warnings = List.copyOf(warnings);
    }

    public int stage() {
        return stage;
    }

    public Set<Curse> curses() {
        return curses;
    }

    /** The user-facing seed; feeding it back with the same stage and curses reproduces the floor. */
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
