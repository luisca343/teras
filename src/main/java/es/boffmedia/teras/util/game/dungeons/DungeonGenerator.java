package es.boffmedia.teras.util.game.dungeons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DungeonGenerator {
    public static class DungeonResult {
        public Room[][] dungeon;
        public String seed;

        public DungeonResult(Room[][] dungeon, String seed) {
            this.dungeon = dungeon;
            this.seed = seed;
        }
    }

    static int calculateNumberOfRooms(int stageId, boolean curseOfTheLabyrinth, boolean curseOfTheLost, SeededRandom rng) {
        if (!DungeonConfig.isValidStageId(stageId)) {
            throw new IllegalArgumentException("Invalid stage ID: " + stageId);
        }

        int baseRooms = Math.min(20, (rng.randomChance(0.5) ? 0 : 1) + 5 + (stageId * 10) / 3);
        int numberOfRooms = DungeonConfig.StageModifiers.calculateRooms(baseRooms, curseOfTheLabyrinth, curseOfTheLost, stageId);
        numberOfRooms += 2 + (rng.randomChance(0.5) ? 0 : 1);

        return numberOfRooms;
    }


    static int calculateMinDeadEnds(int stageId, boolean curseOfTheLabyrinth) {
        int minDeadEnds = 5;
        if (stageId != 1) minDeadEnds++;
        if (curseOfTheLabyrinth) minDeadEnds++;
        if (stageId == 12) minDeadEnds += 2;
        return minDeadEnds;
    }

    public static DungeonResult generateDungeon(int stageId, boolean curseOfTheLabyrinth, boolean curseOfTheLost, String seed) {
        String generatedSeed = (seed != null && !seed.isEmpty()) ? seed : String.valueOf(System.currentTimeMillis());
        String combinedSeed = stageId + "-" + generatedSeed;
        SeededRandom rng = new SeededRandom(combinedSeed);

        int requiredRooms = calculateNumberOfRooms(stageId, curseOfTheLabyrinth, curseOfTheLost, rng);
        int requiredDeadEnds = calculateMinDeadEnds(stageId, curseOfTheLabyrinth);

        Room[][] dungeon = NewRoomCarver.generateDungeonLayout(requiredRooms, requiredDeadEnds, rng);
        placeSpecialRooms(dungeon, stageId, rng);

        return new DungeonResult(dungeon, combinedSeed);
    }

    static void placeSpecialRooms(Room[][] dungeon, int stageId, SeededRandom rng) {
        List<Room> deadEnds = findDeadEnds(dungeon);
        Collections.sort(deadEnds, (a, b) -> getDistanceFromStart(dungeon, b) - getDistanceFromStart(dungeon, a));

        int currentDeadEndIndex = 0;
        Room bossRoom = null;

        // Place Boss Room
        if (currentDeadEndIndex < deadEnds.size()) {
            bossRoom = deadEnds.get(currentDeadEndIndex);
            dungeon[bossRoom.getY()][bossRoom.getX()].setType(RoomType.BOSS);
            currentDeadEndIndex++;
        }

        // Place Super Secret Room
        if (currentDeadEndIndex < deadEnds.size()) {
            dungeon[deadEnds.get(currentDeadEndIndex).getY()][deadEnds.get(currentDeadEndIndex).getX()].setType(RoomType.SUPER_SECRET);
            currentDeadEndIndex++;
        }

        // Place Shop
        if (currentDeadEndIndex < deadEnds.size()) {
            dungeon[deadEnds.get(currentDeadEndIndex).getY()][deadEnds.get(currentDeadEndIndex).getX()].setType(RoomType.SHOP);
            currentDeadEndIndex++;
        }

        // Place Curse Room
        if (currentDeadEndIndex < deadEnds.size() && rng.randomChance(DungeonConfig.SpecialRooms.getCurseRoomChance())) {
            dungeon[deadEnds.get(currentDeadEndIndex).getY()][deadEnds.get(currentDeadEndIndex).getX()].setType(RoomType.CURSE);
            currentDeadEndIndex++;
        }

        // Place Mini-Boss Room
        if (currentDeadEndIndex < deadEnds.size()) {
            double miniBossChance = DungeonConfig.SpecialRooms.getMiniBossChance(stageId);
            if (rng.randomChance(miniBossChance)) {
                dungeon[deadEnds.get(currentDeadEndIndex).getY()][deadEnds.get(currentDeadEndIndex).getX()].setType(RoomType.MINI_BOSS);
                currentDeadEndIndex++;
            }
        }

        // Place Challenge Room
        if (currentDeadEndIndex < deadEnds.size() && stageId > 1 &&
                rng.randomChance(DungeonConfig.SpecialRooms.getChallengeRoomChance())) {
            dungeon[deadEnds.get(currentDeadEndIndex).getY()][deadEnds.get(currentDeadEndIndex).getX()].setType(RoomType.CHALLENGE);
            currentDeadEndIndex++;
        }

        // Place Treasure Room
        Room treasureRoom = null;
        if (currentDeadEndIndex < deadEnds.size()) {
            treasureRoom = deadEnds.get(currentDeadEndIndex);
            dungeon[treasureRoom.getY()][treasureRoom.getX()].setType(RoomType.TREASURE);
            currentDeadEndIndex++;
        } else {
            Room newDeadEnd = addDeadEnd(dungeon, rng);
            if (newDeadEnd != null) {
                treasureRoom = dungeon[newDeadEnd.getY()][newDeadEnd.getX()];

                if (bossRoom != null) {
                    int bossDistance = getDistanceFromStart(dungeon, bossRoom);
                    int newRoomDistance = getDistanceFromStart(dungeon, treasureRoom);

                    if (newRoomDistance > bossDistance) {
                        dungeon[bossRoom.getY()][bossRoom.getX()].setType(RoomType.TREASURE);
                        dungeon[treasureRoom.getY()][treasureRoom.getX()].setType(RoomType.BOSS);
                        Room tempRoom = bossRoom;
                        bossRoom = treasureRoom;
                        treasureRoom = tempRoom;
                    } else {
                        dungeon[treasureRoom.getY()][treasureRoom.getX()].setType(RoomType.TREASURE);
                    }
                } else {
                    dungeon[treasureRoom.getY()][treasureRoom.getX()].setType(RoomType.TREASURE);
                }
            }
        }

        placeSecretRoom(dungeon, rng);
    }

    private static List<Room> findDeadEnds(Room[][] dungeon) {
        List<Room> deadEnds = new ArrayList<>();
        int gridSize = DungeonConfig.Dimensions.getGridSize();

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                if (dungeon[y][x].getType() == RoomType.NORMAL &&
                        dungeon[y][x].getWidth() == 1 &&
                        dungeon[y][x].getHeight() == 1) {
                    if (DungeonUtils.countNeighboringRooms(dungeon, x, y) == 1) {
                        deadEnds.add(dungeon[y][x]);
                    }
                }
            }
        }
        return deadEnds;
    }

    private static void placeSecretRoom(Room[][] dungeon, SeededRandom rng) {
        List<RoomCandidate> candidates = getSecretRoomCandidates(dungeon, rng);
        if (candidates.isEmpty()) return;

        RoomCandidate bestCandidate = candidates.stream()
                .max((a, b) -> Double.compare(a.weight, b.weight))
                .orElse(null);

        if (bestCandidate != null) {
            dungeon[bestCandidate.y][bestCandidate.x] = new Room(RoomType.SECRET, bestCandidate.x, bestCandidate.y, 1, 1);
        }
    }

    private static class RoomCandidate {
        int x, y;
        double weight;

        RoomCandidate(int x, int y, double weight) {
            this.x = x;
            this.y = y;
            this.weight = weight;
        }
    }

    private static List<RoomCandidate> getSecretRoomCandidates(Room[][] dungeon, SeededRandom rng) {
        List<RoomCandidate> candidates = new ArrayList<>();
        int gridSize = DungeonConfig.Dimensions.getGridSize();

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                if (dungeon[y][x].getType() == RoomType.WALL) {
                    int neighboringRooms = 0;
                    boolean isValid = false;

                    for (int[] direction : DungeonUtils.DIRECTIONS) {
                        int newX = x + direction[0];
                        int newY = y + direction[1];
                        if (DungeonUtils.isValidRoom(newX, newY) && dungeon[newY][newX].getType() != RoomType.WALL) {
                            neighboringRooms++;
                            isValid = true;
                        }
                    }

                    if (isValid && !isAdjacentToSpecialRoom(dungeon, x, y)) {
                        double weight = 10 + rng.randomInt(0, 4);
                        if (neighboringRooms == 2) weight -= 3;
                        if (neighboringRooms == 1) weight -= 6;

                        candidates.add(new RoomCandidate(x, y, weight));
                    }
                }
            }
        }

        return candidates;
    }

    private static boolean isAdjacentToSpecialRoom(Room[][] dungeon, int x, int y) {
        for (int[] direction : DungeonUtils.DIRECTIONS) {
            int newX = x + direction[0];
            int newY = y + direction[1];
            if (DungeonUtils.isValidRoom(newX, newY)) {
                RoomType roomType = dungeon[newY][newX].getType();
                if (roomType == RoomType.BOSS || roomType == RoomType.SUPER_SECRET || roomType == RoomType.SECRET) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int getDistanceFromStart(Room[][] dungeon, Room room) {
        return bfs(dungeon, new int[]{DungeonConfig.Dimensions.getCenter(), DungeonConfig.Dimensions.getCenter()},
                new int[]{room.getX(), room.getY()});
    }

    private static int bfs(Room[][] dungeon, int[] start, int[] end) {
        int gridSize = DungeonConfig.Dimensions.getGridSize();
        boolean[][] visited = new boolean[gridSize][gridSize];
        List<int[]> queue = new ArrayList<>();
        queue.add(new int[]{start[0], start[1], 0});

        while (!queue.isEmpty()) {
            int[] current = queue.remove(0);
            int x = current[0], y = current[1], distance = current[2];

            if (x == end[0] && y == end[1]) {
                return distance;
            }

            if (!visited[y][x]) {
                visited[y][x] = true;

                for (int[] direction : DungeonUtils.DIRECTIONS) {
                    int newX = x + direction[0];
                    int newY = y + direction[1];

                    if (DungeonUtils.isValidRoom(newX, newY) && !visited[newY][newX] &&
                            dungeon[newY][newX].getType() != RoomType.WALL) {
                        queue.add(new int[]{newX, newY, distance + 1});
                    }
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    private static Room addDeadEnd(Room[][] dungeon, SeededRandom rng) {
        List<int[]> possibleDeadEnds = new ArrayList<>();

        for (int y = 0; y < DungeonConfig.Dimensions.getGridSize(); y++) {
            for (int x = 0; x < DungeonConfig.Dimensions.getGridSize(); x++) {
                if (dungeon[y][x].getType() == RoomType.WALL) {
                    if (DungeonUtils.countNeighboringRooms(dungeon, x, y) == 1) {
                        boolean isNextToNormalRoom = false;
                        for (int[] direction : DungeonUtils.DIRECTIONS) {
                            int newX = x + direction[0];
                            int newY = y + direction[1];
                            if (DungeonUtils.isValidRoom(newX, newY) &&
                                    dungeon[newY][newX].getType() != RoomType.WALL) {
                                if (dungeon[newY][newX].getType() == RoomType.NORMAL) {
                                    isNextToNormalRoom = true;
                                    break;
                                }
                            }
                        }
                        if (isNextToNormalRoom) {
                            possibleDeadEnds.add(new int[]{x, y});
                        }
                    }
                }
            }
        }

        if (!possibleDeadEnds.isEmpty()) {
            int[] selectedSpace = possibleDeadEnds.get(rng.randomInt(0, possibleDeadEnds.size() - 1));
            Room newRoom = new Room(RoomType.NORMAL, selectedSpace[0], selectedSpace[1], 1, 1);
            dungeon[selectedSpace[1]][selectedSpace[0]] = newRoom;
            return newRoom;
        }

        return null;
    }
}