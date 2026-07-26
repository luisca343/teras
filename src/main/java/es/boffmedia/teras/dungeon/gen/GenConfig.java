package es.boffmedia.teras.dungeon.gen;

/**
 * Generation tuning, one record instead of the legacy {@code DungeonConfig} constant-holder. The
 * numbers are the legacy values (which are Isaac's), except {@code largeShapeDecay}: the legacy
 * "normalization" divided large-shape odds by an accidental {@code largeSum + 1.0} on top of
 * halving them; the decay factor is that intent made explicit.
 *
 * <p>{@code celdas} is the cell budget of each canonical floor, indexed from floor one, and
 * {@code canonicalFloors} is simply how many entries it has — the depth of the sequence is the
 * length of the curve, so the two cannot drift. It replaces both the Isaac formula
 * ({@code min(20, coin + 5 + stage*10/3)}) and the {@code finalStageRooms} override: the first
 * saturated at floor five, so no floor past it could be authored as deeper than another, and the
 * second was a flat 50 applied to whichever floor a dungeon happened to end on, which made a
 * two-floor dungeon's second floor the size of a twelve-floor climax. Entries one through six are
 * still exactly what the formula produced; the rest are authored.</p>
 *
 * <p>A floor is indexed by its <i>canonical</i> number, never by its position in the current run —
 * see {@link FloorDepth}. That is what lets a one-floor challenge opening on floor ten build the
 * floor ten of the full descent rather than a floor one.</p>
 *
 * <p>{@code gridSize} is the playfield — the diameter the carve, placement and validation see,
 * untouched by anything below. {@code postMargin} is reserved space <i>around</i> it: the built
 * grid is {@code gridSize + 2·postMargin}, and the ring only ever holds rooms appended after
 * validation ({@link PostRooms} — the exit chamber today, red-room-style extras tomorrow), so a
 * post room always has somewhere to stand and can never cost a generation attempt.</p>
 *
 * <p>{@code forceBossQuad} rejects any floor whose boss did not grow to a 2×2, rerolling for one
 * that did. Set only for pisos that declare QUAD (and therefore authored {@code boss_big}): the
 * boss is then always a 2×2 arena, which is both grander and what lets the exit share a full,
 * aligned face for the centered ceremonial door. It costs a few percent more generation attempts
 * — the boss already grows on ~92–95% of floors — and never bites a piso that does not build 2×2
 * rooms.</p>
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
        int labyrinthCellCap,
        int lostRoomBonus,
        java.util.List<Integer> celdas,
        int jitter,
        int maxAttempts,
        boolean exitRoom,
        int postMargin,
        boolean forceBossQuad) {

    public GenConfig {
        if (gridSize < 5) {
            throw new IllegalArgumentException("gridSize must be at least 5: " + gridSize);
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive: " + maxAttempts);
        }
        if (celdas == null || celdas.isEmpty()) {
            throw new IllegalArgumentException("celdas must name at least one floor");
        }
        for (int i = 0; i < celdas.size(); i++) {
            Integer cells = celdas.get(i);
            if (cells == null || cells < 1) {
                throw new IllegalArgumentException(
                        "celdas[" + i + "] is " + cells + "; every floor needs a positive budget");
            }
        }
        if (jitter < 0) {
            throw new IllegalArgumentException("jitter cannot be negative: " + jitter);
        }
        celdas = java.util.List.copyOf(celdas);
    }

    /** How deep the canonical sequence goes — the length of the curve, not a second declaration. */
    public int canonicalFloors() {
        return celdas.size();
    }

    /**
     * The cell budget of a canonical floor, before jitter and curses. Floors past the end of the
     * curve take its last entry rather than throwing: a dungeon window reaching past the sequence
     * is refused at config load ({@code DungeonDef.problems}), and generation is not the place to
     * discover it.
     */
    public int cellsFor(int floor) {
        int index = Math.max(1, Math.min(canonicalFloors(), floor)) - 1;
        return celdas.get(index);
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
     *
     * <p><b>These are per-roll odds, not frequencies.</b> {@link RoomCarver} offers the shapes
     * biggest-first, so a 2×2 gets first refusal at every frontier cell and the smaller shapes are
     * only rolled when it declines — which is why QUAD at {@code 0.15} produces more rooms than
     * HORIZONTAL and VERTICAL at {@code 0.20} each put together. Halving a weight makes that family
     * rarer, dependably; it does not halve its share of the floor, and the four numbers cannot be
     * read against each other as a distribution.</p>
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
                labyrinthMultiplier, labyrinthCellCap, lostRoomBonus,
                celdas, jitter, maxAttempts, exitRoom, postMargin,
                forceBossQuad);
    }

    /**
     * This config with the curve a server authored. Kept separate from {@link #defaults()} so the
     * factory stays a pure constant — tests and the generator itself must not depend on a config
     * file having been read.
     */
    public GenConfig withCurve(java.util.List<Integer> curve, int jitterValue) {
        if (curve == null || curve.isEmpty()) {
            return this;
        }
        return new GenConfig(gridSize,
                chanceQuad, chanceHorizontal, chanceVertical, chanceLShape,
                largeShapeDecay, shapeResetInterval,
                curseRoomChance, challengeRoomChance, sacrificeRoomChance, arcadeRoomChance,
                devilDealChance, miniBossChance, firstStageMiniBossBoost,
                labyrinthMultiplier, labyrinthCellCap, lostRoomBonus,
                curve, Math.max(0, jitterValue), maxAttempts, exitRoom, postMargin, forceBossQuad);
    }

    /**
     * This config generating (or not) the appended sala del sello. Decided per floor by whether
     * the piso authored an {@code exit} template — the generator itself must not know what a piso
     * is, and a layout with an exit room the build cannot furnish would be a sealed doorway into
     * an empty cell.
     */
    public GenConfig withExitRoom(boolean exit) {
        if (exit == exitRoom) {
            return this;
        }
        return new GenConfig(gridSize,
                chanceQuad, chanceHorizontal, chanceVertical, chanceLShape,
                largeShapeDecay, shapeResetInterval,
                curseRoomChance, challengeRoomChance, sacrificeRoomChance, arcadeRoomChance,
                devilDealChance, miniBossChance, firstStageMiniBossBoost,
                labyrinthMultiplier, labyrinthCellCap, lostRoomBonus,
                celdas, jitter, maxAttempts, exit, postMargin, forceBossQuad);
    }

    /**
     * This config requiring (or not) a 2×2 boss. Set true only for pisos that declare QUAD — a
     * piso without {@code boss_big} could never satisfy it and would reroll every floor to
     * exhaustion.
     */
    public GenConfig withForceBossQuad(boolean force) {
        if (force == forceBossQuad) {
            return this;
        }
        return new GenConfig(gridSize,
                chanceQuad, chanceHorizontal, chanceVertical, chanceLShape,
                largeShapeDecay, shapeResetInterval,
                curseRoomChance, challengeRoomChance, sacrificeRoomChance, arcadeRoomChance,
                devilDealChance, miniBossChance, firstStageMiniBossBoost,
                labyrinthMultiplier, labyrinthCellCap, lostRoomBonus,
                celdas, jitter, maxAttempts, exitRoom, postMargin, force);
    }

    private static double weight(
            java.util.Map<es.boffmedia.teras.dungeon.model.ShapeFamily, Double> weights,
            es.boffmedia.teras.dungeon.model.ShapeFamily family) {
        Double value = weights.get(family);
        return value == null ? 1.0 : Math.max(0.0, value);
    }

    /**
     * Floors one to six are the Isaac formula's own values, so the floors that have content today
     * are untouched. Seven to twelve are authored, and they are the reason the curve is a table:
     * the formula's {@code min(20, …)} saturated at floor five, which made every floor past it the
     * same size and left no way to say that the tenth is deeper than the seventh.
     */
    private static final java.util.List<Integer> DEFAULT_CELDAS =
            java.util.List.of(10, 13, 17, 20, 22, 22, 24, 26, 28, 30, 34, 40);

    public static GenConfig defaults() {
        return new GenConfig(
                13,
                0.15, 0.20, 0.20, 0.10,
                0.5, 10,
                0.5, 0.5,
                0.35, 0.35, 0.30,
                0.25, 0.75,
                1.8, 45, 4,
                DEFAULT_CELDAS, 2,
                20, false, 2, false);
    }
}
