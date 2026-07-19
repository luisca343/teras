package es.boffmedia.teras.dungeon.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomGridTest {

    @Test
    void multiCellRoomOccupiesAllItsCellsAsOneObject() {
        RoomGrid grid = new RoomGrid(7);
        Room quad = new Room(RoomType.NORMAL, new GridPos(1, 1), RoomShape.QUAD);
        grid.place(quad);

        assertSame(quad, grid.roomAt(new GridPos(1, 1)));
        assertSame(quad, grid.roomAt(new GridPos(2, 1)));
        assertSame(quad, grid.roomAt(new GridPos(1, 2)));
        assertSame(quad, grid.roomAt(new GridPos(2, 2)));
        assertNull(grid.roomAt(new GridPos(3, 3)));
        assertEquals(4, grid.occupiedCellCount());
    }

    @Test
    void placingOverAnOccupiedCellThrows() {
        RoomGrid grid = new RoomGrid(7);
        grid.place(new Room(RoomType.NORMAL, new GridPos(2, 2), RoomShape.SINGLE));
        Room overlapping = new Room(RoomType.NORMAL, new GridPos(1, 2), RoomShape.HORIZONTAL);
        assertThrows(IllegalArgumentException.class, () -> grid.place(overlapping));
    }

    @Test
    void placingOutOfBoundsThrows() {
        RoomGrid grid = new RoomGrid(5);
        Room outside = new Room(RoomType.NORMAL, new GridPos(4, 4), RoomShape.QUAD);
        assertThrows(IllegalArgumentException.class, () -> grid.place(outside));
    }

    @Test
    void neighborCountsExcludeSecretRooms() {
        RoomGrid grid = new RoomGrid(7);
        grid.place(new Room(RoomType.NORMAL, new GridPos(2, 2), RoomShape.SINGLE));
        grid.place(new Room(RoomType.SECRET, new GridPos(3, 2), RoomShape.SINGLE));

        assertEquals(0, grid.occupiedNeighborCount(new GridPos(3, 3)));
        assertEquals(1, grid.occupiedNeighborCount(new GridPos(2, 3)));
    }

    @Test
    void deadEndsAreSingleCellRoomsWithOneNeighborRegardlessOfType() {
        RoomGrid grid = new RoomGrid(7);
        grid.place(new Room(RoomType.START, new GridPos(3, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.BOSS, new GridPos(3, 2), RoomShape.SINGLE));

        assertEquals(2, grid.deadEndCells().size());
    }

    @Test
    void multiCellRoomCellsAreNeverDeadEnds() {
        RoomGrid grid = new RoomGrid(7);
        grid.place(new Room(RoomType.START, new GridPos(3, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(3, 1), RoomShape.VERTICAL));

        assertEquals(1, grid.deadEndCells().size());
        assertEquals(new GridPos(3, 3), grid.deadEndCells().get(0));
    }

    @Test
    void distancesWalkOutwardFromTheCenter() {
        RoomGrid grid = new RoomGrid(7);
        grid.place(new Room(RoomType.START, new GridPos(3, 3), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(3, 2), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(3, 1), RoomShape.SINGLE));

        Map<GridPos, Integer> distances = grid.distancesFromCenter();
        assertEquals(0, distances.get(new GridPos(3, 3)));
        assertEquals(1, distances.get(new GridPos(3, 2)));
        assertEquals(2, distances.get(new GridPos(3, 1)));
        assertTrue(distances.size() == 3);
    }
}
