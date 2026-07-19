package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.RoomType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutAsciiTest {

    @Test
    void rendersOneLegacySymbolPerCell() {
        RoomGrid grid = new RoomGrid(5);
        grid.place(new Room(RoomType.START, new GridPos(2, 2), RoomShape.SINGLE));
        grid.place(new Room(RoomType.NORMAL, new GridPos(2, 1), RoomShape.HORIZONTAL));
        grid.place(new Room(RoomType.BOSS, new GridPos(2, 3), RoomShape.SINGLE));

        String expected =
                "█ █ █ █ █\n"
                + "█ █ □ □ █\n"
                + "█ █ S █ █\n"
                + "█ █ B █ █\n"
                + "█ █ █ █ █\n";
        assertEquals(expected, LayoutAscii.render(grid));
    }
}
