package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.dungeon.model.SeededRng;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialRoomPlacerTest {

    /** Every shape: these predate the piso opt-out and must keep their old behaviour. */
    private static final java.util.Set<RoomShape> ALL_SHAPES =
            java.util.EnumSet.allOf(RoomShape.class);


    /**
     * Only (2,2) touches three rooms; every other empty cell touches at most one. A one-neighbor
     * candidate can score at most 10+4−6=8 against the three-neighbor minimum of 10, so the choice
     * is deterministic whatever the rng draws.
     */
    private RoomGrid gridWithOneThreeNeighborHole() {
        RoomGrid grid = new RoomGrid(7);
        grid.place(new Room(RoomType.START, new GridPos(3, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(2, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(1, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(1, 2), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(3, 2), RoomShape.SINGLE));
        return grid;
    }

    @Test
    void secretRoomPrefersTheCellTouchingMostRooms() {
        RoomGrid grid = gridWithOneThreeNeighborHole();
        SpecialRoomPlacer.placeSecretRoom(grid, new SeededRng(42));

        Room secret = grid.roomAt(new GridPos(2, 2));
        assertNotNull(secret);
        assertEquals(RoomType.SECRET, secret.type());
    }

    @Test
    void secretRoomNeverTouchesTheBoss() {
        RoomGrid grid = gridWithOneThreeNeighborHole();
        grid.roomAt(new GridPos(3, 2)).setType(RoomType.BOSS);
        SpecialRoomPlacer.placeSecretRoom(grid, new SeededRng(42));

        Room secret = grid.rooms().stream()
                .filter(r -> r.type() == RoomType.SECRET)
                .findFirst().orElse(null);
        assertNotNull(secret);
        assertNotEquals(new GridPos(2, 2), secret.anchor());
    }

    @Test
    void miniBossChanceIsBoostedOnStageOneOnly() {
        GenConfig config = GenConfig.defaults();
        assertEquals(0.4375, SpecialRoomPlacer.miniBossChance(config, FloorDepth.of(config, 1)), 1e-9);
        assertEquals(0.25, SpecialRoomPlacer.miniBossChance(config, FloorDepth.of(config, 2)), 1e-9);
    }

    @Test
    void placeAssignsBossFarthestAndRequiredRooms() {
        GenConfig config = GenConfig.defaults();
        for (int seed = 0; seed < 200; seed++) {
            SeededRng rng = new SeededRng(seed);
            RoomGrid grid = RoomCarver.carve(config, 22, 6, ALL_SHAPES, rng);
            SpecialRoomPlacer.place(grid, config, FloorDepth.of(config, 3), ALL_SHAPES, rng);

            Room boss = firstOfType(grid, RoomType.BOSS);
            assertNotNull(boss, "seed " + seed + " placed no boss");
            assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.SHOP),
                    "seed " + seed + " placed no shop");
            assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.TREASURE),
                    "seed " + seed + " placed no treasure");

            // The invariant the feature name always claimed but never checked: no special
            // destination sits beyond the boss. Every special room is a 1×1 dead end, and the boss
            // claims the farthest dead end, so they are all nearer by construction — this is the bug
            // that is fixed (SUPER_SECRET/SHOP used to take the dead ends beyond a non-growable
            // boss). NORMAL rooms are maze, not destinations: a multi-cell one can incidentally poke
            // a cell deeper, which is harmless and measured separately. The exit and its satellites
            // do not exist yet — they are appended after validation, behind the boss.
            Map<GridPos, Integer> distances = grid.distancesFromCenter();
            int bossDistance = maxDistance(boss, distances);
            for (Room room : grid.rooms()) {
                if (room == boss || room.type().isSecret() || !room.type().isSpecial()) {
                    continue;
                }
                assertTrue(maxDistance(room, distances) <= bossDistance,
                        "seed " + seed + ": " + room.type() + " sits beyond the boss");
            }
        }
    }

    private static Room firstOfType(RoomGrid grid, RoomType type) {
        return grid.rooms().stream().filter(r -> r.type() == type).findFirst().orElse(null);
    }

    /** A room's deepest cell from the start; 0 for a room the distance map does not reach. */
    private static int maxDistance(Room room, Map<GridPos, Integer> distances) {
        int max = 0;
        for (GridPos cell : room.cells()) {
            max = Math.max(max, distances.getOrDefault(cell, 0));
        }
        return max;
    }

    /** A corridor of rooms ending in a clear corner: the chamber has somewhere to go. */
    @Test
    void bossGrowsIntoAQuadWhenTheSpaceIsClear() {
        RoomGrid grid = new RoomGrid(9);
        grid.place(new Room(RoomType.START, new GridPos(4, 4), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(4, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.BOSS, new GridPos(4, 2), RoomShape.SINGLE));

        SpecialRoomPlacer.growBossRoom(grid, new SeededRng(1));

        Room grown = grid.roomAt(new GridPos(4, 2));
        assertEquals(RoomShape.QUAD, grown.shape());
        assertEquals(RoomType.BOSS, grown.type());
        assertEquals(1, grid.externalNeighborCount(grown));
    }

    /** Boxed in on both sides: every quad would open a second door, so it stays 1x1 rather than. */
    @Test
    void bossStaysSingleWhenEveryQuadWouldAddADoor() {
        RoomGrid grid = new RoomGrid(9);
        grid.place(new Room(RoomType.START, new GridPos(4, 4), RoomShape.SINGLE));
        grid.place(new Room(RoomType.BOSS, new GridPos(4, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(3, 2), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(5, 2), RoomShape.SINGLE));

        SpecialRoomPlacer.growBossRoom(grid, new SeededRng(1));

        assertTrue(grid.roomAt(new GridPos(4, 3)).isSingle());
    }

    /**
     * A quad cell laid against the SUPER_SECRET would be a hidden second door into the boss room —
     * and {@code occupiedNeighborCount} cannot see it, which is why growth reads occupancy raw.
     */
    @Test
    void bossNeverGrowsAgainstTheSuperSecretRoom() {
        RoomGrid grid = new RoomGrid(9);
        grid.place(new Room(RoomType.START, new GridPos(4, 4), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(4, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.BOSS, new GridPos(4, 2), RoomShape.SINGLE));
        // Beside every cell the chamber could claim, in all four candidate quads.
        grid.place(new Room(RoomType.SUPER_SECRET, new GridPos(3, 1), RoomShape.SINGLE));
        grid.place(new Room(RoomType.SUPER_SECRET, new GridPos(5, 1), RoomShape.SINGLE));

        SpecialRoomPlacer.growBossRoom(grid, new SeededRng(1));

        assertTrue(grid.roomAt(new GridPos(4, 2)).isSingle(),
                "grew against a secret room, giving the boss a hidden second door");
    }

    /** The chamber must never cost the floor its boss-is-deepest ordering. */
    @Test
    void theGrownBossIsStillNoNearerThanTheTreasure() {
        GenConfig config = GenConfig.defaults();
        for (int seed = 0; seed < 40; seed++) {
            SeededRng rng = new SeededRng(seed);
            RoomGrid grid = RoomCarver.carve(config, 22, 7, ALL_SHAPES, rng);
            SpecialRoomPlacer.place(grid, config, FloorDepth.of(config, 3), ALL_SHAPES, rng);

            var distances = grid.distancesFromCenter();
            Room boss = grid.rooms().stream()
                    .filter(r -> r.type() == RoomType.BOSS).findFirst().orElseThrow();
            Room treasure = grid.rooms().stream()
                    .filter(r -> r.type() == RoomType.TREASURE).findFirst().orElse(null);
            if (treasure == null) {
                continue;
            }
            assertTrue(nearestDistance(distances, boss) >= nearestDistance(distances, treasure),
                    "seed " + seed + ": treasure ended up farther than the boss");
        }
    }

    private int nearestDistance(java.util.Map<GridPos, Integer> distances, Room room) {
        return room.cells().stream()
                .mapToInt(c -> distances.getOrDefault(c, Integer.MAX_VALUE))
                .min().orElseThrow();
    }

    // --- the optional side rooms ---------------------------------------------------------------

    private GenConfig withSideRoomChances(double chance) {
        GenConfig d = GenConfig.defaults();
        return new GenConfig(d.gridSize(),
                d.chanceQuad(), d.chanceHorizontal(), d.chanceVertical(), d.chanceLShape(),
                d.largeShapeDecay(), d.shapeResetInterval(),
                d.curseRoomChance(), d.challengeRoomChance(),
                chance, chance, chance,
                d.miniBossChance(), d.firstStageMiniBossBoost(),
                d.labyrinthMultiplier(), d.labyrinthRoomCap(), d.lostRoomBonus(),
                d.celdas(), d.jitter(), d.maxAttempts(), d.exitRoom(),
                d.postMargin(), d.forceBossQuad());
    }

    @Test
    void sideRoomsAppearWhenTheirChanceIsCertain() {
        GenConfig config = withSideRoomChances(1.0);
        SeededRng rng = new SeededRng(11);
        RoomGrid grid = RoomCarver.carve(config, 30, 6, ALL_SHAPES, rng);
        SpecialRoomPlacer.place(grid, config, FloorDepth.of(config, 4), ALL_SHAPES, rng);

        assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.SACRIFICE));
        assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.ARCADE));
        assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.DEVIL_DEAL));
    }

    /** They are optional, so a floor that rolls none of them must still be a complete floor. */
    @Test
    void sideRoomsAreAbsentAtZeroChanceAndTheRequiredOnesStillPlace() {
        GenConfig config = withSideRoomChances(0.0);
        SeededRng rng = new SeededRng(11);
        RoomGrid grid = RoomCarver.carve(config, 30, 6, ALL_SHAPES, rng);
        SpecialRoomPlacer.place(grid, config, FloorDepth.of(config, 4), ALL_SHAPES, rng);

        assertTrue(grid.rooms().stream().noneMatch(r -> r.type() == RoomType.SACRIFICE));
        assertTrue(grid.rooms().stream().noneMatch(r -> r.type() == RoomType.ARCADE));
        assertTrue(grid.rooms().stream().noneMatch(r -> r.type() == RoomType.DEVIL_DEAL));
        assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.BOSS));
        assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.TREASURE));
    }

    /** The arcade and the devil deal are stage-2+: a first floor has neither coins nor health to spare. */
    @Test
    void arcadeAndDevilDealNeverAppearOnTheFirstStage() {
        GenConfig config = withSideRoomChances(1.0);
        for (int seed = 0; seed < 20; seed++) {
            SeededRng rng = new SeededRng(seed);
            RoomGrid grid = RoomCarver.carve(config, 25, 1, ALL_SHAPES, rng);
            SpecialRoomPlacer.place(grid, config, FloorDepth.of(config, 1), ALL_SHAPES, rng);

            assertTrue(grid.rooms().stream().noneMatch(r -> r.type() == RoomType.ARCADE),
                    "seed " + seed + ": arcade placed on stage 1");
            assertTrue(grid.rooms().stream().noneMatch(r -> r.type() == RoomType.DEVIL_DEAL),
                    "seed " + seed + ": devil deal placed on stage 1");
        }
    }

    @Test
    void placementStaysDeterministicForASeed() {
        GenConfig config = withSideRoomChances(0.5);
        RoomGrid first = RoomCarver.carve(config, 28, 5, ALL_SHAPES, new SeededRng(99));
        SpecialRoomPlacer.place(first, config, FloorDepth.of(config, 5), ALL_SHAPES, new SeededRng(1234));
        RoomGrid second = RoomCarver.carve(config, 28, 5, ALL_SHAPES, new SeededRng(99));
        SpecialRoomPlacer.place(second, config, FloorDepth.of(config, 5), ALL_SHAPES, new SeededRng(1234));

        assertEquals(LayoutAscii.render(first), LayoutAscii.render(second));
    }
}
