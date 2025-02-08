package es.boffmedia.teras.util.game.dungeons;
public class DungeonUtils {
    public static final int GRID_SIZE = 13;
    public static final int CENTER = GRID_SIZE / 2;

    public static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public static Room[][] initializeDungeon() {
        Room[][] dungeon = new Room[GRID_SIZE][GRID_SIZE];
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                dungeon[y][x] = new Room(RoomType.WALL, x, y, 1, 1);
            }
        }
        return dungeon;
    }

    public static boolean isValidRoom(int x, int y) {
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    public static int countRooms(Room[][] dungeon) {
        int count = 0;
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
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
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (dungeon[y][x].getType() != RoomType.WALL && dungeon[y][x].getWidth() == 1 && dungeon[y][x].getHeight() == 1) {
                    if (isDeadEnd(dungeon, x, y)) {
                        count++;
                    }
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

