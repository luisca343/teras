package es.boffmedia.teras.dungeon.gear;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loot tables ask for a rarity; the catalog decides which piece.
 *
 * <p>The invariant worth the most here is the last test: <b>every rarity has to be fillable</b>. A
 * table that rolls epic against a catalog with no epic piece produces an empty stack, which is
 * indistinguishable from a table that meant to give nothing — the same silent-blank shape that made
 * the boss pedestal look broken when {@code boss.json} was 57% {@code minecraft:empty}.</p>
 */
class GearRollTest {

    private static final Map<GearDef.Rarity, List<String>> FULL = Map.of(
            GearDef.Rarity.COMUN, List.of("c1", "c2"),
            GearDef.Rarity.RARO, List.of("r1"),
            GearDef.Rarity.EPICO, List.of("e1", "e2", "e3"));

    @Test
    void rarityFollowsTheWeights() {
        GearRoll.Odds odds = new GearRoll.Odds(50, 30, 20);
        assertEquals(GearDef.Rarity.COMUN, GearRoll.rollRarity(odds, 0.0));
        assertEquals(GearDef.Rarity.COMUN, GearRoll.rollRarity(odds, 0.49));
        assertEquals(GearDef.Rarity.RARO, GearRoll.rollRarity(odds, 0.5));
        assertEquals(GearDef.Rarity.RARO, GearRoll.rollRarity(odds, 0.79));
        assertEquals(GearDef.Rarity.EPICO, GearRoll.rollRarity(odds, 0.8));
        assertEquals(GearDef.Rarity.EPICO, GearRoll.rollRarity(odds, 0.999999));
    }

    @Test
    void aZeroWeightRarityIsNeverDrawn() {
        GearRoll.Odds odds = new GearRoll.Odds(1, 0, 1);
        for (int i = 0; i < 100; i++) {
            assertFalse(GearRoll.rollRarity(odds, i / 100.0) == GearDef.Rarity.RARO);
        }
    }

    @Test
    void allZeroWeightsDrawNothing() {
        assertNull(GearRoll.rollRarity(new GearRoll.Odds(0, 0, 0), 0.5));
        assertNull(GearRoll.roll(new GearRoll.Odds(0, 0, 0), FULL, 0.5, 0.5));
    }

    @Test
    void theWholeListIsReachable() {
        GearRoll.Odds epicOnly = new GearRoll.Odds(0, 0, 1);
        assertEquals("e1", GearRoll.roll(epicOnly, FULL, 0.5, 0.0));
        assertEquals("e2", GearRoll.roll(epicOnly, FULL, 0.5, 0.5));
        assertEquals("e3", GearRoll.roll(epicOnly, FULL, 0.5, 0.999999));
    }

    @Test
    void aRarityTheCatalogCannotFillFallsBackInsteadOfDroppingNothing() {
        Map<GearDef.Rarity, List<String>> noEpic = Map.of(
                GearDef.Rarity.COMUN, List.of("c1"),
                GearDef.Rarity.RARO, List.of("r1"),
                GearDef.Rarity.EPICO, List.of());
        String drawn = GearRoll.roll(new GearRoll.Odds(0, 0, 1), noEpic, 0.5, 0.5);
        assertEquals("r1", drawn, "epic falls to rare, the nearest populated rarity");
    }

    @Test
    void anEmptyCatalogDrawsNothingRatherThanThrowing() {
        Map<GearDef.Rarity, List<String>> empty = Map.of(
                GearDef.Rarity.COMUN, List.of(),
                GearDef.Rarity.RARO, List.of(),
                GearDef.Rarity.EPICO, List.of());
        assertNull(GearRoll.roll(new GearRoll.Odds(1, 1, 1), empty, 0.5, 0.5));
    }

    @Test
    void groupingIsStableAcrossCatalogOrder() {
        Map<GearDef.Rarity, List<String>> grouped = GearRoll.byRarity(GearDefs.defaults());
        Map<GearDef.Rarity, List<String>> again = GearRoll.byRarity(GearDefs.defaults());
        assertEquals(grouped, again);
        for (List<String> ids : grouped.values()) {
            List<String> sorted = ids.stream().sorted().toList();
            assertEquals(sorted, ids, "ids are sorted so a seeded run draws the same piece twice");
        }
    }

    /**
     * The shipped catalog must be able to answer every rarity a table can ask for. If it cannot,
     * the fallback quietly hands out the wrong tier forever and nothing says so.
     */
    @Test
    void theShippedCatalogFillsEveryRarity() {
        Map<GearDef.Rarity, List<String>> grouped = GearRoll.byRarity(GearDefs.defaults());
        for (GearDef.Rarity rarity : GearDef.Rarity.values()) {
            List<String> ids = grouped.get(rarity);
            assertNotNull(ids);
            assertFalse(ids.isEmpty(),
                    "no shipped gear of rarity " + rarity + " — every table rolling it would fall "
                            + "back to another tier with nothing to warn the server owner");
        }
    }

    @Test
    void everyShippedPieceIsReachableFromSomeRoll() {
        Map<GearDef.Rarity, List<String>> grouped = GearRoll.byRarity(GearDefs.defaults());
        for (Map.Entry<String, GearDef> entry : GearDefs.defaults().entrySet()) {
            assertTrue(grouped.get(entry.getValue().rarity()).contains(entry.getKey()),
                    entry.getKey() + " is in the catalog but no rarity bucket can draw it");
        }
    }
}
