package es.boffmedia.teras.dungeon.gen;

/**
 * Where a floor sits in its run, for the generator. Three questions that used to be one {@code int
 * stage} and are only the same number in a dungeon of the reference length:
 *
 * <ul>
 *   <li>{@link #isFirst()} — the party's first floor, with no coins and no gear yet. What
 *       {@code SpecialRoomPlacer} means when it withholds the shop, arcade and devil deal.</li>
 *   <li>{@link #isFinal()} — the last floor of <i>this</i> dungeon, whatever its length.</li>
 *   <li>{@link #curveStage()} — how deep this floor is <i>proportionally</i>, which is what the
 *       room-count and difficulty curves are authored against.</li>
 * </ul>
 *
 * <p>The curve is authored for a {@code referenceLength}-floor dungeon, so a dungeon of another
 * length has its floors mapped onto it: a six-floor dungeon's third floor sits where a twelve-floor
 * dungeon's sixth does. Without this a two-floor dungeon would generate two starter floors and never
 * reach its own climax, and a twenty-floor one would plateau by a quarter of the way in.</p>
 *
 * <p>The last floor always maps exactly onto the reference length — {@code length * ref / length} —
 * so "the final floor" and "the end of the curve" cannot drift apart however odd the length.</p>
 */
public record FloorDepth(int stage, int dungeonLength, int referenceLength) {

    public FloorDepth {
        if (stage < 1) {
            throw new IllegalArgumentException("stage is 1-based: " + stage);
        }
        if (dungeonLength < 1) {
            throw new IllegalArgumentException("dungeonLength must be positive: " + dungeonLength);
        }
        if (referenceLength < 1) {
            throw new IllegalArgumentException("referenceLength must be positive: " + referenceLength);
        }
    }

    /** A floor of a dungeon exactly as long as the curve was authored for. */
    public static FloorDepth of(GenConfig config, int stage) {
        return new FloorDepth(stage, config.referenceLength(), config.referenceLength());
    }

    /** A floor of a dungeon of any length. */
    public static FloorDepth of(GenConfig config, int stage, int dungeonLength) {
        return new FloorDepth(stage, dungeonLength, config.referenceLength());
    }

    public boolean isFirst() {
        return stage == 1;
    }

    public boolean isFinal() {
        return stage == dungeonLength;
    }

    public boolean isValid() {
        return stage >= 1 && stage <= dungeonLength;
    }

    /**
     * This floor's position on the authored difficulty curve, in {@code 1..referenceLength}.
     * Identical to {@link #stage()} when the dungeon is the reference length.
     */
    public int curveStage() {
        int mapped = stage * referenceLength / dungeonLength;
        return Math.max(1, Math.min(referenceLength, mapped));
    }
}
