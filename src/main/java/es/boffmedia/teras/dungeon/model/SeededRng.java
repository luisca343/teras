package es.boffmedia.teras.dungeon.model;

import java.util.List;
import java.util.SplittableRandom;

/**
 * The generator's randomness, behind one small surface so every draw is attributable and the
 * algorithm is specified ({@link SplittableRandom} is, bit for bit — {@code java.util.Random} only
 * kept 48 of the legacy hash's 32 meaningful bits).
 */
public final class SeededRng {

    private final SplittableRandom random;

    public SeededRng(long seed) {
        this.random = new SplittableRandom(seed);
    }

    public boolean chance(double p) {
        return random.nextDouble() < p;
    }

    /** Uniform integer in {@code [min, max]}, both inclusive. */
    public int between(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    public <T> T pick(List<T> options) {
        return options.get(random.nextInt(options.size()));
    }
}
