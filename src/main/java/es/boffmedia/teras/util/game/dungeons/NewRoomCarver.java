package es.boffmedia.teras.util.game.dungeons;

import java.util.ArrayList;
import java.util.List;

public class NewRoomCarver {
    private static class PendingRoom {
        int x, y, width, height;

        PendingRoom(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static class RoomShape {
        int width, height;
        double initialChance, currentChance;
        boolean isLarge;

        RoomShape(int width, int height, double initialChance, boolean isLarge) {
            this.width = width;
            this.height = height;
            this.initialChance = initialChance;
            this.currentChance = initialChance;
            this.isLarge = isLarge;
        }
    }

    private static class RoomShapeManager {
        private List<RoomShape> shapes;

        RoomShapeManager() {
            shapes = new ArrayList<>();
            shapes.add(new RoomShape(2, 2, 0.15, true));
            shapes.add(new RoomShape(2, 1, 0.2, true));
            shapes.add(new RoomShape(1, 2, 0.2, true));
            shapes.add(new RoomShape(1, 1, 1.0, false));
        }

        void updateChances(int width, int height) {
            boolean isLargeRoom = width > 1 || height > 1;
            for (RoomShape shape : shapes) {
                if (shape.isLarge && isLargeRoom) {
                    shape.currentChance *= 0.5;
                }
            }
            normalizeChances();
        }

        private void normalizeChances() {
            double totalLargeChance = 0;
            double normalRoomChance = 0;
            for (RoomShape shape : shapes) {
                if (shape.isLarge) {
                    totalLargeChance += shape.currentChance;
                } else {
                    normalRoomChance = shape.currentChance;
                }
            }
            double targetTotalChance = totalLargeChance + normalRoomChance;

            for (RoomShape shape : shapes) {
                if (shape.isLarge) {
                    shape.currentChance /= targetTotalChance;
                }
            }
        }

        void resetChances() {
            for (RoomShape shape : shapes) {
                shape.currentChance = shape.initialChance;
            }
        }

        List<RoomShape> getShapes() {
            return shapes;
        }
    }

    private static boolean isValidLargeRoom(Room[][] dungeon, int x, int y, int width, int height) {
        if (x + width > DungeonUtils.GRID_SIZE || y + height > DungeonUtils.GRID_SIZE) {
            return false;
        }

        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                if (dungeon[y + dy][x + dx].getType() != RoomType.WALL) {
                    return false;
                }
            }
        }

        List<int[]> positions = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            positions.add(new int[]{x - 1, y + i});
            positions.add(new int[]{x + width, y + i});
        }
        for (int i = 0; i < width; i++) {
            positions.add(new int[]{x + i, y - 1});
            positions.add(new int[]{x + i, y + height});
        }

        for (int[] pos : positions) {
            int checkX = pos[0], checkY = pos[1];
            if (DungeonUtils.isValidRoom(checkX, checkY) && dungeon[checkY][checkX].getType() != RoomType.WALL) {
                return true;
            }
        }

        return false;
    }

    private static void createLargeRoom(Room[][] dungeon, int x, int y, int width, int height, RoomType type) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                dungeon[y + dy][x + dx] = new Room(type, x + dx, y + dy, width, height, x, y);
            }
        }
    }

    private static PendingRoom tryCreateRoom(Room[][] dungeon, int x, int y, SeededRandom rng, RoomShapeManager shapeManager) {
        for (RoomShape shape : shapeManager.getShapes()) {
            if (rng.randomChance(shape.currentChance) && isValidLargeRoom(dungeon, x, y, shape.width, shape.height)) {
                createLargeRoom(dungeon, x, y, shape.width, shape.height, RoomType.NORMAL);
                shapeManager.updateChances(shape.width, shape.height);
                return new PendingRoom(x, y, shape.width, shape.height);
            }
        }
        return null;
    }

    private static int carveRooms(Room[][] dungeon, int targetRooms, SeededRandom rng) {
        int roomsCreated = 1; // Start room is already placed
        List<PendingRoom> pendingRooms = new ArrayList<>();
        RoomShapeManager shapeManager = new RoomShapeManager();

        // First, process the starting room
        for (int[] direction : DungeonUtils.DIRECTIONS) {
            int newX = DungeonUtils.CENTER + direction[0];
            int newY = DungeonUtils.CENTER + direction[1];

            if (DungeonUtils.isValidRoom(newX, newY) && dungeon[newY][newX].getType() == RoomType.WALL) {
                if (DungeonUtils.countNeighboringRooms(dungeon, newX, newY) < 2 && rng.randomChance(0.5)) {
                    PendingRoom newRoom = tryCreateRoom(dungeon, newX, newY, rng, shapeManager);
                    if (newRoom != null) {
                        roomsCreated += newRoom.width * newRoom.height;
                        pendingRooms.add(newRoom);
                    }
                }
            }
        }

        // Process each room created
        while (roomsCreated < targetRooms && !pendingRooms.isEmpty()) {
            PendingRoom currentRoom = pendingRooms.remove(0);

            int[][] checkDirections = {
                    {0, -1},
                    {0, currentRoom.height},
                    {-1, 0},
                    {currentRoom.width, 0}
            };

            for (int[] direction : checkDirections) {
                int newX = currentRoom.x + direction[0];
                int newY = currentRoom.y + direction[1];

                if (DungeonUtils.isValidRoom(newX, newY) && dungeon[newY][newX].getType() == RoomType.WALL) {
                    if (DungeonUtils.countNeighboringRooms(dungeon, newX, newY) < 2 && rng.randomChance(0.5)) {
                        PendingRoom newRoom = tryCreateRoom(dungeon, newX, newY, rng, shapeManager);
                        if (newRoom != null) {
                            roomsCreated += newRoom.width * newRoom.height;
                            if (roomsCreated < targetRooms) {
                                pendingRooms.add(newRoom);
                            }
                        }
                    }
                }
            }
        }

        // Fill remaining rooms if needed
        while (roomsCreated < targetRooms) {
            List<int[]> possibleSpaces = new ArrayList<>();

            for (int y = 0; y < DungeonUtils.GRID_SIZE; y++) {
                for (int x = 0; x < DungeonUtils.GRID_SIZE; x++) {
                    if (dungeon[y][x].getType() != RoomType.WALL) {
                        for (int[] direction : DungeonUtils.DIRECTIONS) {
                            int newX = x + direction[0];
                            int newY = y + direction[1];
                            if (DungeonUtils.isValidRoom(newX, newY) &&
                                    dungeon[newY][newX].getType() == RoomType.WALL &&
                                    DungeonUtils.countNeighboringRooms(dungeon, newX, newY) < 2) {
                                possibleSpaces.add(new int[]{newX, newY});
                            }
                        }
                    }
                }
            }

            if (possibleSpaces.isEmpty()) break;

            int[] selectedSpace = possibleSpaces.get(rng.randomInt(0, possibleSpaces.size() - 1));
            PendingRoom newRoom = tryCreateRoom(dungeon, selectedSpace[0], selectedSpace[1], rng, shapeManager);

            if (newRoom != null) {
                roomsCreated += newRoom.width * newRoom.height;
            } else {
                createLargeRoom(dungeon, selectedSpace[0], selectedSpace[1], 1, 1, RoomType.NORMAL);
                roomsCreated++;
            }

            // Reset chances periodically to avoid getting stuck with only 1x1 rooms
            if (roomsCreated % 10 == 0) {
                shapeManager.resetChances();
            }
        }

        return roomsCreated;
    }

    public static Room[][] generateDungeonLayout(int requiredRooms, int requiredDeadEnds, SeededRandom rng) {
        Room[][] dungeon = DungeonUtils.initializeDungeon();
        dungeon[DungeonUtils.CENTER][DungeonUtils.CENTER] = new Room(RoomType.START, DungeonUtils.CENTER, DungeonUtils.CENTER, 1, 1);

        int roomsCreated = carveRooms(dungeon, requiredRooms, rng);
        int currentDeadEnds = DungeonUtils.countDeadEnds(dungeon);

        // Add dead ends if necessary
        while (currentDeadEnds < requiredDeadEnds) {
            if (addDeadEnd(dungeon, rng)) {
                currentDeadEnds++;
            } else {
                break; // If we can't add more dead ends, break the loop
            }
        }

        return dungeon;
    }

    private static boolean addDeadEnd(Room[][] dungeon, SeededRandom rng) {
        List<int[]> possibleDeadEnds = new ArrayList<>();

        for (int y = 0; y < DungeonUtils.GRID_SIZE; y++) {
            for (int x = 0; x < DungeonUtils.GRID_SIZE; x++) {
                if (dungeon[y][x].getType() == RoomType.WALL) {
                    if (DungeonUtils.countNeighboringRooms(dungeon, x, y) == 1) {
                        possibleDeadEnds.add(new int[]{x, y});
                    }
                }
            }
        }

        if (!possibleDeadEnds.isEmpty()) {
            int[] selectedSpace = possibleDeadEnds.get(rng.randomInt(0, possibleDeadEnds.size() - 1));
            createLargeRoom(dungeon, selectedSpace[0], selectedSpace[1], 1, 1, RoomType.NORMAL);
            return true;
        }

        return false;
    }
}

