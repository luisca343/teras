package es.boffmedia.teras.util.game.dungeons;

import java.util.ArrayList;
import java.util.List;

public class NewRoomCarver {
    private static class PendingRoom {
        int x, y;
        RoomShape shape;

        PendingRoom(int x, int y, RoomShape shape) {
            this.x = x;
            this.y = y;
            this.shape = shape;
        }
    }
    private static class RoomShapeConfig {
        RoomShape shape;
        double initialChance, currentChance;

        RoomShapeConfig(RoomShape shape, double initialChance) {
            this.shape = shape;
            this.initialChance = initialChance;
            this.currentChance = initialChance;
        }
    }

    private static class RoomShapeManager {
        private List<RoomShapeConfig> shapes;

        RoomShapeManager() {
            shapes = new ArrayList<>();
            // Regular room shapes
            shapes.add(new RoomShapeConfig(RoomShape.QUAD, DungeonConfig.RoomProbabilities.get2x2Chance()));
            shapes.add(new RoomShapeConfig(RoomShape.HORIZONTAL, DungeonConfig.RoomProbabilities.get2x1Chance()));
            shapes.add(new RoomShapeConfig(RoomShape.VERTICAL, DungeonConfig.RoomProbabilities.get1x2Chance()));
            shapes.add(new RoomShapeConfig(RoomShape.SINGLE, DungeonConfig.RoomProbabilities.get1x1Chance()));

            // L-shaped rooms
            double lShapeChance = DungeonConfig.RoomProbabilities.getLShapedChance();
            shapes.add(new RoomShapeConfig(RoomShape.L_SHAPE_TOP_LEFT, lShapeChance));
            shapes.add(new RoomShapeConfig(RoomShape.L_SHAPE_TOP_RIGHT, lShapeChance));
            shapes.add(new RoomShapeConfig(RoomShape.L_SHAPE_BOTTOM_LEFT, lShapeChance));
            // shapes.add(new RoomShapeConfig(RoomShape.L_SHAPE_BOTTOM_RIGHT, lShapeChance));
        }

        void updateChances(RoomShape usedShape) {
            if (usedShape.isLarge()) {
                for (RoomShapeConfig config : shapes) {
                    if (config.shape.isLarge()) {
                        config.currentChance *= 0.5;
                    }
                }
                normalizeChances();
            }
        }

        private void normalizeChances() {
            double totalLargeChance = 0;
            double normalRoomChance = 0;

            for (RoomShapeConfig config : shapes) {
                if (config.shape.isLarge()) {
                    totalLargeChance += config.currentChance;
                } else {
                    normalRoomChance = config.currentChance;
                }
            }

            double targetTotalChance = totalLargeChance + normalRoomChance;
            for (RoomShapeConfig config : shapes) {
                if (config.shape.isLarge()) {
                    config.currentChance /= targetTotalChance;
                }
            }
        }

        void resetChances() {
            for (RoomShapeConfig config : shapes) {
                config.currentChance = config.initialChance;
            }
        }

        List<RoomShapeConfig> getShapes() {
            return shapes;
        }
    }


    private static boolean isValidLargeRoom(Room[][] dungeon, int x, int y, RoomShape shape) {
        // First check if any of the cells that would be part of this room are special rooms
        // This prevents large rooms from overwriting special rooms
        if (shape.isLShaped()) {
            boolean[][] layout = getLShapeLayout(shape);
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    if (layout[dy][dx] && dungeon[y + dy][x + dx].getType() != RoomType.WALL) {
                        return false;
                    }
                }
            }
        } else {
            for (int dy = 0; dy < shape.getHeight(); dy++) {
                for (int dx = 0; dx < shape.getWidth(); dx++) {
                    if (dungeon[y + dy][x + dx].getType() != RoomType.WALL) {
                        return false;
                    }
                }
            }
        }

        // Check basic boundaries
        if (x + shape.getWidth() > DungeonConfig.Dimensions.getGridSize() ||
                y + shape.getHeight() > DungeonConfig.Dimensions.getGridSize()) {
            return false;
        }

        // For L-shaped rooms, check the specific layout
        if (shape.isLShaped()) {
            return isValidLShapedRoom(dungeon, x, y, shape);
        }

        // For regular rooms, check the rectangular area
        return isValidRectangularRoom(dungeon, x, y, shape);
    }


    private static boolean isValidRectangularRoom(Room[][] dungeon, int x, int y, RoomShape shape) {
        // Check if all cells in the shape are walls
        for (int dy = 0; dy < shape.getHeight(); dy++) {
            for (int dx = 0; dx < shape.getWidth(); dx++) {
                if (dungeon[y + dy][x + dx].getType() != RoomType.WALL) {
                    return false;
                }
            }
        }

        // Check if there's at least one adjacent room
        List<int[]> positions = new ArrayList<>();
        for (int i = 0; i < shape.getHeight(); i++) {
            positions.add(new int[]{x - 1, y + i});
            positions.add(new int[]{x + shape.getWidth(), y + i});
        }
        for (int i = 0; i < shape.getWidth(); i++) {
            positions.add(new int[]{x + i, y - 1});
            positions.add(new int[]{x + i, y + shape.getHeight()});
        }

        return hasAdjacentRoom(dungeon, positions);
    }

    private static boolean isValidLShapedRoom(Room[][] dungeon, int x, int y, RoomShape shape) {
        // Get the cells that should be part of the L-shape
        boolean[][] roomLayout = getLShapeLayout(shape);

        // Check if all required cells are walls
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                if (roomLayout[dy][dx] && dungeon[y + dy][x + dx].getType() != RoomType.WALL) {
                    return false;
                }
            }
        }

        // Check for adjacent rooms
        List<int[]> positions = new ArrayList<>();
        positions.add(new int[]{x - 1, y});
        positions.add(new int[]{x - 1, y + 1});
        positions.add(new int[]{x + 2, y});
        positions.add(new int[]{x + 2, y + 1});
        positions.add(new int[]{x, y - 1});
        positions.add(new int[]{x + 1, y - 1});
        positions.add(new int[]{x, y + 2});
        positions.add(new int[]{x + 1, y + 2});

        return hasAdjacentRoom(dungeon, positions);
    }

    private static boolean[][] getLShapeLayout(RoomShape shape) {
        boolean[][] layout = new boolean[2][2];
        switch (shape) {
            case L_SHAPE_TOP_LEFT:
                layout[0][0] = layout[0][1] = layout[1][0] = true;
                break;
            case L_SHAPE_TOP_RIGHT:
                layout[0][0] = layout[0][1] = layout[1][1] = true;
                break;
            case L_SHAPE_BOTTOM_LEFT:
                layout[0][0] = layout[1][0] = layout[1][1] = true;
                break;
            case L_SHAPE_BOTTOM_RIGHT:
                layout[0][1] = layout[1][0] = layout[1][1] = true;
                break;
        }
        return layout;
    }

    private static boolean hasAdjacentRoom(Room[][] dungeon, List<int[]> positions) {
        for (int[] pos : positions) {
            int checkX = pos[0], checkY = pos[1];
            if (DungeonUtils.isValidRoom(checkX, checkY) &&
                    dungeon[checkY][checkX].getType() != RoomType.WALL) {
                return true;
            }
        }
        return false;
    }

    private static void createRoom(Room[][] dungeon, int x, int y, RoomType type, RoomShape shape) {
        if (shape.isLShaped()) {
            createLShapedRoom(dungeon, x, y, type, shape);
        } else {
            createRectangularRoom(dungeon, x, y, type, shape);
        }
    }

    private static void createRectangularRoom(Room[][] dungeon, int x, int y, RoomType type, RoomShape shape) {
        for (int dy = 0; dy < shape.getHeight(); dy++) {
            for (int dx = 0; dx < shape.getWidth(); dx++) {
                if (dy == 0 && dx == 0) {
                    dungeon[y + dy][x + dx] = new Room(type, x, y, shape);
                } else {
                    dungeon[y + dy][x + dx] = new Room(type, x + dx, y + dy, shape, x, y);
                }
            }
        }
    }

    private static void createLShapedRoom(Room[][] dungeon, int x, int y, RoomType type, RoomShape shape) {
        boolean[][] layout = getLShapeLayout(shape);
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                if (layout[dy][dx]) {
                    if (dy == 0 && dx == 0) {
                        dungeon[y + dy][x + dx] = new Room(type, x, y, shape);
                    } else {
                        dungeon[y + dy][x + dx] = new Room(type, x + dx, y + dy, shape, x, y);
                    }
                }
            }
        }
    }

    private static PendingRoom tryCreateRoom(Room[][] dungeon, int x, int y, SeededRandom rng, RoomShapeManager shapeManager) {
        // Only try large room shapes for NORMAL rooms
        for (RoomShapeConfig config : shapeManager.getShapes()) {
            // Skip large room shapes for special rooms
            if (config.shape.isLarge()) {
                if (rng.randomChance(config.currentChance) && isValidLargeRoom(dungeon, x, y, config.shape)) {
                    createRoom(dungeon, x, y, RoomType.NORMAL, config.shape);
                    shapeManager.updateChances(config.shape);
                    return new PendingRoom(x, y, config.shape);
                }
            }
        }

        // If we couldn't create a large room or if we're creating a special room, create a 1x1 room
        if (isValidLargeRoom(dungeon, x, y, RoomShape.SINGLE)) {
            createRoom(dungeon, x, y, RoomType.NORMAL, RoomShape.SINGLE);
            return new PendingRoom(x, y, RoomShape.SINGLE);
        }

        return null;
    }

    private static int countRoomCells(RoomShape shape) {
        if (shape.isLShaped()) {
            return 3; // L-shaped rooms always occupy 3 cells
        }
        return shape.getWidth() * shape.getHeight();
    }

    private static List<int[]> getRoomEdges(PendingRoom room) {
        List<int[]> edges = new ArrayList<>();

        if (room.shape.isLShaped()) {
            boolean[][] layout = getLShapeLayout(room.shape);
            // Add positions around the L-shape
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    if (layout[dy][dx]) {
                        // Check adjacent positions
                        int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
                        for (int[] dir : directions) {
                            int newX = room.x + dx + dir[0];
                            int newY = room.y + dy + dir[1];
                            if (!isPartOfLShape(newX - room.x, newY - room.y, layout)) {
                                edges.add(new int[]{newX, newY});
                            }
                        }
                    }
                }
            }
        } else {
            // Add positions around rectangular room
            for (int i = 0; i < room.shape.getHeight(); i++) {
                edges.add(new int[]{room.x - 1, room.y + i}); // Left side
                edges.add(new int[]{room.x + room.shape.getWidth(), room.y + i}); // Right side
            }
            for (int i = 0; i < room.shape.getWidth(); i++) {
                edges.add(new int[]{room.x + i, room.y - 1}); // Top side
                edges.add(new int[]{room.x + i, room.y + room.shape.getHeight()}); // Bottom side
            }
        }

        return edges;
    }

    private static boolean isPartOfLShape(int dx, int dy, boolean[][] layout) {
        return dx >= 0 && dx < 2 && dy >= 0 && dy < 2 && layout[dy][dx];
    }

    public static Room[][] generateDungeonLayout(int requiredRooms, int requiredDeadEnds, SeededRandom rng) {
        Room[][] dungeon = DungeonUtils.initializeDungeon();
        dungeon[DungeonConfig.Dimensions.getCenter()][DungeonConfig.Dimensions.getCenter()] =
                new Room(RoomType.START, DungeonConfig.Dimensions.getCenter(), DungeonConfig.Dimensions.getCenter(), RoomShape.SINGLE);

        int roomsCreated = carveRooms(dungeon, requiredRooms, rng);
        int currentDeadEnds = DungeonUtils.countDeadEnds(dungeon);

        while (currentDeadEnds < requiredDeadEnds) {
            if (addDeadEnd(dungeon, rng)) {
                currentDeadEnds++;
            } else {
                break;
            }
        }

        return dungeon;
    }

    private static int carveRooms(Room[][] dungeon, int targetRooms, SeededRandom rng) {
        int roomsCreated = 1; // Start room is already placed
        List<PendingRoom> pendingRooms = new ArrayList<>();
        RoomShapeManager shapeManager = new RoomShapeManager();

        // Process starting room
        for (int[] direction : DungeonUtils.DIRECTIONS) {
            int newX = DungeonConfig.Dimensions.getCenter() + direction[0];
            int newY = DungeonConfig.Dimensions.getCenter() + direction[1];

            if (DungeonUtils.isValidRoom(newX, newY) && dungeon[newY][newX].getType() == RoomType.WALL) {
                if (DungeonUtils.countNeighboringRooms(dungeon, newX, newY) < 2 && rng.randomChance(0.5)) {
                    PendingRoom newRoom = tryCreateRoom(dungeon, newX, newY, rng, shapeManager);
                    if (newRoom != null) {
                        roomsCreated += countRoomCells(newRoom.shape);
                        pendingRooms.add(newRoom);
                    }
                }
            }
        }

        // Process each room created
        while (roomsCreated < targetRooms && !pendingRooms.isEmpty()) {
            PendingRoom currentRoom = pendingRooms.remove(0);
            List<int[]> edges = getRoomEdges(currentRoom);

            for (int[] edge : edges) {
                int newX = edge[0];
                int newY = edge[1];

                if (DungeonUtils.isValidRoom(newX, newY) && dungeon[newY][newX].getType() == RoomType.WALL) {
                    if (DungeonUtils.countNeighboringRooms(dungeon, newX, newY) < 2 && rng.randomChance(0.5)) {
                        PendingRoom newRoom = tryCreateRoom(dungeon, newX, newY, rng, shapeManager);
                        if (newRoom != null) {
                            roomsCreated += countRoomCells(newRoom.shape);
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
            List<int[]> possibleSpaces = findPossibleSpaces(dungeon);
            if (possibleSpaces.isEmpty()) break;

            int[] selectedSpace = possibleSpaces.get(rng.randomInt(0, possibleSpaces.size() - 1));
            PendingRoom newRoom = tryCreateRoom(dungeon, selectedSpace[0], selectedSpace[1], rng, shapeManager);

            if (newRoom != null) {
                roomsCreated += countRoomCells(newRoom.shape);
            } else {
                createRoom(dungeon, selectedSpace[0], selectedSpace[1], RoomType.NORMAL, RoomShape.SINGLE);
                roomsCreated++;
            }

            if (roomsCreated % 10 == 0) {
                shapeManager.resetChances();
            }
        }

        return roomsCreated;
    }

    private static List<int[]> findPossibleSpaces(Room[][] dungeon) {
        List<int[]> possibleSpaces = new ArrayList<>();
        int gridSize = DungeonConfig.Dimensions.getGridSize();

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
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

        return possibleSpaces;
    }

    private static boolean addDeadEnd(Room[][] dungeon, SeededRandom rng) {
        List<int[]> possibleDeadEnds = new ArrayList<>();

        for (int y = 0; y < DungeonConfig.Dimensions.getGridSize(); y++) {
            for (int x = 0; x < DungeonConfig.Dimensions.getGridSize(); x++) {
                if (dungeon[y][x].getType() == RoomType.WALL) {
                    if (DungeonUtils.countNeighboringRooms(dungeon, x, y) == 1) {
                        possibleDeadEnds.add(new int[]{x, y});
                    }
                }
            }
        }

        if (!possibleDeadEnds.isEmpty()) {
            int[] selectedSpace = possibleDeadEnds.get(rng.randomInt(0, possibleDeadEnds.size() - 1));
            createRoom(dungeon, selectedSpace[0], selectedSpace[1], RoomType.NORMAL, RoomShape.SINGLE);
            return true;
        }

        return false;
    }
}