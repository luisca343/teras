package es.boffmedia.teras.dungeon.gen;

/**
 * Generation tuning, one record instead of the legacy {@code DungeonConfig} constant-holder. The
 * numbers are the legacy values (which are Isaac's), except {@code largeShapeDecay}: the legacy
 * "normalization" divided large-shape odds by an accidental {@code largeSum + 1.0} on top of
 * halving them; the decay factor is that intent made explicit.
 *
 * <p>{@code referenceLength} was {@code finalStage} while every run was twelve floors. It is no
 * longer "the last floor" — a dungeon declares its own length from its tramos — but the length the
 * room-count and difficulty curves are <i>authored against</i>. {@link FloorDepth} maps a floor of
 * any dungeon onto it.</p>
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
        double sacrificeRoomChance,
        double arcadeRoomChance,
        double devilDealChance,
        double miniBossChance,
        double firstStageMiniBossBoost,
        double labyrinthMultiplier,
        int labyrinthRoomCap,
        int lostRoomBonus,
        int referenceLength,
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

    /**
     * This config with the multi-cell shape chances scaled by a piso's own weights.
     *
     * <p>Shape odds are global, which is right for the generator — it should not know what a piso
     * is. But two pisos that declare the same families then roll identically, so the only lever for
     * making one feel tighter was to <b>forbid</b> it a shape. That is blunt: forbidding also
     * deletes the rooms, and a shape that can never appear cannot be a surprise when it does.
     * Scaling here keeps the generator ignorant of pisos and lets one say "big rooms exist, and
     * they are rare".</p>
     *
     * <p>SINGLE is deliberately not scalable: it is what every layout falls back to, and weighting
     * it would only mean something relative to the other three, which those already express.</p>
     */
    public GenConfig withShapeWeights(
            java.util.Map<es.boffmedia.teras.dungeon.model.ShapeFamily, Double> weights) {
        if (weights == null || weights.isEmpty()) {
            return this;
        }
        double big = weight(weights, es.boffmedia.teras.dungeon.model.ShapeFamily.BIG);
        double large = weight(weights, es.boffmedia.teras.dungeon.model.ShapeFamily.LARGE);
        double l = weight(weights, es.boffmedia.teras.dungeon.model.ShapeFamily.L);
        return new GenConfig(gridSize,
                chanceQuad * big, chanceHorizontal * large, chanceVertical * large, chanceLShape * l,
                largeShapeDecay, shapeResetInterval,
                curseRoomChance, challengeRoomChance, sacrificeRoomChance, arcadeRoomChance,
                devilDealChance, miniBossChance, firstStageMiniBossBoost,
                labyrinthMultiplier, labyrinthRoomCap, lostRoomBonus,
                referenceLength, finalStageRooms, maxAttempts);
    }

    private static double weight(
            java.util.Map<es.boffmedia.teras.dungeon.model.ShapeFamily, Double> weights,
            es.boffmedia.teras.dungeon.model.ShapeFamily family) {
        Double value = weights.get(family);
        return value == null ? 1.0 : Math.max(0.0, value);
    }

    public static GenConfig defaults() {
        return new GenConfig(
                13,
                0.15, 0.20, 0.20, 0.10,
                0.5, 10,
                0.5, 0.5,
                0.35, 0.35, 0.30,
                0.25, 0.75,
                1.8, 45, 4,
                12, 50,
                20);
    }
}
