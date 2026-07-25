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
 *
 * <p>Of those two, the dead-end top-up is what decides how big a floor ends up. The carve grows
 * only about one dead end per four cells, so reaching a minimum of six from the ~2.8 it grew
 * itself costs some seven cells of extra corridor — which is why a floor cannot be made smaller
 * by lowering {@code targetCells} alone. The fill honours its target exactly; the top-up is the
 * curve.</p>
 */
final class RoomCarver {

    private RoomCarver() {}

    /**
     * Large shapes biggest-first, which is the legacy try-order and the one that matters: a cell
     * that could hold a 2×2 should be offered the 2×2 first. Within a size the orientations are
     * shuffled per attempt by {@link #tryOrder} — they are rotations of one template with identical
     * odds, so a fixed order is pure bias. Rolling them in list order gave L_TOP_LEFT first refusal
     * on every L and left L_BOTTOM_RIGHT, dead last, four times rarer than its siblings.
     */
    private static final List<RoomShape> TWO_CELL =
            List.of(RoomShape.HORIZONTAL, RoomShape.VERTICAL);
    private static final List<RoomShape> L_SHAPES =
            List.of(RoomShape.L_TOP_LEFT, RoomShape.L_TOP_RIGHT,
                    RoomShape.L_BOTTOM_LEFT, RoomShape.L_BOTTOM_RIGHT);

    private static List<RoomShape> tryOrder(SeededRng rng) {
        List<RoomShape> order = new ArrayList<>(7);
        order.add(RoomShape.QUAD);
        order.addAll(rng.shuffled(TWO_CELL));
        order.addAll(rng.shuffled(L_SHAPES));
        return order;
    }

    static RoomGrid carve(GenConfig config, int targetCells, int minDeadEnds,
                          java.util.Set<RoomShape> shapes, SeededRng rng) {
        RoomGrid grid = new RoomGrid(config.gridSize());
        Room start = new Room(RoomType.START, grid.center(), RoomShape.SINGLE);
        grid.place(start);

        ShapeOdds odds = new ShapeOdds(config, shapes);
        int cellsCarved = 1;
        ArrayDeque<Room> pending = new ArrayDeque<>();

        for (GridPos edge : perimeter(start)) {
            if (cellsCarved >= targetCells) {
                break;
            }
            Room created = tryExpand(grid, edge, rng, odds, targetCells - cellsCarved);
            if (created != null) {
                cellsCarved += created.shape().cellCount();
                pending.add(created);
            }
        }

        while (cellsCarved < targetCells && !pending.isEmpty()) {
            Room current = pending.poll();
            for (GridPos edge : perimeter(current)) {
                if (cellsCarved >= targetCells) {
                    break;
                }
                Room created = tryExpand(grid, edge, rng, odds, targetCells - cellsCarved);
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
            Room created = tryShapes(grid, rng.pick(spaces), rng, odds, targetCells - cellsCarved);
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
     * The frontier gate from the legacy carver: the frontier cell must be empty, touch fewer than
     * two rooms, and win a coin toss. Only that cell is gated — a large shape's other cells may
     * touch more rooms, as in the original.
     */
    private static Room tryExpand(RoomGrid grid, GridPos pos, SeededRng rng, ShapeOdds odds,
                                  int budget) {
        if (!grid.isEmpty(pos) || grid.occupiedNeighborCount(pos) >= 2 || !rng.chance(0.5)) {
            return null;
        }
        return tryShapes(grid, pos, rng, odds, budget);
    }

    /**
     * Rolls each allowed shape in turn and places the first that both wins its roll and fits with
     * {@code seed} as one of its cells, falling back to a single.
     *
     * <p>{@code seed} is the frontier cell the carve is growing from, <b>not</b> the room's anchor.
     * The two used to be the same thing, and that quietly halved the carve: a shape was only ever
     * tried in the one position where its anchor — its minimum corner — landed on the frontier cell,
     * so a 2×1 could only ever extend east from it and never west, a 2×2 only ever south-east. Every
     * large shape therefore grew away from the floor's centre and never back across it. Offering the
     * shape at each of its cells in turn is what makes placement symmetric.</p>
     *
     * <p>It also fixes L_BOTTOM_RIGHT outright. That is the one shape whose minimum corner is the
     * cell it does <i>not</i> own, so anchoring it at the frontier cell demanded a fourth free cell
     * no other shape needed — the sole reason it appeared four times less often.</p>
     */
    private static Room tryShapes(RoomGrid grid, GridPos seed, SeededRng rng, ShapeOdds odds,
                                  int budget) {
        for (RoomShape shape : tryOrder(rng)) {
            // A shape the piso never authored is not rolled for at all, rather than rolled and
            // rejected: the piso has no other piso to borrow the room from.
            if (!odds.allows(shape)) {
                continue;
            }
            // A shape that would spend more cells than the floor has left is not offered, so the
            // fill lands on targetCells rather than up to three cells past it.
            if (shape.cellCount() > budget) {
                continue;
            }
            if (!rng.chance(odds.of(shape))) {
                continue;
            }
            GridPos anchor = anchorFor(grid, seed, shape, rng);
            if (anchor != null) {
                Room room = new Room(RoomType.NORMAL, anchor, shape);
                grid.place(room);
                odds.decayLarge();
                return room;
            }
        }
        if (canPlace(grid, seed, RoomShape.SINGLE)) {
            Room room = new Room(RoomType.NORMAL, seed, RoomShape.SINGLE);
            grid.place(room);
            return room;
        }
        return null;
    }

    /**
     * An anchor that puts {@code shape} over {@code seed} and fits, or null. Candidates are the
     * anchors that make each of the shape's cells the seed cell, tried in shuffled order so no
     * direction is systematically preferred when several work.
     */
    private static GridPos anchorFor(RoomGrid grid, GridPos seed, RoomShape shape, SeededRng rng) {
        for (GridPos offset : rng.shuffled(shape.offsets())) {
            GridPos anchor = seed.offset(-offset.x(), -offset.y());
            if (canPlace(grid, anchor, shape)) {
                return anchor;
            }
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
        private final java.util.Set<RoomShape> allowed;
        private double factor = 1.0;

        ShapeOdds(GenConfig config, java.util.Set<RoomShape> allowed) {
            this.config = config;
            this.allowed = allowed;
        }

        boolean allows(RoomShape shape) {
            return allowed.contains(shape);
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
