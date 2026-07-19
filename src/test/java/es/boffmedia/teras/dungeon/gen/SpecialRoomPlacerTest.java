package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.dungeon.model.SeededRng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialRoomPlacerTest {

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
        assertEquals(0.4375, SpecialRoomPlacer.miniBossChance(config, 1), 1e-9);
        assertEquals(0.25, SpecialRoomPlacer.miniBossChance(config, 2), 1e-9);
    }

    @Test
    void placeAssignsBossFarthestAndRequiredRooms() {
        GenConfig config = GenConfig.defaults();
        SeededRng rng = new SeededRng(7);
        RoomGrid grid = RoomCarver.carve(config, 22, 6, rng);
        SpecialRoomPlacer.place(grid, config, 3, rng);

        assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.BOSS));
        assertTrue(grid.rooms().stream().anyMatch(r -> r.type() == RoomType.SHOP)
                || grid.rooms().stream().anyMatch(r -> r.type() == RoomType.TREASURE));
    }
}
