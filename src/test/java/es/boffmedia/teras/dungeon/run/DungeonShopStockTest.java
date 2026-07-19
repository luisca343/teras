package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.model.SeededRng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a shop puts on its pedestals: the guaranteed charge, no repeats, same seed same stock. */
class DungeonShopStockTest {

    /** The shipped default weights, so the test does not need a loaded config (or a world). */
    private static List<ShopStock.StockKind> roll(int slots, SeededRng rng) {
        return ShopStock.roll(slots, rng, kind -> switch (kind) {
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
     * entirely. The first slot is not a weighted pick.
     */
    @Test
    void theFirstSlotIsAlwaysAWallBreakerCharge() {
        for (int seed = 0; seed < 50; seed++) {
            List<ShopStock.StockKind> stock = roll(3, new SeededRng(seed));
            assertEquals(ShopStock.StockKind.ROMPEMUROS, stock.get(0), "seed " + seed);
        }
    }

    @Test
    void rollsExactlyAsManyKindsAsThereArePedestals() {
        assertEquals(1, roll(1, new SeededRng(3)).size());
        assertEquals(4, roll(4, new SeededRng(3)).size());
        assertTrue(roll(0, new SeededRng(3)).isEmpty());
    }

    /** Three of the same potion is not a choice; a shop's slots have to differ. */
    @Test
    void aShopNeverStocksTheSameKindTwice() {
        for (int seed = 0; seed < 50; seed++) {
            List<ShopStock.StockKind> stock = roll(4, new SeededRng(seed));
            assertEquals(stock.size(), stock.stream().distinct().count(), "seed " + seed);
        }
    }

    @Test
    void theSameSeedStocksTheSameShop() {
        assertEquals(roll(4, new SeededRng(4242)),
                roll(4, new SeededRng(4242)));
    }

    /** Enough pedestals to exhaust the catalog must not hang or leave a slot empty. */
    @Test
    void moreSlotsThanKindsStillFills() {
        int kinds = ShopStock.StockKind.values().length;
        List<ShopStock.StockKind> stock = roll(kinds + 3, new SeededRng(8));

        assertEquals(kinds + 3, stock.size());
        assertTrue(stock.stream().noneMatch(java.util.Objects::isNull));
    }
}
