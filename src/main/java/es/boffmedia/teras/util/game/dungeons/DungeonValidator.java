package es.boffmedia.teras.util.game.dungeons;

import java.util.*;

public class DungeonValidator {
    public static class ValidationResult {
        private final boolean isValid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
            this.isValid = isValid;
            this.errors = Collections.unmodifiableList(errors);
            this.warnings = Collections.unmodifiableList(warnings);
        }

        public boolean isValid() { return isValid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }

    public static ValidationResult validateDungeon(Room[][] dungeon, int stageId,
                                                   boolean curseOfTheLabyrinth, boolean curseOfTheLost) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateBasicStructure(dungeon, errors);
        validateSpecialRooms(dungeon, errors, warnings);
        validateRoomConnectivity(dungeon, errors);
        validateStageSpecificRules(dungeon, stageId, curseOfTheLabyrinth, curseOfTheLost, errors, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private static void validateBasicStructure(Room[][] dungeon, List<String> errors) {
        if (dungeon == null || dungeon.length != DungeonConfig.Dimensions.getGridSize()) {
            errors.add("Invalid dungeon dimensions");
            return;
        }

        boolean hasStart = false;
        boolean hasBoss = false;

        for (int y = 0; y < dungeon.length; y++) {
            for (int x = 0; x < dungeon[y].length; x++) {
                Room room = dungeon[y][x];
                if (room == null) {
                    errors.add(String.format("Null room at position [%d, %d]", x, y));
                    continue;
                }

                if (room.getType() == RoomType.START) hasStart = true;
                if (room.getType() == RoomType.BOSS) hasBoss = true;

                validateRoomDimensions(room, errors);
            }
        }

        if (!hasStart) errors.add("Missing start room");
        if (!hasBoss) errors.add("Missing boss room");
    }

    private static void validateRoomDimensions(Room room, List<String> errors) {
        if (room.getWidth() < 1 || room.getHeight() < 1) {
            errors.add(String.format("Invalid room dimensions at [%d, %d]", room.getX(), room.getY()));
        }

        // Add validation for special rooms
        if (room.getType() != RoomType.NORMAL && room.getType() != RoomType.WALL &&
                (room.getWidth() > 1 || room.getHeight() > 1)) {
            errors.add(String.format("Special room at [%d, %d] cannot be larger than 1x1", room.getX(), room.getY()));
        }

        if (room.getWidth() > 2 || room.getHeight() > 2) {
            errors.add(String.format("Room too large at [%d, %d]", room.getX(), room.getY()));
        }
    }

    private static void validateSpecialRooms(Room[][] dungeon, List<String> errors, List<String> warnings) {
        Map<RoomType, Integer> roomCounts = new EnumMap<>(RoomType.class);
        for (RoomType type : RoomType.values()) {
            roomCounts.put(type, 0);
        }

        for (Room[] rooms : dungeon) {
            for (Room room : rooms) {
                roomCounts.merge(room.getType(), 1, Integer::sum);
            }
        }

        // Validate required rooms
        if (roomCounts.get(RoomType.TREASURE) == 0) errors.add("Missing treasure room");
        if (roomCounts.get(RoomType.SHOP) == 0) errors.add("Missing shop");
        if (roomCounts.get(RoomType.SUPER_SECRET) == 0) warnings.add("Missing super secret room");

        // Validate room count limits
        if (roomCounts.get(RoomType.BOSS) > 1) errors.add("Multiple boss rooms detected");
        if (roomCounts.get(RoomType.START) > 1) errors.add("Multiple start rooms detected");
        if (roomCounts.get(RoomType.SHOP) > 1) warnings.add("Multiple shops detected");
    }

    private static void validateRoomConnectivity(Room[][] dungeon, List<String> errors) {
        Room startRoom = findStartRoom(dungeon);
        if (startRoom == null) return; // Already reported in basic structure validation

        Set<Room> reachableRooms = new HashSet<>();
        findReachableRooms(dungeon, startRoom, reachableRooms);

        int totalRooms = countNonWallRooms(dungeon);
        if (reachableRooms.size() != totalRooms) {
            errors.add(String.format("Disconnected rooms detected: %d unreachable rooms",
                    totalRooms - reachableRooms.size()));
        }
    }

    private static void validateStageSpecificRules(Room[][] dungeon, int stageId,
                                                   boolean curseOfTheLabyrinth, boolean curseOfTheLost,
                                                   List<String> errors, List<String> warnings) {

        int minDeadEnds = DungeonGenerator.calculateMinDeadEnds(stageId, curseOfTheLabyrinth);
        int actualDeadEnds = DungeonUtils.countDeadEnds(dungeon);

        if (actualDeadEnds < minDeadEnds) {
            errors.add(String.format("Insufficient dead ends: %d (minimum: %d)",
                    actualDeadEnds, minDeadEnds));
        }

        if (stageId == 12 && !hasChallengeRoom(dungeon)) {
            warnings.add("Final stage missing challenge room");
        }
    }

    private static Room findStartRoom(Room[][] dungeon) {
        for (Room[] rooms : dungeon) {
            for (Room room : rooms) {
                if (room.getType() == RoomType.START) return room;
            }
        }
        return null;
    }

    private static void findReachableRooms(Room[][] dungeon, Room start, Set<Room> visited) {
        if (visited.contains(start)) return;
        visited.add(start);

        for (int[] direction : DungeonUtils.DIRECTIONS) {
            int newX = start.getX() + direction[0];
            int newY = start.getY() + direction[1];

            if (DungeonUtils.isValidRoom(newX, newY)) {
                Room nextRoom = dungeon[newY][newX];
                if (nextRoom.getType() != RoomType.WALL) {
                    findReachableRooms(dungeon, nextRoom, visited);
                }
            }
        }
    }

    private static int countNonWallRooms(Room[][] dungeon) {
        int count = 0;
        for (Room[] rooms : dungeon) {
            for (Room room : rooms) {
                if (room.getType() != RoomType.WALL) count++;
            }
        }
        return count;
    }

    private static boolean hasChallengeRoom(Room[][] dungeon) {
        for (Room[] rooms : dungeon) {
            for (Room room : rooms) {
                if (room.getType() == RoomType.CHALLENGE) return true;
            }
        }
        return false;
    }
}