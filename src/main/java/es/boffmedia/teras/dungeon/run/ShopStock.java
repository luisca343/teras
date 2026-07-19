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
     * The floor's stock list: a wall-breaker charge first, then weighted picks without repeats.
     *
     * <p>The first slot is not a roll. Secret walls cost a charge, so a shop that happened to stock
     * none would lock a floor's secrets away entirely — the one purchase a party must always be
     * able to make.</p>
     */
    public static List<StockKind> roll(int slotCount, SeededRng rng, ToIntFunction<StockKind> weights) {
        List<StockKind> picked = new ArrayList<>();
        if (slotCount <= 0) {
            return picked;
        }
        picked.add(StockKind.ROMPEMUROS);
        Set<StockKind> used = new LinkedHashSet<>(picked);
        while (picked.size() < slotCount) {
            StockKind next = pickWeighted(used, rng, weights);
            if (next == null) {
                // Every kind is either already stocked or weightless: repeat rather than leave a
                // pedestal standing empty.
                picked.add(StockKind.POCION);
                continue;
            }
            used.add(next);
            picked.add(next);
        }
        return picked;
    }

    private static StockKind pickWeighted(Set<StockKind> exclude, SeededRng rng,
                                          ToIntFunction<StockKind> weights) {
        Map<StockKind, Integer> pool = new EnumMap<>(StockKind.class);
        int total = 0;
        for (StockKind kind : StockKind.values()) {
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
