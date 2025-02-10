package es.boffmedia.teras.util.game.dungeons;

public class DungeonConfig {
    // Grid configuration
    private static final int GRID_SIZE = 13;
    private static final int CENTER = GRID_SIZE / 2;

    // Room dimensions
    private static final int ROOM_SIZE = 21;
    private static final int ROOM_HEIGHT = 8;
    private static final int DOOR_SIZE = 3;

    // Room shape probabilities
    private static final double PROBABILITY_2x2 = 0.15;
    private static final double PROBABILITY_2x1 = 0.20;
    private static final double PROBABILITY_1x2 = 0.20;
    private static final double PROBABILITY_1x1 = 1.00;

    // Special room generation
    private static final double CURSE_ROOM_CHANCE = 0.5;
    private static final double CHALLENGE_ROOM_CHANCE = 0.5;
    private static final double BASE_MINIBOSS_CHANCE = 0.25;
    private static final double FIRST_FLOOR_MINIBOSS_MODIFIER = 0.75;

    // Stage modifiers
    private static final double LABYRINTH_CURSE_MULTIPLIER = 1.8;
    private static final int LOST_CURSE_ROOM_BONUS = 4;
    private static final int FINAL_STAGE_MIN_ROOMS = 50;

    private DungeonConfig() {} // Prevent instantiation

    public static class Dimensions {
        public static int getGridSize() { return GRID_SIZE; }
        public static int getCenter() { return CENTER; }
        public static int getRoomSize() { return ROOM_SIZE; }
        public static int getRoomHeight() { return ROOM_HEIGHT; }
        public static int getDoorSize() { return DOOR_SIZE; }
    }

    public static class RoomProbabilities {
        public static double get2x2Chance() { return PROBABILITY_2x2; }
        public static double get2x1Chance() { return PROBABILITY_2x1; }
        public static double get1x2Chance() { return PROBABILITY_1x2; }
        public static double get1x1Chance() { return PROBABILITY_1x1; }
    }

    public static class SpecialRooms {
        public static double getCurseRoomChance() { return CURSE_ROOM_CHANCE; }
        public static double getChallengeRoomChance() { return CHALLENGE_ROOM_CHANCE; }

        public static double getMiniBossChance(int stageId) {
            return stageId == 1
                    ? BASE_MINIBOSS_CHANCE + FIRST_FLOOR_MINIBOSS_MODIFIER * BASE_MINIBOSS_CHANCE
                    : BASE_MINIBOSS_CHANCE;
        }
    }

    public static class StageModifiers {
        public static int calculateRooms(int baseRooms, boolean curseOfTheLabyrinth, boolean curseOfTheLost, int stageId) {
            int rooms = baseRooms;

            if (curseOfTheLabyrinth) {
                rooms = Math.min(45, (int)(rooms * LABYRINTH_CURSE_MULTIPLIER));
            } else if (curseOfTheLost) {
                rooms += LOST_CURSE_ROOM_BONUS;
            }

            if (stageId == 12) {
                rooms = FINAL_STAGE_MIN_ROOMS;
            }

            return rooms;
        }
    }

    public static boolean isValidStageId(int stageId) {
        return stageId >= 1 && stageId <= 12;
    }
}