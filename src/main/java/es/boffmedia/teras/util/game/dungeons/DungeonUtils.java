package es.boffmedia.teras.util.game.dungeons;

public class DungeonUtils {
    public static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public static Room[][] initializeDungeon() {
        int gridSize = DungeonConfig.Dimensions.getGridSize();
        Room[][] dungeon = new Room[gridSize][gridSize];
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                dungeon[y][x] = new Room(RoomType.WALL, x, y, 1, 1);
            }
        }
        return dungeon;
    }

    public static boolean isValidRoom(int x, int y) {
        int gridSize = DungeonConfig.Dimensions.getGridSize();
        return x >= 0 && x < gridSize && y >= 0 && y < gridSize;
    }

    public static int countRooms(Room[][] dungeon) {
        int count = 0;
        int gridSize = DungeonConfig.Dimensions.getGridSize();
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                if (dungeon[y][x].getType() != RoomType.WALL) {
                    count++;
                }
            }
        }
        return count;
    }

    public static boolean isDeadEnd(Room[][] dungeon, int x, int y) {
        return countNeighboringRooms(dungeon, x, y) == 1;
    }

    public static int countDeadEnds(Room[][] dungeon) {
        int count = 0;
        int gridSize = DungeonConfig.Dimensions.getGridSize();
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                Room room = dungeon[y][x];
                if (room.getType() != RoomType.WALL &&
                        room.getWidth() == 1 &&
                        room.getHeight() == 1 &&
                        isDeadEnd(dungeon, x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countNeighboringRooms(Room[][] dungeon, int x, int y) {
        int count = 0;
        for (int[] direction : DIRECTIONS) {
            int newX = x + direction[0];
            int newY = y + direction[1];
            if (isValidRoom(newX, newY) && dungeon[newY][newX].getType() != RoomType.WALL) {
                count++;
            }
        }
        return count;
    }
}