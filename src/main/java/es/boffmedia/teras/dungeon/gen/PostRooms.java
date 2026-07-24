package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.dungeon.model.SeededRng;

import java.util.ArrayList;
import java.util.List;

/**
 * Rooms appended <b>after</b> a floor has been carved, placed and validated — the exit chamber
 * today, red-room-style extras tomorrow.
 *
 * <p>The whole point of the split is that nothing here can influence generation: the playfield is
 * produced exactly as it always was, and only then is it embedded into a grid widened by
 * {@link GenConfig#postMargin()} on every side. The margin ring is space the carve never sees, so
 * a post room always has somewhere to stand, never collides with the floor, and never costs a
 * generation attempt. The embed is symmetric, which keeps the start room on the grid's exact
 * center — the invariant the validator and the run loop both lean on.</p>
 *
 * <p>Post rooms also own their doors. The door graph is derived from cell adjacency, which is
 * right for the playfield and wrong here: the exit chamber may end up touching ordinary rooms,
 * and every such edge must stay solid wall — a second way into the seal chamber is a way past the
 * seal. So a post room's edges are appended by hand, exactly the ones it means to have.</p>
 */
public final class PostRooms {
    private PostRooms() {}

    /** The playfield copied into a grid widened by {@code margin} cells on every side. */
    static RoomGrid embed(RoomGrid grid, int margin) {
        if (margin <= 0) {
            return grid;
        }
        RoomGrid full = new RoomGrid(grid.size() + 2 * margin);
        for (Room room : grid.rooms()) {
            full.place(new Room(room.type(),
                    room.anchor().offset(margin, margin), room.shape()));
        }
        return full;
    }

    /**
     * Lays la sala del sello — a 2×2 chamber — against the boss room and appends its one door
     * edge, barred {@link DoorKind#SELLO}. Candidates prefer touching nothing but the boss and
     * sitting away from the boss's entrance, so the chamber reads as <i>behind</i> the arena.
     *
     * <p>Placement is guaranteed whenever the boss touches the playfield's edge (the margin ring
     * outward is never carved). The one theoretical miss is a fully interior boss wrapped by
     * rooms at distance two on every free side; then no room is placed and the run engine keeps
     * the in-arena carve, the same fallback a piso without an exit template uses.</p>
     */
    static void appendExitRoom(RoomGrid grid, List<DoorEdge> doors, SeededRng rng) {
        Room boss = firstBoss(grid);
        if (boss == null) {
            return;
        }
        GridPos entrance = bossEntrance(grid, boss);

        // Prefer an exit that shares a full, aligned 2-cell face with a 2×2 boss — the only
        // placement whose shared face has an exact middle, and therefore the only one the centered
        // ceremonial door can straddle. A 1×1 boss (a piso that ships an exit but no boss_big), or
        // a 2×2 boss too boxed in for an aligned face, falls back to any touching 2×2 with a
        // normal door.
        List<GridPos> anchors = boss.shape() == RoomShape.QUAD
                ? alignedAnchors(grid, boss) : new ArrayList<>();
        if (anchors.isEmpty()) {
            anchors = touchingAnchors(grid, boss);
        }
        if (anchors.isEmpty()) {
            return;
        }

        Room exit = new Room(RoomType.EXIT, bestAnchor(grid, boss, entrance, anchors, rng),
                RoomShape.QUAD);
        grid.place(exit);
        doors.addAll(sealDoors(grid, boss, exit));
    }

    private static Room firstBoss(RoomGrid grid) {
        for (Room room : grid.rooms()) {
            if (room.type() == RoomType.BOSS) {
                return room;
            }
        }
        return null;
    }

    /**
     * The four exit anchors that share a full aligned face with a 2×2 boss — one straight out
     * from each of its faces — that actually fit in free space.
     */
    private static List<GridPos> alignedAnchors(RoomGrid grid, Room boss) {
        GridPos a = boss.anchor();
        List<GridPos> anchors = new ArrayList<>(4);
        for (GridPos anchor : List.of(a.offset(0, -2), a.offset(0, 2),
                a.offset(-2, 0), a.offset(2, 0))) {
            if (fits(grid, quadCells(anchor))) {
                anchors.add(anchor);
            }
        }
        return anchors;
    }

    /** Every 2×2 anchor that fits and touches the boss — the fallback when no aligned face is free. */
    private static List<GridPos> touchingAnchors(RoomGrid grid, Room boss) {
        List<GridPos> anchors = new ArrayList<>();
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.size(); x++) {
                GridPos anchor = new GridPos(x, y);
                List<GridPos> quad = quadCells(anchor);
                if (fits(grid, quad) && touchesBoss(grid, quad, boss)) {
                    anchors.add(anchor);
                }
            }
        }
        return anchors;
    }

    /** Fewest foreign contacts, then farthest from the boss's entrance; ties broken by the rng. */
    private static GridPos bestAnchor(RoomGrid grid, Room boss, GridPos entrance,
                                      List<GridPos> anchors, SeededRng rng) {
        List<GridPos> best = new ArrayList<>();
        long bestScore = Long.MIN_VALUE;
        for (GridPos anchor : anchors) {
            List<GridPos> quad = quadCells(anchor);
            long score = -1000L * foreignContacts(grid, quad, boss) + distanceFrom(entrance, quad);
            if (score > bestScore) {
                bestScore = score;
                best.clear();
            }
            if (score == bestScore) {
                best.add(anchor);
            }
        }
        return rng.pick(best);
    }

    /**
     * Every boss↔exit edge, normalized to EAST/SOUTH like the rest of the door graph. An aligned
     * attachment yields two parallel edges (the full shared face); a fallback yields one. The
     * reveal reads that count to decide between a centered grand door and a normal one.
     */
    private static List<DoorEdge> sealDoors(RoomGrid grid, Room boss, Room exit) {
        List<DoorEdge> shared = new ArrayList<>();
        for (GridPos cell : boss.cells()) {
            for (GridDir dir : List.of(GridDir.EAST, GridDir.SOUTH)) {
                if (grid.roomAt(cell.step(dir)) == exit) {
                    shared.add(new DoorEdge(cell, dir, boss, exit, DoorKind.SELLO));
                }
            }
        }
        for (GridPos cell : exit.cells()) {
            for (GridDir dir : List.of(GridDir.EAST, GridDir.SOUTH)) {
                if (grid.roomAt(cell.step(dir)) == boss) {
                    shared.add(new DoorEdge(cell, dir, exit, boss, DoorKind.SELLO));
                }
            }
        }
        return shared;
    }

    /**
     * Hangs the seal chamber's satellites off its flanks: El Acreedor's 1×1 DEVIL_DEAL room and la
     * Orden's ORDEN room, on the two exit sides perpendicular to the boss, reached from inside the
     * chamber once the boss has fallen (PRODUCCION §10.4, PISOS §63e).
     *
     * <p><b>This method decides nothing.</b> Whether either room is wanted is answered by
     * {@link SatelliteOdds} against the run's ledger and the floor just played, and the answer is
     * passed in — which is what keeps the conditions pure and unit-testable while generation stays
     * a function of its inputs. Both rooms reuse the shipped reveal wholesale: their doors are
     * barred at build and carved open when the boss dies.</p>
     *
     * <p>The doors are appended by hand after the door graph is built, exactly like the seal door,
     * so a satellite that happens to abut another room grows no accidental second entrance.</p>
     */
    static void appendSatellites(RoomGrid grid, List<DoorEdge> doors, SeededRng rng,
                                 boolean wantAcreedor, boolean wantOrden) {
        Room exit = firstOfType(grid, RoomType.EXIT);
        Room boss = firstBoss(grid);
        if (exit == null || boss == null || (!wantAcreedor && !wantOrden)) {
            return;
        }
        // The two sides perpendicular to the boss: the seal chamber's free walls, and the only
        // places a satellite can hang without standing between the arena and the way down.
        List<GridDir> flanks = perpendicular(dominantDir(exit, boss));
        List<List<GridPos>> free = new ArrayList<>(2);
        for (GridDir flank : flanks) {
            free.add(freeCellsOn(grid, exit, flank));
        }
        boolean firstFree = !free.get(0).isEmpty();
        boolean secondFree = !free.get(1).isEmpty();

        if (wantAcreedor && wantOrden && firstFree && secondFree) {
            // Facing each other across the chamber: temptation on one wall, salvation on the other.
            int acreedorSide = rng.chance(0.5) ? 0 : 1;
            placeSatellite(grid, doors, rng, exit, free.get(acreedorSide), RoomType.DEVIL_DEAL);
            placeSatellite(grid, doors, rng, exit, free.get(1 - acreedorSide), RoomType.ORDEN);
            return;
        }
        // Only one wall is free (measured at ~0.15 % of the deepest floors, 0 % shallow). Whoever
        // is left out must be the one that comes back cheapest: he returns next floor at 95–100 %,
        // and a debt summons him outright — she was earned by how a single floor was played, and
        // that floor is over. So she takes the wall.
        List<GridPos> only = firstFree ? free.get(0) : (secondFree ? free.get(1) : List.of());
        if (only.isEmpty()) {
            return;
        }
        placeSatellite(grid, doors, rng, exit, only,
                wantOrden ? RoomType.ORDEN : RoomType.DEVIL_DEAL);
    }

    /** The empty cells beside the exit on one flank — the seats a satellite may take. */
    private static List<GridPos> freeCellsOn(RoomGrid grid, Room exit, GridDir flank) {
        List<GridPos> cells = new ArrayList<>();
        for (GridPos cell : exit.cells()) {
            GridPos c = cell.step(flank);
            if (grid.inBounds(c) && grid.isEmpty(c)) {
                cells.add(c);
            }
        }
        return cells;
    }

    private static void placeSatellite(RoomGrid grid, List<DoorEdge> doors, SeededRng rng,
                                       Room exit, List<GridPos> seats, RoomType type) {
        Room room = new Room(type, rng.pick(seats), RoomShape.SINGLE);
        grid.place(room);
        DoorEdge door = satelliteDoor(grid, exit, room,
                type == RoomType.ORDEN ? DoorKind.GRACIA : DoorKind.DEVIL);
        if (door != null) {
            doors.add(door);
        }
    }

    /** The single exit↔satellite edge, normalized to EAST/SOUTH so the shipped reveal fits. */
    private static DoorEdge satelliteDoor(RoomGrid grid, Room exit, Room satellite, DoorKind kind) {
        for (GridPos cell : satellite.cells()) {
            for (GridDir dir : List.of(GridDir.EAST, GridDir.SOUTH)) {
                if (grid.roomAt(cell.step(dir)) == exit) {
                    return new DoorEdge(cell, dir, satellite, exit, kind);
                }
            }
        }
        for (GridPos cell : exit.cells()) {
            for (GridDir dir : List.of(GridDir.EAST, GridDir.SOUTH)) {
                if (grid.roomAt(cell.step(dir)) == satellite) {
                    return new DoorEdge(cell, dir, exit, satellite, kind);
                }
            }
        }
        return null;
    }

    private static Room firstOfType(RoomGrid grid, RoomType type) {
        for (Room room : grid.rooms()) {
            if (room.type() == type) {
                return room;
            }
        }
        return null;
    }

    /** The cardinal from {@code from} toward {@code to}, by their cell centroids. */
    private static GridDir dominantDir(Room from, Room to) {
        double dx = avg(to, true) - avg(from, true);
        double dy = avg(to, false) - avg(from, false);
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? GridDir.EAST : GridDir.WEST;
        }
        return dy >= 0 ? GridDir.SOUTH : GridDir.NORTH;
    }

    private static double avg(Room room, boolean x) {
        return room.cells().stream().mapToInt(c -> x ? c.x() : c.y()).average().orElse(0);
    }

    private static List<GridDir> perpendicular(GridDir dir) {
        return dir == GridDir.EAST || dir == GridDir.WEST
                ? List.of(GridDir.NORTH, GridDir.SOUTH)
                : List.of(GridDir.EAST, GridDir.WEST);
    }

    private static GridPos bossEntrance(RoomGrid grid, Room boss) {
        for (GridPos cell : boss.cells()) {
            for (GridDir dir : GridDir.values()) {
                Room neighbor = grid.roomAt(cell.step(dir));
                if (neighbor != null && neighbor != boss) {
                    return cell.step(dir);
                }
            }
        }
        return null;
    }

    private static List<GridPos> quadCells(GridPos anchor) {
        List<GridPos> cells = new ArrayList<>(RoomShape.QUAD.cellCount());
        for (GridPos offset : RoomShape.QUAD.offsets()) {
            cells.add(anchor.offset(offset.x(), offset.y()));
        }
        return cells;
    }

    private static boolean fits(RoomGrid grid, List<GridPos> quad) {
        for (GridPos cell : quad) {
            if (!grid.inBounds(cell) || !grid.isEmpty(cell)) {
                return false;
            }
        }
        return true;
    }

    private static boolean touchesBoss(RoomGrid grid, List<GridPos> quad, Room boss) {
        for (GridPos cell : quad) {
            for (GridDir dir : GridDir.values()) {
                if (grid.roomAt(cell.step(dir)) == boss) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Occupied non-boss cells the quad would share a wall with — each one an unwanted contact. */
    private static int foreignContacts(RoomGrid grid, List<GridPos> quad, Room boss) {
        int contacts = 0;
        for (GridPos cell : quad) {
            for (GridDir dir : GridDir.values()) {
                Room neighbor = grid.roomAt(cell.step(dir));
                if (neighbor != null && neighbor != boss) {
                    contacts++;
                }
            }
        }
        return contacts;
    }

    private static long distanceFrom(GridPos entrance, List<GridPos> quad) {
        if (entrance == null) {
            return 0;
        }
        long distance = 0;
        for (GridPos cell : quad) {
            distance += Math.abs(cell.x() - entrance.x()) + Math.abs(cell.y() - entrance.y());
        }
        return distance;
    }
}
