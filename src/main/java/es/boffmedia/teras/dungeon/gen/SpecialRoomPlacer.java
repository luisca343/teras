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
 * BOSS, SUPER_SECRET, SHOP, CURSE?, MINI_BOSS?, CHALLENGE?, TREASURE. The <b>boss takes the farthest
 * dead end outright</b> ({@link #claimBoss}), so it is the deepest point of the playfield and every
 * special sits nearer than it — the 2×2 chamber is kept not by choosing a growable dead end but by
 * the reroll ({@link LayoutValidator#checkBossQuad} rejects a floor whose farthest boss did not
 * grow). The treasure takes the nearest dead end left rather than the next in order, a second guard
 * that it never ends up beyond the boss, and keeps its add-a-dead-end fallback that hands the new
 * room to the boss when it lands farther out. The SECRET room converts the empty cell touching the
 * most rooms.
 *
 * <p>Placement makes no promises: when dead ends run short, rooms are simply not placed (as in
 * legacy, where a floor could silently lack its shop). The validator now runs on every floor and
 * the generator rerolls, which is where the guarantee lives.</p>
 */
final class SpecialRoomPlacer {

    private SpecialRoomPlacer() {}

    static void place(RoomGrid grid, GenConfig config, FloorDepth depth,
                      java.util.Set<RoomShape> shapes, SeededRng rng) {
        Map<GridPos, Integer> distances = grid.distancesFromCenter();
        List<Room> deadEnds = normalDeadEnds(grid, distances);

        Room boss = claimBoss(deadEnds);

        // The treasure's dead end is reserved before anything else is claimed. It is the only
        // special room the validator errors on, so a floor that spent its last dead end on an
        // arcade is not a floor with one room missing — it is a whole layout thrown away and
        // rerolled. The side-room comment below always said the treasure outranks them; the guard
        // said only "if any dead end is left", which is not the same thing once a small floor has
        // exactly as many dead ends as there are rooms wanting one.
        int spendable = deadEnds.size() - 1;

        int index = 0;
        if (index < spendable) {
            deadEnds.get(index++).setType(RoomType.SUPER_SECRET);
        }
        if (index < spendable) {
            deadEnds.get(index++).setType(RoomType.SHOP);
        }
        if (index < spendable && rng.chance(config.curseRoomChance())) {
            deadEnds.get(index++).setType(RoomType.CURSE);
        }
        if (index < spendable && rng.chance(miniBossChance(config, depth))) {
            deadEnds.get(index++).setType(RoomType.MINI_BOSS);
        }
        if (index < spendable && !depth.isFirst() && rng.chance(config.challengeRoomChance())) {
            deadEnds.get(index++).setType(RoomType.CHALLENGE);
        }
        // The three optional side rooms, claimed after the classics and before the treasure so a
        // short floor loses these rather than something the validator requires. Arcade and devil
        // deal wait for stage 2: on the first floor a party has neither the coins to gamble nor
        // the health to sell.
        if (index < spendable && rng.chance(config.sacrificeRoomChance())) {
            deadEnds.get(index++).setType(RoomType.SACRIFICE);
        }
        if (index < spendable && !depth.isFirst() && rng.chance(config.arcadeRoomChance())) {
            deadEnds.get(index++).setType(RoomType.ARCADE);
        }
        // Only when the piso has no sala del sello to hang it off: with an exit, El Acreedor moves
        // to the exit's flank ({@code PostRooms.appendDevilSatellite}) instead of the playfield.
        if (index < spendable && !depth.isFirst() && !config.exitRoom()
                && rng.chance(config.devilDealChance())) {
            deadEnds.get(index++).setType(RoomType.DEVIL_DEAL);
        }

        if (!deadEnds.isEmpty()) {
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
        // Only when this piso actually builds 2x2 rooms. Growing regardless was the bug behind a
        // boss chamber materialising as a void: the room asked for boss_big, the piso had never
        // been required to author it, and the materializer left the cell empty.
        if (shapes.contains(RoomShape.QUAD)) {
            growBossRoom(grid, rng);
        }
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
     * The boss claims the <b>farthest</b> dead end, full stop — so it is the deepest point of the
     * playfield <i>by construction</i>, and every special below draws from a nearer one. Nothing the
     * player can walk to sits beyond the boss (the exit and its satellites are appended after
     * validation, behind the boss on purpose — the reward chamber past it).
     *
     * <p>This replaces an earlier "farthest that can grow its 2×2 chamber" pick, which was a real
     * bug: when the farthest dead end could not grow (measured ~71% of floors), the boss landed on a
     * nearer one and {@code SUPER_SECRET}/{@code SHOP} took the dead ends beyond it. Growability is
     * no longer this method's concern — it belongs to the reroll: a piso that builds 2×2 rooms sets
     * {@link GenConfig#forceBossQuad()}, {@link LayoutValidator#checkBossQuad} rejects a floor whose
     * (now farthest) boss did not grow, and the generator rerolls onto one where it did.
     * {@link #growBossRoom} still grows the pick when it can, and a piso without 2×2 rooms simply
     * keeps a 1×1 boss at the farthest dead end.</p>
     */
    private static Room claimBoss(List<Room> deadEnds) {
        if (deadEnds.isEmpty()) {
            return null;
        }
        Room boss = deadEnds.remove(0);
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

    static double miniBossChance(GenConfig config, FloorDepth depth) {
        return depth.isFirst()
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
