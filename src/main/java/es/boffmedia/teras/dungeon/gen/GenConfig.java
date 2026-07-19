package es.boffmedia.teras.dungeon.gen;

/**
 * Generation tuning, one record instead of the legacy {@code DungeonConfig} constant-holder. The
 * numbers are the legacy values (which are Isaac's), except {@code largeShapeDecay}: the legacy
 * "normalization" divided large-shape odds by an accidental {@code largeSum + 1.0} on top of
 * halving them; the decay factor is that intent made explicit.
 */
public record GenConfig(
        int gridSize,
        double chanceQuad,
        double chanceHorizontal,
        double chanceVertical,
        double chanceLShape,
        double largeShapeDecay,
        int shapeResetInterval,
        double curseRoomChance,
        double challengeRoomChance,
        double miniBossChance,
        double firstStageMiniBossBoost,
        double labyrinthMultiplier,
        int labyrinthRoomCap,
        int lostRoomBonus,
        int finalStage,
        int finalStageRooms,
        int maxAttempts) {

    public GenConfig {
        if (gridSize < 5) {
            throw new IllegalArgumentException("gridSize must be at least 5: " + gridSize);
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive: " + maxAttempts);
        }
    }

    public static GenConfig defaults() {
        return new GenConfig(
                13,
                0.15, 0.20, 0.20, 0.10,
                0.5, 10,
                0.5, 0.5,
                0.25, 0.75,
                1.8, 45, 4,
                12, 50,
                20);
    }

    public boolean isValidStage(int stage) {
        return stage >= 1 && stage <= finalStage;
    }
}
