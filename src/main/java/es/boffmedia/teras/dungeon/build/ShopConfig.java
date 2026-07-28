package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shop's planogram and its two deals, plus the curse room's market — the rooms that price
 * things in coins.
 *
 * <p>Weights and prices are keyed by stock kind, exactly as the {@code tienda:} block writes them.
 * A kind absent from the map rolls at weight 0, which is how a server takes one out of the pool.</p>
 */
public record ShopConfig(Map<String, Integer> weights, Map<String, Integer> prices,
                         int gangaChance, int gangaDiscountPct, String gambleLootTable,
                         int premiumStage, int marketSlots, int marketReward, int purgePrice) {

    /** Shop stock kinds, as they are keyed in the config's {@code tienda:} block. */
    static final Map<String, Integer> DEFAULT_WEIGHTS = new LinkedHashMap<>();
    static final Map<String, Integer> DEFAULT_PRICES = new LinkedHashMap<>();

    static {
        DEFAULT_WEIGHTS.put("pocion", 20);
        DEFAULT_WEIGHTS.put("pocion_mayor", 10);
        DEFAULT_WEIGHTS.put("mapa", 12);
        DEFAULT_WEIGHTS.put("brujula", 12);
        DEFAULT_WEIGHTS.put("rompemuros", 14);
        DEFAULT_WEIGHTS.put("bendicion_fuerza", 8);
        DEFAULT_WEIGHTS.put("bendicion_resistencia", 8);
        DEFAULT_WEIGHTS.put("bendicion_velocidad", 8);
        DEFAULT_WEIGHTS.put("fenix", 5);
        DEFAULT_WEIGHTS.put("seguro", 8);
        DEFAULT_WEIGHTS.put("caja_sorpresa", 10);

        DEFAULT_PRICES.put("pocion", 10);
        DEFAULT_PRICES.put("pocion_mayor", 25);
        DEFAULT_PRICES.put("mapa", 15);
        DEFAULT_PRICES.put("brujula", 15);
        DEFAULT_PRICES.put("rompemuros", 20);
        DEFAULT_PRICES.put("bendicion_fuerza", 12);
        DEFAULT_PRICES.put("bendicion_resistencia", 12);
        DEFAULT_PRICES.put("bendicion_velocidad", 12);
        DEFAULT_PRICES.put("fenix", 40);
        DEFAULT_PRICES.put("seguro", 8);
        DEFAULT_PRICES.put("caja_sorpresa", 12);
    }

    static ShopConfig defaults() {
        return new ShopConfig(new LinkedHashMap<>(DEFAULT_WEIGHTS), new LinkedHashMap<>(DEFAULT_PRICES),
                40, 30, "teras:dungeon/gamble", 3, 3, 40, 30);
    }

    static ShopConfig read(YamlConfig yaml, ShopConfig previous) {
        YamlConfig shop = yaml.section("tienda");
        YamlConfig weightBlock = shop.section("pesos");
        YamlConfig priceBlock = shop.section("precios");
        Map<String, Integer> weights = new LinkedHashMap<>(previous.weights);
        Map<String, Integer> prices = new LinkedHashMap<>(previous.prices);
        for (String kind : DEFAULT_WEIGHTS.keySet()) {
            weights.put(kind, weightBlock.integer(kind, weights.get(kind)));
            prices.put(kind, priceBlock.integer(kind, prices.get(kind)));
        }
        return new ShopConfig(weights, prices,
                shop.integer("gangaProbabilidad", previous.gangaChance),
                shop.integer("gangaDescuentoPct", previous.gangaDiscountPct),
                shop.string("lootCaja", previous.gambleLootTable),
                shop.integer("pisoPremium", previous.premiumStage),
                yaml.integer("ofertasMaldicion", previous.marketSlots),
                yaml.integer("pagoAfliccion", previous.marketReward),
                yaml.integer("precioPurga", previous.purgePrice));
    }

    /** Roll weight of a stock kind; 0 keeps it out of the pool entirely. */
    int weight(String kind) {
        return weights.getOrDefault(kind, 0);
    }

    /** Base price in coins, before per-floor scaling. */
    int price(String kind) {
        return prices.getOrDefault(kind, DEFAULT_PRICES.getOrDefault(kind, 10));
    }
}
