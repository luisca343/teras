package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.dungeon.model.SeededRng;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Isaac's floor carve, ported from the legacy {@code NewRoomCarver} with its crash fixed: shape
 * placement now checks bounds before reading any cell (the legacy code read {@code
 * dungeon[y+dy][x+dx]} first, so a large-shape roll on the last row or column threw
 * {@code ArrayIndexOutOfBoundsException} and killed generation).
 *
 * <p>The algorithm: start at the center, expand outward room by room; a frontier cell is skipped
 * when it would touch two existing rooms, then a coin decides expansion — that pairing is what
 * produces the corridor-and-loop floors. Large shapes roll with decaying odds; a fill pass tops up
 * to the target cell count, then dead ends are added until the minimum the special rooms need.</p>
 */
final class RoomCarver {

    private RoomCarver() {}

    /** Large shapes in the legacy try-order; SINGLE is the fallback, not part of the roll. */
    private static final List<RoomShape> LARGE_SHAPES = List.of(
            RoomShape.QUAD, RoomShape.HORIZONTAL, RoomShape.VERTICAL,
            RoomShape.L_TOP_LEFT, RoomShape.L_TOP_RIGHT,
            RoomShape.L_BOTTOM_LEFT, RoomShape.L_BOTTOM_RIGHT);

    static RoomGrid carve(GenConfig config, int targetCells, int minDeadEnds, SeededRng rng) {
        RoomGrid grid = new RoomGrid(config.gridSize());
        Room start = new Room(RoomType.START, grid.center(), RoomShape.SINGLE);
        grid.place(start);

        ShapeOdds odds = new ShapeOdds(config);
        int cellsCarved = 1;
        ArrayDeque<Room> pending = new ArrayDeque<>();

        for (GridPos edge : perimeter(start)) {
            Room created = tryExpand(grid, edge, rng, odds);
            if (created != null) {
                cellsCarved += created.shape().cellCount();
                pending.add(created);
            }
        }

        while (cellsCarved < targetCells && !pending.isEmpty()) {
            Room current = pending.poll();
            for (GridPos edge : perimeter(current)) {
                Room created = tryExpand(grid, edge, rng, odds);
                if (created != null) {
                    cellsCarved += created.shape().cellCount();
                    if (cellsCarved < targetCells) {
                        pending.add(created);
                    }
                }
            }
        }

        while (cellsCarved < targetCells) {
            List<GridPos> spaces = frontierSpaces(grid);
            if (spaces.isEmpty()) {
                break;
            }
            Room created = tryShapes(grid, rng.pick(spaces), rng, odds);
            if (created != null) {
                cellsCarved += created.shape().cellCount();
            }
            if (cellsCarved % config.shapeResetInterval() == 0) {
                odds.reset();
            }
        }

        while (grid.deadEndCells().size() < minDeadEnds) {
            if (!addDeadEnd(grid, rng)) {
                break;
            }
        }

        return grid;
    }

    /**
     * The frontier gate from the legacy carver: the anchor cell must be empty, touch fewer than
     * two rooms, and win a coin toss. Only the anchor is gated — a large shape's other cells may
     * touch more rooms, as in the original.
     */
    private static Room tryExpand(RoomGrid grid, GridPos pos, SeededRng rng, ShapeOdds odds) {
        if (!grid.isEmpty(pos) || grid.occupiedNeighborCount(pos) >= 2 || !rng.chance(0.5)) {
            return null;
        }
        return tryShapes(grid, pos, rng, odds);
    }

    private static Room tryShapes(RoomGrid grid, GridPos anchor, SeededRng rng, ShapeOdds odds) {
        for (RoomShape shape : LARGE_SHAPES) {
            if (rng.chance(odds.of(shape)) && canPlace(grid, anchor, shape)) {
                Room room = new Room(RoomType.NORMAL, anchor, shape);
                grid.place(room);
                odds.decayLarge();
                return room;
            }
        }
        if (canPlace(grid, anchor, RoomShape.SINGLE)) {
            Room room = new Room(RoomType.NORMAL, anchor, RoomShape.SINGLE);
            grid.place(room);
            return room;
        }
        return null;
    }

    private static boolean canPlace(RoomGrid grid, GridPos anchor, RoomShape shape) {
        for (GridPos offset : shape.offsets()) {
            GridPos cell = anchor.offset(offset.x(), offset.y());
            if (!grid.inBounds(cell) || !grid.isEmpty(cell)) {
                return false;
            }
        }
        for (GridPos edge : perimeterAt(anchor, shape)) {
            if (grid.roomAt(edge) != null) {
                return true;
            }
        }
        return false;
    }

    /** Empty cells adjacent to any room that still pass the two-neighbor gate. */
    private static List<GridPos> frontierSpaces(RoomGrid grid) {
        List<GridPos> spaces = new ArrayList<>();
        Set<GridPos> seen = new HashSet<>();
        for (Room room : grid.rooms()) {
            for (GridPos edge : perimeter(room)) {
                if (seen.add(edge) && grid.isEmpty(edge) && grid.occupiedNeighborCount(edge) < 2) {
                    spaces.add(edge);
                }
            }
        }
        return spaces;
    }

    private static boolean addDeadEnd(RoomGrid grid, SeededRng rng) {
        List<GridPos> candidates = new ArrayList<>();
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.size(); x++) {
                GridPos pos = new GridPos(x, y);
                if (grid.isEmpty(pos) && grid.occupiedNeighborCount(pos) == 1) {
                    candidates.add(pos);
                }
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        grid.place(new Room(RoomType.NORMAL, rng.pick(candidates), RoomShape.SINGLE));
        return true;
    }

    private static List<GridPos> perimeter(Room room) {
        return perimeterAt(room.anchor(), room.shape());
    }

    /** Cells orthogonally adjacent to the shape but not part of it, in shape-offset order. */
    private static List<GridPos> perimeterAt(GridPos anchor, RoomShape shape) {
        List<GridPos> body = new ArrayList<>(shape.cellCount());
        for (GridPos offset : shape.offsets()) {
            body.add(anchor.offset(offset.x(), offset.y()));
        }
        Set<GridPos> bodySet = new HashSet<>(body);
        List<GridPos> edges = new ArrayList<>();
        Set<GridPos> seen = new HashSet<>();
        for (GridPos cell : body) {
            for (GridDir dir : GridDir.values()) {
                GridPos edge = cell.step(dir);
                if (!bodySet.contains(edge) && seen.add(edge)) {
                    edges.add(edge);
                }
            }
        }
        return edges;
    }

    /** Large-shape odds with the explicit decay that replaces the legacy accidental normalization. */
    private static final class ShapeOdds {
        private final GenConfig config;
        private double factor = 1.0;

        ShapeOdds(GenConfig config) {
            this.config = config;
        }

        double of(RoomShape shape) {
            double base = switch (shape) {
                case QUAD -> config.chanceQuad();
                case HORIZONTAL -> config.chanceHorizontal();
                case VERTICAL -> config.chanceVertical();
                case L_TOP_LEFT, L_TOP_RIGHT, L_BOTTOM_LEFT, L_BOTTOM_RIGHT -> config.chanceLShape();
                case SINGLE -> 1.0;
            };
            return base * factor;
        }

        void decayLarge() {
            factor *= config.largeShapeDecay();
        }

        void reset() {
            factor = 1.0;
        }
    }
}
