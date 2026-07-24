package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run-long half of the draw. {@link VariantBagTest} covers a single floor; this covers the gap
 * that left — the room every floor has exactly one of.
 */
class VariantDrawTest {

    private static List<RoomVariant> pool(int size) {
        List<RoomVariant> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            pool.add(new RoomVariant("v" + i, "teras:dungeon/cuevas/start/v" + i, 1.0));
        }
        return pool;
    }

    private static final long KEY_SALT = DungeonSeeds.fnv1a64("start");

    /** What a once-per-floor key drew on each floor of a run, under the run bag. */
    private static List<String> acrossFloors(int variants, String seed, int floors) {
        List<String> drawn = new ArrayList<>();
        for (int floor = 0; floor < floors; floor++) {
            VariantDraw draw = new VariantDraw(DungeonSeeds.fnv1a64(seed), floor);
            drawn.add(VariantBag.draw(pool(variants), draw.seed(), KEY_SALT,
                    draw.pisoOrdinal()).name());
        }
        return drawn;
    }

    /**
     * The bug this exists for. Seeding per floor means two floors shuffle from unrelated bags and
     * position 0 of each is an independent roll — so the start chamber of a two-floor Cripta
     * repeated about a third of the time however many variants were authored.
     */
    @Test
    void theOldPerFloorSeedingRepeatsAcrossFloors() {
        int repeats = 0;
        for (int run = 0; run < 300; run++) {
            String seed = "semilla" + run;
            String first = VariantBag.draw(pool(3),
                    DungeonSeeds.baseSeed(1, EnumSet.noneOf(Curse.class), seed), KEY_SALT, 0).name();
            String second = VariantBag.draw(pool(3),
                    DungeonSeeds.baseSeed(2, EnumSet.noneOf(Curse.class), seed), KEY_SALT, 0).name();
            if (first.equals(second)) {
                repeats++;
            }
        }
        // A third of 300, give or take: this is the measurement, not an aspiration.
        assertTrue(repeats > 60, "expected the memoryless case to repeat often, saw " + repeats);
    }

    /** And the fix: one bag, dealt across the run, cannot repeat inside a cycle. */
    @Test
    void theRunBagNeverRepeatsWithinACycle() {
        for (int run = 0; run < 300; run++) {
            List<String> dealt = acrossFloors(3, "semilla" + run, 3);
            assertEquals(3, new HashSet<>(dealt).size(),
                    "a cycle of three floors drew " + dealt);
        }
    }

    /** Past the cycle it deals again, which is what a bag is — every variant, once more. */
    @Test
    void aSecondCycleDealsThemAllAgain() {
        List<String> dealt = acrossFloors(3, "cripta", 6);
        assertEquals(Set.of("v0", "v1", "v2"), new HashSet<>(dealt.subList(0, 3)));
        assertEquals(Set.of("v0", "v1", "v2"), new HashSet<>(dealt.subList(3, 6)));
    }

    /** Same seed, same run, same rooms: the whole floor still rebuilds from its inputs. */
    @Test
    void theSameRunSeedDealsTheSameSequence() {
        assertEquals(acrossFloors(4, "reproducible", 8), acrossFloors(4, "reproducible", 8));
    }

    @Test
    void differentRunsDealDifferently() {
        assertNotEquals(acrossFloors(4, "uno", 8), acrossFloors(4, "dos", 8));
    }

    /**
     * The ordinal is per piso, so an alternating pool does not skip positions: Cuevas' second floor
     * is position 1 of Cuevas' bag whether or not an Infestadas floor happened in between.
     */
    @Test
    void thePisoOrdinalIgnoresOtherPisos() {
        VariantDraw first = new VariantDraw(DungeonSeeds.fnv1a64("s"), 0);
        VariantDraw second = new VariantDraw(DungeonSeeds.fnv1a64("s"), 1);
        assertEquals(0, first.pisoOrdinal());
        assertEquals(1, second.pisoOrdinal());
        assertNotEquals(
                VariantBag.draw(pool(3), first.seed(), KEY_SALT, first.pisoOrdinal()).name(),
                VariantBag.draw(pool(3), second.seed(), KEY_SALT, second.pisoOrdinal()).name());
    }

    /** A floor built outside a run is simply the first floor of a run of one. */
    @Test
    void aSingleBuildDealsPositionZero() {
        assertEquals(0, VariantDraw.single("x").pisoOrdinal());
        assertEquals(DungeonSeeds.fnv1a64("x"), VariantDraw.single("x").seed());
    }
}
