package es.boffmedia.teras.dungeon.model;

import java.util.Set;

/**
 * Seed derivation for dungeon generation. The legacy {@code SeededRandom} hashed seed strings with
 * a transliterated JavaScript 32-bit hash (its {@code hash & hash} line is a JS idiom that is a
 * no-op in Java); this uses FNV-1a 64 so a printed seed reproduces a floor exactly, and splitmix64
 * to derive per-attempt and per-room sub-seeds from one base without correlation.
 */
public final class DungeonSeeds {

    private DungeonSeeds() {}

    /** FNV-1a 64-bit offset basis; also the hash of the empty string. */
    public static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public static long fnv1a64(String s) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * The base seed for one floor. The <b>canonical floor</b> and the curses fold in so every floor
     * of a run derives from the run seed yet differs, and the same three inputs always rebuild the
     * same floor.
     *
     * <p>The floor, not the run's stage: a challenge opening on floor ten and a full descent
     * reaching it must derive the same seed, or floor ten would build differently depending on how
     * it was arrived at — the rules would match and the layout would not.</p>
     */
    public static long baseSeed(int floor, Set<Curse> curses, String seedString) {
        StringBuilder canonical = new StringBuilder().append(floor).append('|').append(seedString);
        for (Curse curse : Curse.values()) {
            if (curses.contains(curse)) {
                canonical.append('|').append(curse.name());
            }
        }
        return fnv1a64(canonical.toString());
    }

    /** A sub-seed decorrelated from {@code seed} by {@code salt} (attempt number, room index…). */
    public static long derive(long seed, long salt) {
        return splitmix64(seed + salt * 0x9e3779b97f4a7c15L);
    }

    private static long splitmix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
