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
 * <p>Of those two, the dead-end top-up is what decides how big a floor ends up: the fill honours
 * {@code targetCells} exactly, so every cell past it was added chasing the minimum. That is why the
 * top-up spends its budget carefully ({@link #addDeadEnd}) — when it did not, floor one shipped at
 * 27.6 cells against a budget of 10 and floors two through eight came out within five cells of each
 * other, which made the authored {@code celdas} curve almost inert.</p>
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
                odds.carvedTo(cellsCarved);
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
                    odds.carvedTo(cellsCarved);
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
                odds.carvedTo(cellsCarved);
            }
        }

        while (grid.deadEndCells().size() < minDeadEnds) {
            if (!addDeadEnd(grid, rng)) {
                break;
            }
        }
        if (config.forceBossQuad()) {
            addGrowableBossDeadEnd(grid, rng);
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

    /**
     * One more dead end, spending as few cells as possible to get it.
     *
     * <p>Every candidate is an empty cell with exactly one occupied neighbour, so placing there
     * always makes a dead end — but if that neighbour <i>was itself</i> a dead end it stops being
     * one, and the floor is a cell bigger for nothing. Roughly half of all candidates hang off a
     * dead end, which is why picking uniformly used to spend some eighteen cells reaching a minimum
     * of six: the loop kept paying for exchanges rather than gains.</p>
     *
     * <p>So the gaining candidates are preferred and the neutral ones kept only as a fallback — a
     * floor packed tight enough to offer nothing but exchanges still has to reach its minimum, and
     * an exchange at least moves the dead end somewhere new.</p>
     */
    private static boolean addDeadEnd(RoomGrid grid, SeededRng rng) {
        List<GridPos> candidates = new ArrayList<>();
        List<GridPos> gains = new ArrayList<>();
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.size(); x++) {
                GridPos pos = new GridPos(x, y);
                if (!grid.isEmpty(pos) || grid.occupiedNeighborCount(pos) != 1) {
                    continue;
                }
                candidates.add(pos);
                if (!attachesToDeadEnd(grid, pos)) {
                    gains.add(pos);
                }
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        grid.place(new Room(RoomType.NORMAL, rng.pick(gains.isEmpty() ? candidates : gains),
                RoomShape.SINGLE));
        return true;
    }

    /** Whether the one room touching {@code pos} is a 1×1 dead end that building there would end. */
    private static boolean attachesToDeadEnd(RoomGrid grid, GridPos pos) {
        for (GridDir dir : GridDir.values()) {
            Room neighbor = grid.roomAt(pos.step(dir));
            if (neighbor != null) {
                return neighbor.isSingle() && grid.occupiedNeighborCount(neighbor.anchor()) == 1;
            }
        }
        return false;
    }

    /**
     * Guarantees the floor's <b>farthest</b> dead end can grow into the 2×2 boss chamber, by adding
     * one that can when the floor did not already end in one.
     *
     * <p>The boss claims the farthest dead end outright ({@code SpecialRoomPlacer.claimBoss}) and a
     * piso that ships {@code boss_big} then requires it to be a 2×2 — but a dead end grown by the
     * carve has 2×2 room around it only about 29% of the time, so the reroll loop was throwing away
     * roughly three and a half complete floors for every one it kept. That is a whole floor
     * re-carved to re-roll one local property.</p>
     *
     * <p>Adding the room here instead costs a single cell and settles it before validation. The cell
     * must be a dead end in its own right (empty, exactly one occupied neighbour), must have a free
     * 2×2 around it with exactly one contact from outside — the same test {@code
     * SpecialRoomPlacer.isGrowable} applies later, so what is promised here is what is checked
     * there — and must sit <b>strictly</b> farther from the start than every existing dead end, so
     * the farthest-first sort cannot hand the boss to anything else.</p>
     *
     * <p>It can never cost the floor its minimum: the new room is a dead end, and the only dead end
     * it can end is the neighbour it attached to, so the count moves by +1 or 0 and never down.
     * When no candidate qualifies nothing is placed and the reroll still covers it.</p>
     */
    private static void addGrowableBossDeadEnd(RoomGrid grid, SeededRng rng) {
        List<GridPos> deadEnds = grid.deadEndCells();
        java.util.Map<GridPos, Integer> distances = grid.distancesFromCenter();
        int farthest = 0;
        for (GridPos pos : deadEnds) {
            farthest = Math.max(farthest, distances.getOrDefault(pos, 0));
        }
        List<GridPos> deepest = new ArrayList<>();
        for (GridPos pos : deadEnds) {
            if (distances.getOrDefault(pos, 0) == farthest) {
                deepest.add(pos);
            }
        }
        // A tie at the farthest distance is not good enough: the sort breaks ties by grid order, so
        // the boss could still land on the one that cannot grow. Only a lone growable farthest is.
        if (deepest.size() == 1
                && hasGrowableQuad(grid, deepest.get(0), grid.roomAt(deepest.get(0)))) {
            return;
        }
        List<GridPos> candidates = new ArrayList<>();
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.size(); x++) {
                GridPos pos = new GridPos(x, y);
                if (!grid.isEmpty(pos) || grid.occupiedNeighborCount(pos) != 1) {
                    continue;
                }
                if (distanceOf(grid, distances, pos) <= farthest) {
                    continue;
                }
                if (hasGrowableQuad(grid, pos, null)) {
                    candidates.add(pos);
                }
            }
        }
        if (!candidates.isEmpty()) {
            grid.place(new Room(RoomType.NORMAL, rng.pick(candidates), RoomShape.SINGLE));
        }
    }

    /** How deep an empty cell would sit: one step past the single room it touches. */
    private static int distanceOf(RoomGrid grid, java.util.Map<GridPos, Integer> distances,
                                  GridPos pos) {
        for (GridDir dir : GridDir.values()) {
            GridPos neighbor = pos.step(dir);
            if (grid.roomAt(neighbor) != null) {
                Integer distance = distances.get(neighbor);
                return distance == null ? -1 : distance + 1;
            }
        }
        return -1;
    }

    /**
     * Whether a 1×1 room at {@code cell} could grow into a 2×2 — every cell of some quad in bounds
     * and free (bar {@code room} itself, when the cell already holds one), and exactly one occupied
     * cell touching that quad from outside.
     *
     * <p>The same test {@code SpecialRoomPlacer.isGrowable} runs later, deliberately: what the carve
     * promises has to be what placement checks, or the guarantee is a guess.</p>
     */
    private static boolean hasGrowableQuad(RoomGrid grid, GridPos cell, Room room) {
        for (GridPos anchor : List.of(cell, cell.offset(-1, 0), cell.offset(0, -1),
                cell.offset(-1, -1))) {
            List<GridPos> quad = new ArrayList<>(RoomShape.QUAD.cellCount());
            boolean free = true;
            for (GridPos offset : RoomShape.QUAD.offsets()) {
                GridPos quadCell = anchor.offset(offset.x(), offset.y());
                quad.add(quadCell);
                Room occupant = grid.roomAt(quadCell);
                if (!grid.inBounds(quadCell) || (occupant != null && occupant != room)) {
                    free = false;
                    break;
                }
            }
            if (free && grid.externalNeighborCount(quad) == 1) {
                return true;
            }
        }
        return false;
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
        private int lastReset = 1;

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

        /**
         * Told the running cell count; clears the decay once {@code shapeResetInterval} cells have
         * been carved since it last did.
         *
         * <p>The counter is the point. This used to be {@code cellsCarved % interval == 0}, tested
         * in the fill pass alone — a two- or three-cell shape steps straight over the multiple, and
         * most floors never reach that pass at all, so the reset all but never fired. The decay
         * therefore ran unbroken across the whole carve: after five large rooms it stands at 3% of
         * the configured odds, which is why large rooms clustered near the start of a floor and were
         * effectively absent from the rest of it.</p>
         */
        void carvedTo(int cells) {
            if (cells - lastReset >= config.shapeResetInterval()) {
                lastReset = cells;
                factor = 1.0;
            }
        }
    }
}
