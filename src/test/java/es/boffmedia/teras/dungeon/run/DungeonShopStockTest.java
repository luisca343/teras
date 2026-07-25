package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.model.SeededRng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a shop puts on its counter (PISOS §66): the guaranteed charge, a curated spread with no
 * repeats, premium stock gated by stage, and the same seed stocking the same shop.
 */
class DungeonShopStockTest {

    private static final int PREMIUM_STAGE = 3;

    /** The shipped default weights, so the test does not need a loaded config (or a world). */
    private static List<ShopStock.StockKind> plan(int slots, int stage, SeededRng rng) {
        return ShopStock.planogram(slots, stage, PREMIUM_STAGE, rng, kind -> switch (kind) {
            case POCION -> 20;
            case POCION_MAYOR, CAJA_SORPRESA -> 10;
            case MAPA, BRUJULA -> 12;
            case ROMPEMUROS -> 14;
            case BENDICION_FUERZA, BENDICION_RESISTENCIA, BENDICION_VELOCIDAD, SEGURO -> 8;
            case FENIX -> 5;
        });
    }

    /**
     * Secret walls cost a charge now, so a shop that rolled none would lock a floor's secrets away
     * entirely. The first slot is never anything else.
     */
    @Test
    void theFirstSlotIsAlwaysAWallBreakerCharge() {
        for (int seed = 0; seed < 50; seed++) {
            List<ShopStock.StockKind> stock = plan(4, 1, new SeededRng(seed));
            assertEquals(ShopStock.StockKind.ROMPEMUROS, stock.get(0), "seed " + seed);
        }
    }

    @Test
    void fillsExactlyAsManySlotsAsThereArePedestals() {
        assertEquals(1, plan(1, 1, new SeededRng(3)).size());
        assertEquals(4, plan(4, 1, new SeededRng(3)).size());
        assertTrue(plan(0, 1, new SeededRng(3)).isEmpty());
    }

    /** The planogram is a curated spread — no shelf doubled up while the catalogue has room. */
    @Test
    void aShopNeverStocksTheSameKindTwice() {
        for (int seed = 0; seed < 50; seed++) {
            List<ShopStock.StockKind> stock = plan(6, 5, new SeededRng(seed));
            assertEquals(stock.size(), stock.stream().distinct().count(), "seed " + seed);
        }
    }

    /** A four-slot shop always shows the essentials: a charge, a heal, a utility and a blessing. */
    @Test
    void aSmallShopStillCoversTheEssentials() {
        for (int seed = 0; seed < 30; seed++) {
            List<ShopStock.StockKind> stock = plan(4, 1, new SeededRng(seed));
            assertTrue(stock.contains(ShopStock.StockKind.ROMPEMUROS), "charge, seed " + seed);
            assertTrue(stock.stream().anyMatch(k -> k == ShopStock.StockKind.POCION
                    || k == ShopStock.StockKind.POCION_MAYOR), "healing, seed " + seed);
            assertTrue(stock.stream().anyMatch(k -> k == ShopStock.StockKind.MAPA
                    || k == ShopStock.StockKind.BRUJULA), "utility, seed " + seed);
        }
    }

    /** Premium stock (fénix, seguro) does not appear before its floor, however long the counter. */
    @Test
    void premiumStockWaitsForItsFloor() {
        for (int seed = 0; seed < 30; seed++) {
            List<ShopStock.StockKind> early = plan(6, PREMIUM_STAGE - 1, new SeededRng(seed));
            assertTrue(early.stream().noneMatch(k -> k == ShopStock.StockKind.FENIX
                    || k == ShopStock.StockKind.SEGURO), "seed " + seed + " leaked premium early");
        }
    }

    @Test
    void theSameSeedStocksTheSameShop() {
        assertEquals(plan(6, 5, new SeededRng(4242)), plan(6, 5, new SeededRng(4242)));
    }

    /** Enough pedestals to exhaust the catalogue must not hang or leave a slot empty. */
    @Test
    void moreSlotsThanKindsStillFills() {
        int kinds = ShopStock.StockKind.values().length;
        List<ShopStock.StockKind> stock = plan(kinds + 3, 5, new SeededRng(8));

        assertEquals(kinds + 3, stock.size());
        assertTrue(stock.stream().noneMatch(java.util.Objects::isNull));
    }
}
