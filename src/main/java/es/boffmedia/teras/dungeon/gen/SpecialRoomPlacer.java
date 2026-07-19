package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.dungeon.model.SeededRng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Special-room placement on carved floors, ported from the legacy {@code
 * DungeonGenerator.placeSpecialRooms}: 1×1 NORMAL dead ends are claimed farthest-first in the order
 * BOSS, SUPER_SECRET, SHOP, CURSE?, MINI_BOSS?, CHALLENGE?, TREASURE. Two departures from legacy,
 * both from the 2×2 boss chamber: the boss takes the farthest dead end its chamber actually
 * <i>fits</i> into ({@link #claimBoss}), and the treasure takes the nearest one left rather than
 * the next in order, so it can never end up beyond a boss no longer chosen by distance. Treasure
 * keeps its add-a-dead-end fallback that hands the new room to the boss instead when it lands
 * farther out. The SECRET room converts the empty cell touching the most rooms.
 *
 * <p>Placement makes no promises: when dead ends run short, rooms are simply not placed (as in
 * legacy, where a floor could silently lack its shop). The validator now runs on every floor and
 * the generator rerolls, which is where the guarantee lives.</p>
 */
final class SpecialRoomPlacer {

    private SpecialRoomPlacer() {}

    static void place(RoomGrid grid, GenConfig config, int stage, SeededRng rng) {
        Map<GridPos, Integer> distances = grid.distancesFromCenter();
        List<Room> deadEnds = normalDeadEnds(grid, distances);

        Room boss = claimBoss(grid, deadEnds);

        int index = 0;
        if (index < deadEnds.size()) {
            deadEnds.get(index++).setType(RoomType.SUPER_SECRET);
        }
        if (index < deadEnds.size()) {
            deadEnds.get(index++).setType(RoomType.SHOP);
        }
        if (index < deadEnds.size() && rng.chance(config.curseRoomChance())) {
            deadEnds.get(index++).setType(RoomType.CURSE);
        }
        if (index < deadEnds.size() && rng.chance(miniBossChance(config, stage))) {
            deadEnds.get(index++).setType(RoomType.MINI_BOSS);
        }
        if (index < deadEnds.size() && stage > 1 && rng.chance(config.challengeRoomChance())) {
            deadEnds.get(index++).setType(RoomType.CHALLENGE);
        }

        if (index < deadEnds.size()) {
            // The nearest unclaimed dead end, not the next one in order. Sequential worked only
            // while the boss was guaranteed to be dead end 0; now that it is chosen by chamber
            // space, taking the next in a farthest-first list can hand the treasure a room beyond
            // the boss. Nearest-last also reads better: the boss is the far end of the floor.
            deadEnds.get(deadEnds.size() - 1).setType(RoomType.TREASURE);
        } else {
            Room added = addDeadEndByNormal(grid, rng);
            if (added != null) {
                int addedDistance = grid.distancesFromCenter().getOrDefault(added.anchor(), 0);
                int bossDistance = boss == null ? Integer.MAX_VALUE
                        : distances.getOrDefault(boss.anchor(), 0);
                if (addedDistance > bossDistance) {
                    boss.setType(RoomType.TREASURE);
                    added.setType(RoomType.BOSS);
                } else {
                    added.setType(RoomType.TREASURE);
                }
            }
        }

        keepBossBeyondTreasure(grid);
        growBossRoom(grid, rng);
        placeSecretRoom(grid, rng);
    }

    /**
     * Last-resort guarantee that the treasure never sits beyond the boss. Taking the treasure from
     * the nearest dead end covers the ordinary case; this catches the one it cannot — a floor where
     * the only dead end with room for the chamber happens to be the nearest one, so every remaining
     * candidate is farther out.
     *
     * <p>Swapping the two types is the same correction the treasure fallback already applies to its
     * added dead end. It can leave the boss on a room that has no chamber space, which is why
     * {@link #growBossRoom} re-checks rather than trusting the pick.</p>
     */
    private static void keepBossBeyondTreasure(RoomGrid grid) {
        Room boss = firstOfType(grid, RoomType.BOSS);
        Room treasure = firstOfType(grid, RoomType.TREASURE);
        if (boss == null || treasure == null) {
            return;
        }
        Map<GridPos, Integer> distances = grid.distancesFromCenter();
        int bossDistance = distances.getOrDefault(boss.anchor(), 0);
        int treasureDistance = distances.getOrDefault(treasure.anchor(), 0);
        if (treasureDistance > bossDistance) {
            boss.setType(RoomType.TREASURE);
            treasure.setType(RoomType.BOSS);
        }
    }

    private static Room firstOfType(RoomGrid grid, RoomType type) {
        for (Room room : grid.rooms()) {
            if (room.type() == type) {
                return room;
            }
        }
        return null;
    }

    /**
     * Grows the boss room into a 2×2 when the floor has room for it — Isaac's boss chamber is the
     * one oversized room on the map. Runs after every retype above (the treasure fallback can still
     * hand BOSS to a different room) and before the secret room, so {@link #placeSecretRoom} sees
     * the final footprint and keeps its distance from all four cells.
     *
     * <p>A candidate quad must leave the room with exactly one door: the three new cells have to be
     * empty and touch nothing outside the quad. Occupancy is read raw through {@code roomAt}, not
     * through {@link RoomGrid#occupiedNeighborCount} — that one skips secret rooms, and a quad cell
     * laid against the SUPER_SECRET would hand the boss a hidden second entrance, the same trap
     * {@link #addDeadEndByNormal} already guards against.</p>
     *
     * <p>Because none of the new cells touches anything, no other cell's neighbor count moves:
     * growth costs exactly one dead end (the boss's own) and changes nothing else on the floor.
     * That is also why the three consumed cells were never viable SECRET candidates.</p>
     *
     * <p>No valid quad means the boss stays 1×1. That floor is still perfectly playable, so this
     * never costs a generation attempt.</p>
     */
    static void growBossRoom(RoomGrid grid, SeededRng rng) {
        Room boss = null;
        for (Room room : grid.rooms()) {
            if (room.type() == RoomType.BOSS) {
                boss = room;
                break;
            }
        }
        if (boss == null || !boss.isSingle()) {
            return;
        }
        List<GridPos> anchors = growableAnchors(grid, boss);
        if (anchors.isEmpty()) {
            return;
        }
        grid.replace(boss, new Room(RoomType.BOSS, rng.pick(anchors), RoomShape.QUAD));
    }

    /**
     * The boss claims the farthest dead end <b>that has room for its chamber</b>, rather than the
     * farthest outright. Measured over 400 floors, the farthest dead end could grow on only 29% of
     * them — but on 94% <i>some</i> dead end could, so insisting on the farthest bought nothing and
     * cost the chamber three times out of four. Picking this way lands it at 92–95% across stages.
     * It still comes from the same farthest-first list, so the boss stays deep in the floor.
     *
     * <p>Growability is re-checked at growth time: the treasure fallback can add a dead end that
     * spoils the pick, and a boss that ends up 1×1 is a worse floor, not a broken one.</p>
     */
    private static Room claimBoss(RoomGrid grid, List<Room> deadEnds) {
        if (deadEnds.isEmpty()) {
            return null;
        }
        int chosen = 0;
        for (int i = 0; i < deadEnds.size(); i++) {
            if (!growableAnchors(grid, deadEnds.get(i)).isEmpty()) {
                chosen = i;
                break;
            }
        }
        Room boss = deadEnds.remove(chosen);
        boss.setType(RoomType.BOSS);
        return boss;
    }

    /**
     * Every 2×2 anchor {@code room} could expand into: all four cells in bounds, the three new ones
     * empty, and exactly one occupied cell touching the quad from outside.
     *
     * <p>Occupancy is read raw through {@code roomAt}, never through
     * {@link RoomGrid#occupiedNeighborCount} — that one skips secret rooms, and a quad cell laid
     * against the SUPER_SECRET would hand the boss a hidden second entrance, the same trap
     * {@link #addDeadEndByNormal} already guards against.</p>
     */
    private static List<GridPos> growableAnchors(RoomGrid grid, Room room) {
        GridPos cell = room.anchor();
        List<GridPos> anchors = new ArrayList<>(4);
        // The room's cell as the quad's top-left, top-right, bottom-left, then bottom-right.
        for (GridPos anchor : List.of(cell, cell.offset(-1, 0), cell.offset(0, -1), cell.offset(-1, -1))) {
            if (isGrowable(grid, quadCells(anchor), room)) {
                anchors.add(anchor);
            }
        }
        return anchors;
    }

    private static List<GridPos> quadCells(GridPos anchor) {
        List<GridPos> cells = new ArrayList<>(RoomShape.QUAD.cellCount());
        for (GridPos offset : RoomShape.QUAD.offsets()) {
            cells.add(anchor.offset(offset.x(), offset.y()));
        }
        return cells;
    }

    private static boolean isGrowable(RoomGrid grid, List<GridPos> quad, Room room) {
        if (!room.isSingle()) {
            return false;
        }
        for (GridPos cell : quad) {
            if (!grid.inBounds(cell)) {
                return false;
            }
            Room occupant = grid.roomAt(cell);
            if (occupant != null && occupant != room) {
                return false;
            }
        }
        // Re-proved rather than inherited from "the boss claimed a dead end": if that ever stops
        // holding, refusing to grow beats shipping a boss room with two ways in.
        return grid.externalNeighborCount(quad) == 1;
    }

    static double miniBossChance(GenConfig config, int stage) {
        return stage == 1
                ? config.miniBossChance() + config.firstStageMiniBossBoost() * config.miniBossChance()
                : config.miniBossChance();
    }

    /** 1×1 NORMAL dead ends, farthest from the start first; ties broken by grid order. */
    private static List<Room> normalDeadEnds(RoomGrid grid, Map<GridPos, Integer> distances) {
        List<Room> deadEnds = new ArrayList<>();
        for (GridPos pos : grid.deadEndCells()) {
            Room room = grid.roomAt(pos);
            if (room.type() == RoomType.NORMAL) {
                deadEnds.add(room);
            }
        }
        deadEnds.sort(Comparator
                .comparingInt((Room r) -> distances.getOrDefault(r.anchor(), 0)).reversed()
                .thenComparingInt(r -> r.anchor().y())
                .thenComparingInt(r -> r.anchor().x()));
        return deadEnds;
    }

    /**
     * The treasure fallback: a fresh dead end whose one neighbor is a NORMAL room. Secret-adjacent
     * cells are rejected outright — neighbor counts exclude secret rooms, so without this a
     * "dead end" could sit beside the SUPER_SECRET room and the boss-swap below would hand the
     * boss a hidden door.
     */
    private static Room addDeadEndByNormal(RoomGrid grid, SeededRng rng) {
        List<GridPos> candidates = new ArrayList<>();
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.size(); x++) {
                GridPos pos = new GridPos(x, y);
                if (!grid.isEmpty(pos) || grid.occupiedNeighborCount(pos) != 1) {
                    continue;
                }
                boolean normalNeighbor = false;
                boolean secretNeighbor = false;
                for (GridDir dir : GridDir.values()) {
                    Room neighbor = grid.roomAt(pos.step(dir));
                    if (neighbor == null) {
                        continue;
                    }
                    if (neighbor.type().isSecret()) {
                        secretNeighbor = true;
                    } else if (neighbor.type() == RoomType.NORMAL) {
                        normalNeighbor = true;
                    }
                }
                if (normalNeighbor && !secretNeighbor) {
                    candidates.add(pos);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Room room = new Room(RoomType.NORMAL, rng.pick(candidates), RoomShape.SINGLE);
        grid.place(room);
        return room;
    }

    /**
     * Converts the best empty cell into the SECRET room: base weight 10 plus a small roll,
     * penalized for touching fewer rooms, never beside the boss or another secret. First
     * strict maximum wins so the choice is deterministic.
     */
    static void placeSecretRoom(RoomGrid grid, SeededRng rng) {
        GridPos best = null;
        double bestWeight = Double.NEGATIVE_INFINITY;
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.size(); x++) {
                GridPos pos = new GridPos(x, y);
                if (!grid.isEmpty(pos)) {
                    continue;
                }
                int neighbors = grid.occupiedNeighborCount(pos);
                if (neighbors == 0 || adjacentToBossOrSecret(grid, pos)) {
                    continue;
                }
                double weight = 10 + rng.between(0, 4);
                if (neighbors == 2) {
                    weight -= 3;
                }
                if (neighbors == 1) {
                    weight -= 6;
                }
                if (weight > bestWeight) {
                    bestWeight = weight;
                    best = pos;
                }
            }
        }
        if (best != null) {
            grid.place(new Room(RoomType.SECRET, best, RoomShape.SINGLE));
        }
    }

    private static boolean adjacentToBossOrSecret(RoomGrid grid, GridPos pos) {
        for (GridDir dir : GridDir.values()) {
            Room neighbor = grid.roomAt(pos.step(dir));
            if (neighbor != null && (neighbor.type() == RoomType.BOSS || neighbor.type().isSecret())) {
                return true;
            }
        }
        return false;
    }
}
