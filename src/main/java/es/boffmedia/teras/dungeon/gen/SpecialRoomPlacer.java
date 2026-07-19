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
 * DungeonGenerator.placeSpecialRooms}: 1×1 NORMAL dead ends are claimed farthest-first (boss gets
 * the farthest), in the order BOSS, SUPER_SECRET, SHOP, CURSE?, MINI_BOSS?, CHALLENGE?, TREASURE —
 * treasure keeps its add-a-dead-end fallback that hands the new room to the boss instead when it
 * lands farther out. The SECRET room converts the empty cell touching the most rooms.
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

        int index = 0;
        Room boss = null;
        if (index < deadEnds.size()) {
            boss = deadEnds.get(index++);
            boss.setType(RoomType.BOSS);
        }
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
            deadEnds.get(index).setType(RoomType.TREASURE);
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

        placeSecretRoom(grid, rng);
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
