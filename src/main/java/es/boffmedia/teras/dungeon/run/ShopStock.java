package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.model.SeededRng;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * What a shop can sell and how a floor's pedestals are filled. Kept free of Minecraft — weights
 * arrive as a function rather than being read from the config here — so the two rules that matter
 * can be tested without a world: the guaranteed charge, and no shop stocking the same thing twice.
 *
 * <p>{@link DungeonShop} is the half that knows about pedestals, displays and purchases.</p>
 */
public final class ShopStock {
    private ShopStock() {}

    /** What a pedestal can hold. The lowercase names are the config keys under {@code tienda:}. */
    public enum StockKind {
        POCION,
        POCION_MAYOR,
        MAPA,
        BRUJULA,
        ROMPEMUROS,
        BENDICION_FUERZA,
        BENDICION_RESISTENCIA,
        BENDICION_VELOCIDAD,
        FENIX,
        SEGURO,
        CAJA_SORPRESA;

        public String configKey() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * A shop category — a shelf that must be represented before any is doubled up. The order is the
     * planogram's priority: with only four slots you still get a charge, a heal, a utility and a
     * blessing; the gamble and the premium come in as the counter gets longer.
     */
    private static final List<List<StockKind>> CATEGORIES = List.of(
            List.of(StockKind.ROMPEMUROS),                                          // the charge
            List.of(StockKind.POCION, StockKind.POCION_MAYOR),                       // healing
            List.of(StockKind.MAPA, StockKind.BRUJULA),                             // utility
            List.of(StockKind.BENDICION_FUERZA, StockKind.BENDICION_RESISTENCIA,
                    StockKind.BENDICION_VELOCIDAD),                                 // a blessing
            List.of(StockKind.CAJA_SORPRESA),                                       // the gamble
            List.of(StockKind.FENIX, StockKind.SEGURO));                            // premium

    /** How far down {@link #CATEGORIES} the premium shelf sits — gated by stage, not by slot count. */
    private static final int PREMIUM_INDEX = 5;

    /**
     * The floor's stock: <b>a curated planogram, not a raw roll.</b> One kind per category in
     * priority order, so a shop always <i>reads</i> as stocked — a charge, then healing, utility, a
     * blessing, the gamble, and (once the floor can afford it) a premium — then weighted extras only
     * if the counter is longer than the catalogue.
     *
     * <p>The old pure weighted-no-repeat could hand a party three blessings and no healing: random
     * <i>and</i> sparse, which is exactly what "felt empty". A guaranteed spread is what the genre's
     * shops do (Isaac, Spire), and it is the whole reason to roll the inventory <b>per floor</b>
     * rather than per pedestal — you can only guarantee a set if you draw it as a set (PISOS §66).</p>
     *
     * <p>The first slot is still always the charge: secret walls cost one, so a shop that stocked
     * none would lock a floor's secrets away.</p>
     */
    public static List<StockKind> planogram(int slotCount, int stage, int premiumStage,
                                            SeededRng rng, ToIntFunction<StockKind> weights) {
        List<StockKind> picked = new ArrayList<>();
        if (slotCount <= 0) {
            return picked;
        }
        Set<StockKind> used = new LinkedHashSet<>();
        for (int c = 0; c < CATEGORIES.size() && picked.size() < slotCount; c++) {
            if (c == PREMIUM_INDEX && stage < premiumStage) {
                // Too early for fénix/seguro: skip the shelf and let an extra take the slot instead,
                // so an early shop is not one pedestal shorter than a late one.
                continue;
            }
            StockKind pick = pickWeighted(CATEGORIES.get(c), used, rng, weights);
            if (pick == null) {
                pick = CATEGORIES.get(c).get(0);   // every weight zero: still show the shelf
            }
            used.add(pick);
            picked.add(pick);
        }
        // The extras that fill an over-long counter draw from everything EXCEPT premium stock that
        // has not reached its floor — otherwise fénix could sneak in as an extra the same floor its
        // own shelf was gated out.
        List<StockKind> extraPool = new ArrayList<>();
        for (StockKind kind : StockKind.values()) {
            boolean premium = CATEGORIES.get(PREMIUM_INDEX).contains(kind);
            if (!premium || stage >= premiumStage) {
                extraPool.add(kind);
            }
        }
        while (picked.size() < slotCount) {
            StockKind extra = pickWeighted(extraPool, used, rng, weights);
            if (extra == null) {
                // Catalogue exhausted or weightless: repeat rather than leave a pedestal empty.
                picked.add(StockKind.POCION);
                continue;
            }
            used.add(extra);
            picked.add(extra);
        }
        return picked;
    }

    private static StockKind pickWeighted(List<StockKind> among, Set<StockKind> exclude,
                                          SeededRng rng, ToIntFunction<StockKind> weights) {
        Map<StockKind, Integer> pool = new EnumMap<>(StockKind.class);
        int total = 0;
        for (StockKind kind : among) {
            if (exclude.contains(kind)) {
                continue;
            }
            int weight = weights.applyAsInt(kind);
            if (weight > 0) {
                pool.put(kind, weight);
                total += weight;
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = rng.between(0, total - 1);
        for (Map.Entry<StockKind, Integer> entry : pool.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }
}
