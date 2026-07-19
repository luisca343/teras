package es.boffmedia.teras.dungeon.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mutable floor grid the generator works on. Cells reference the {@link Room} occupying them
 * (multi-cell rooms appear at every cell they span) or are empty.
 *
 * <p>Neighbor counts exclude SECRET rooms, as in the legacy {@code countNeighboringRooms}: secret
 * rooms convert former wall cells after everything else is decided, and must not retroactively
 * change any cell's dead-end status — the validator recounts dead ends after placement and would
 * otherwise disagree with the placer.</p>
 */
public final class RoomGrid {

    private final int size;
    private final Room[][] cells;
    private final List<Room> rooms = new ArrayList<>();

    public RoomGrid(int size) {
        this.size = size;
        this.cells = new Room[size][size];
    }

    public int size() {
        return size;
    }

    public GridPos center() {
        return new GridPos(size / 2, size / 2);
    }

    public boolean inBounds(GridPos pos) {
        return pos.x() >= 0 && pos.x() < size && pos.y() >= 0 && pos.y() < size;
    }

    /** The room occupying {@code pos}, or null for an empty (or out-of-bounds) cell. */
    public Room roomAt(GridPos pos) {
        if (!inBounds(pos)) {
            return null;
        }
        return cells[pos.y()][pos.x()];
    }

    public boolean isEmpty(GridPos pos) {
        return inBounds(pos) && cells[pos.y()][pos.x()] == null;
    }

    /** Rooms in placement order. */
    public List<Room> rooms() {
        return List.copyOf(rooms);
    }

    public int occupiedCellCount() {
        int count = 0;
        for (Room room : rooms) {
            count += room.shape().cellCount();
        }
        return count;
    }

    public void place(Room room) {
        for (GridPos cell : room.cells()) {
            if (!inBounds(cell)) {
                throw new IllegalArgumentException("Room out of bounds at " + cell + ": " + room);
            }
            if (cells[cell.y()][cell.x()] != null) {
                throw new IllegalArgumentException("Cell already occupied at " + cell + ": " + room);
            }
        }
        for (GridPos cell : room.cells()) {
            cells[cell.y()][cell.x()] = room;
        }
        rooms.add(room);
    }

    /**
     * Swaps {@code existing} for {@code replacement}, which must cover every cell the old room
     * held; the extra cells have to be free. The room keeps its slot in {@link #rooms()} —
     * template variants and encounter seeds are both derived from placement index, so a room that
     * grows must not shift itself, or anything after it, onto a different index.
     *
     * <p>Like {@link #place}, every cell is checked before any is written, so a rejected replace
     * leaves the grid exactly as it was.</p>
     */
    public void replace(Room existing, Room replacement) {
        int index = rooms.indexOf(existing);
        if (index < 0) {
            throw new IllegalArgumentException("Room is not on the grid: " + existing);
        }
        List<GridPos> footprint = replacement.cells();
        for (GridPos cell : footprint) {
            if (!inBounds(cell)) {
                throw new IllegalArgumentException("Room out of bounds at " + cell + ": " + replacement);
            }
            Room occupant = cells[cell.y()][cell.x()];
            if (occupant != null && occupant != existing) {
                throw new IllegalArgumentException("Cell already occupied at " + cell + ": " + replacement);
            }
        }
        if (!new java.util.HashSet<>(footprint).containsAll(existing.cells())) {
            throw new IllegalArgumentException("Replacement drops cells of " + existing);
        }
        for (GridPos cell : existing.cells()) {
            cells[cell.y()][cell.x()] = null;
        }
        for (GridPos cell : footprint) {
            cells[cell.y()][cell.x()] = replacement;
        }
        rooms.set(index, replacement);
    }

    /**
     * Occupied cells orthogonally adjacent to {@code footprint} but outside it — one per door edge
     * the door graph will emit for that room.
     *
     * <p>Unlike {@link #occupiedNeighborCount} this <b>counts secret rooms</b>, and that difference
     * is the whole point: a cracked wall is still a way in, so "the boss chamber has exactly one
     * entrance" has to mean exactly one. Counting the secret-aware way would let a room sit against
     * the SUPER_SECRET and still look sealed.</p>
     */
    public int externalNeighborCount(java.util.Collection<GridPos> footprint) {
        java.util.Set<GridPos> body = new java.util.HashSet<>(footprint);
        int count = 0;
        for (GridPos cell : body) {
            for (GridDir dir : GridDir.values()) {
                GridPos outside = cell.step(dir);
                if (!body.contains(outside) && roomAt(outside) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    public int externalNeighborCount(Room room) {
        return externalNeighborCount(room.cells());
    }

    /** Occupied orthogonal neighbors of {@code pos}, secret rooms excluded. */
    public int occupiedNeighborCount(GridPos pos) {
        int count = 0;
        for (GridDir dir : GridDir.values()) {
            Room neighbor = roomAt(pos.step(dir));
            if (neighbor != null && !neighbor.type().isSecret()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Cells of 1×1 rooms with exactly one occupied neighbor — the legacy definition, type-agnostic:
     * a special room that claimed a dead end still counts as one.
     */
    public List<GridPos> deadEndCells() {
        List<GridPos> deadEnds = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                GridPos pos = new GridPos(x, y);
                Room room = roomAt(pos);
                if (room != null && room.isSingle() && !room.type().isSecret()
                        && occupiedNeighborCount(pos) == 1) {
                    deadEnds.add(pos);
                }
            }
        }
        return deadEnds;
    }

    /**
     * BFS cell-distance from the start cell to every reachable occupied cell, never routing
     * through a secret room — a closed cracked wall is not a corridor, so a secret room must not
     * shorten anyone's distance. One pass replaces the legacy per-room BFS (which rebuilt its
     * queue with {@code ArrayList.remove(0)} per query).
     */
    public Map<GridPos, Integer> distancesFromCenter() {
        Map<GridPos, Integer> distances = new HashMap<>();
        ArrayDeque<GridPos> queue = new ArrayDeque<>();
        GridPos start = center();
        if (roomAt(start) == null) {
            return distances;
        }
        distances.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            GridPos current = queue.poll();
            int next = distances.get(current) + 1;
            for (GridDir dir : GridDir.values()) {
                GridPos neighbor = current.step(dir);
                Room room = roomAt(neighbor);
                if (room != null && !room.type().isSecret() && !distances.containsKey(neighbor)) {
                    distances.put(neighbor, next);
                    queue.add(neighbor);
                }
            }
        }
        return distances;
    }
}
