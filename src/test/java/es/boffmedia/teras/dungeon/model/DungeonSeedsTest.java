package es.boffmedia.teras.dungeon.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DungeonSeedsTest {

    /** Published FNV-1a 64 test vectors — a printed seed must reproduce forever. */
    @Test
    void fnv1a64MatchesKnownVectors() {
        assertEquals(DungeonSeeds.FNV_OFFSET, DungeonSeeds.fnv1a64(""));
        assertEquals(0xaf63dc4c8601ec8cL, DungeonSeeds.fnv1a64("a"));
        assertEquals(0x85944171f73967e8L, DungeonSeeds.fnv1a64("foobar"));
    }

    @Test
    void baseSeedFoldsInStageAndCurses() {
        long plain = DungeonSeeds.baseSeed(1, Set.of(), "seed");
        assertNotEquals(plain, DungeonSeeds.baseSeed(2, Set.of(), "seed"));
        assertNotEquals(plain, DungeonSeeds.baseSeed(1, Set.of(Curse.LABYRINTH), "seed"));
        assertNotEquals(plain, DungeonSeeds.baseSeed(1, Set.of(), "seed2"));
        assertEquals(plain, DungeonSeeds.baseSeed(1, Set.of(), "seed"));
    }

    @Test
    void curseOrderDoesNotChangeTheSeed() {
        assertEquals(
                DungeonSeeds.baseSeed(3, Set.of(Curse.LABYRINTH, Curse.LOST), "s"),
                DungeonSeeds.baseSeed(3, Set.of(Curse.LOST, Curse.LABYRINTH), "s"));
    }

    @Test
    void deriveDecorrelatesAttempts() {
        long seed = DungeonSeeds.fnv1a64("base");
        assertNotEquals(DungeonSeeds.derive(seed, 0), DungeonSeeds.derive(seed, 1));
        assertNotEquals(DungeonSeeds.derive(seed, 1), DungeonSeeds.derive(seed, 2));
        assertEquals(DungeonSeeds.derive(seed, 5), DungeonSeeds.derive(seed, 5));
    }
}
