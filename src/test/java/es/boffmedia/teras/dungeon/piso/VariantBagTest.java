package es.boffmedia.teras.dungeon.piso;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The draw. Two things have to hold at once: a seed rebuilds the same floor, and a floor does not
 * read as though the piso owns one room.
 */
class VariantBagTest {

    private static List<RoomVariant> pool(double... weights) {
        List<RoomVariant> pool = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            pool.add(new RoomVariant("v" + i, "teras:dungeon/cuevas/normal/v" + i, weights[i]));
        }
        return pool;
    }

    private static List<String> deal(List<RoomVariant> pool, long seed, int count) {
        List<String> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drawn.add(VariantBag.draw(pool, seed, 7L, i).name());
        }
        return drawn;
    }

    @Test
    void theSameSeedDealsTheSameSequence() {
        assertEquals(deal(pool(1, 1, 1), 1234L, 12), deal(pool(1, 1, 1), 1234L, 12));
    }

    @Test
    void differentSeedsDealDifferently() {
        assertTrue(deal(pool(1, 1, 1, 1), 1L, 16).equals(deal(pool(1, 1, 1, 1), 1L, 16)));
        assertTrue(!deal(pool(1, 1, 1, 1), 1L, 16).equals(deal(pool(1, 1, 1, 1), 2L, 16)));
    }

    /**
     * The reason the bag exists. Independent rolls over three rooms repeat immediately about a third
     * of the time; a cycle can only repeat across its boundary, so a window the size of the pool
     * always holds one of each.
     */
    @Test
    void everyVariantAppearsOncePerCycle() {
        List<String> dealt = deal(pool(1, 1, 1), 99L, 9);
        for (int cycle = 0; cycle < 3; cycle++) {
            assertEquals(3, List.copyOf(dealt.subList(cycle * 3, cycle * 3 + 3)).stream()
                    .distinct().count(), "cycle " + cycle + " repeated a room: " + dealt);
        }
    }

    /** Weights are relative, and the smallest sets the scale. */
    @Test
    void aRareVariantIsRareInProportion() {
        List<RoomVariant> pool = pool(1, 1, 1, 0.2);
        assertEquals(16, VariantBag.bag(pool).size(), "5+5+5+1");
        Map<String, Integer> counts = new HashMap<>();
        for (String name : deal(pool, 4321L, 1600)) {
            counts.merge(name, 1, Integer::sum);
        }
        assertEquals(100, counts.get("v3"), "exactly one per cycle, 100 cycles");
        assertEquals(500, counts.get("v0"));
    }

    /** A weight so extreme it would deal a bag of thousands is scaled, not truncated. */
    @Test
    void anAbsurdWeightRatioStillDealsEveryVariant() {
        List<RoomVariant> bag = VariantBag.bag(pool(1000, 1));
        assertTrue(bag.size() <= 64, "bag was " + bag.size());
        assertTrue(bag.stream().anyMatch(v -> v.name().equals("v1")),
                "scaling down must never drop a variant out of the bag entirely");
    }

    @Test
    void aSinglingtonPoolAlwaysDrawsItAndAnEmptyOneDrawsNothing() {
        assertEquals("v0", VariantBag.draw(pool(1), 5L, 0L, 41).name());
        assertNull(VariantBag.draw(List.of(), 5L, 0L, 0));
    }
}
