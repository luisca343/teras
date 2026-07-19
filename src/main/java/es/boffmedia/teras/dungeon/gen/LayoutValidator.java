package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Floor validation, always on. The legacy {@code DungeonValidator} existed but its only call site
 * was commented out, so floors missing their shop — or with rooms reachable only through a secret
 * room — shipped. A floor failing here costs one generation attempt; the generator rerolls.
 *
 * <p>Connectivity is secret-aware: every non-secret room must be reachable from the start without
 * traversing a secret room (players enter those by breaking a wall, so they cannot be load-bearing
 * corridors), and every secret room must touch that reachable set.</p>
 */
final class LayoutValidator {

    private LayoutValidator() {}

    record Result(List<String> errors, List<String> warnings) {
        boolean valid() {
            return errors.isEmpty();
        }
    }

    static Result validate(RoomGrid grid, GenConfig config, int stage,
                           int targetCells, int minDeadEnds) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        checkCounts(grid, errors, warnings, stage, config);
        checkShapes(grid, errors);
        checkConnectivity(grid, errors);

        int deadEnds = grid.deadEndCells().size();
        if (deadEnds < minDeadEnds) {
            errors.add("Insufficient dead ends: " + deadEnds + " (minimum " + minDeadEnds + ")");
        }
        if (grid.occupiedCellCount() < targetCells) {
            warnings.add("Carved " + grid.occupiedCellCount() + " of " + targetCells + " target cells");
        }
        return new Result(errors, warnings);
    }

    private static void checkCounts(RoomGrid grid, List<String> errors, List<String> warnings,
                                    int stage, GenConfig config) {
        int starts = 0;
        int bosses = 0;
        int shops = 0;
        int treasures = 0;
        boolean superSecret = false;
        boolean challenge = false;
        for (Room room : grid.rooms()) {
            switch (room.type()) {
                case START -> starts++;
                case BOSS -> bosses++;
                case SHOP -> shops++;
                case TREASURE -> treasures++;
                case SUPER_SECRET -> superSecret = true;
                case CHALLENGE -> challenge = true;
                default -> { }
            }
        }
        if (starts != 1) {
            errors.add("Expected exactly one START room, found " + starts);
        }
        if (bosses != 1) {
            errors.add("Expected exactly one BOSS room, found " + bosses);
        }
        if (shops == 0) {
            errors.add("Missing SHOP room");
        }
        if (treasures == 0) {
            errors.add("Missing TREASURE room");
        }
        if (shops > 1) {
            warnings.add("Multiple SHOP rooms: " + shops);
        }
        if (!superSecret) {
            warnings.add("Missing SUPER_SECRET room");
        }
        if (stage == config.finalStage() && !challenge) {
            warnings.add("Final stage without CHALLENGE room");
        }
    }

    private static void checkShapes(RoomGrid grid, List<String> errors) {
        for (Room room : grid.rooms()) {
            if (room.type().isSpecial() && !room.isSingle()) {
                errors.add("Special room larger than 1x1: " + room);
            }
        }
    }

    private static void checkConnectivity(RoomGrid grid, List<String> errors) {
        Set<Room> reachable = reachableSkippingSecrets(grid);
        if (reachable.isEmpty()) {
            errors.add("No START room to walk from");
            return;
        }
        for (Room room : grid.rooms()) {
            if (room.type().isSecret()) {
                if (!touchesAny(grid, room, reachable)) {
                    errors.add("Secret room unreachable (no adjacent reachable room): " + room);
                }
            } else if (!reachable.contains(room)) {
                errors.add("Room unreachable without passing through a secret room: " + room);
            }
        }
    }

    private static Set<Room> reachableSkippingSecrets(RoomGrid grid) {
        Set<Room> reachable = new HashSet<>();
        Room start = grid.roomAt(grid.center());
        if (start == null || start.type() != RoomType.START) {
            return reachable;
        }
        Set<GridPos> visited = new HashSet<>();
        ArrayDeque<GridPos> queue = new ArrayDeque<>();
        queue.add(grid.center());
        visited.add(grid.center());
        while (!queue.isEmpty()) {
            GridPos current = queue.poll();
            reachable.add(grid.roomAt(current));
            for (GridDir dir : GridDir.values()) {
                GridPos next = current.step(dir);
                Room room = grid.roomAt(next);
                if (room != null && !room.type().isSecret() && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return reachable;
    }

    private static boolean touchesAny(RoomGrid grid, Room room, Set<Room> reachable) {
        for (GridPos cell : room.cells()) {
            for (GridDir dir : GridDir.values()) {
                Room neighbor = grid.roomAt(cell.step(dir));
                if (neighbor != null && reachable.contains(neighbor)) {
                    return true;
                }
            }
        }
        return false;
    }
}
