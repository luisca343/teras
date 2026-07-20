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

    /**
     * A copy of {@code options} in random order — a Fisher–Yates on this generator, not
     * {@code Collections.shuffle}, so the draws stay on the one sequence a seed reproduces.
     */
    public <T> List<T> shuffled(List<T> options) {
        List<T> copy = new java.util.ArrayList<>(options);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T swap = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, swap);
        }
        return copy;
    }
}
