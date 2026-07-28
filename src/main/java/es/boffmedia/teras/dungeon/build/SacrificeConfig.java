package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/**
 * The sacrifice altar: each step on the spikes costs health — which nothing but a dungeon potion
 * gives back — and improves the odds of the payout.
 */
public record SacrificeConfig(float damage, int baseChancePct, int stepChancePct,
                              int coinsMin, int coinsMax) {

    static SacrificeConfig defaults() {
        return new SacrificeConfig(4.0f, 10, 10, 20, 40);
    }

    static SacrificeConfig read(YamlConfig yaml, SacrificeConfig previous) {
        YamlConfig sacrifice = yaml.section("sacrificio");
        return new SacrificeConfig(
                sacrifice.integer("dano", Math.round(previous.damage)),
                sacrifice.integer("probBasePct", previous.baseChancePct),
                sacrifice.integer("probPorPasoPct", previous.stepChancePct),
                sacrifice.integer("monedasMin", previous.coinsMin),
                sacrifice.integer("monedasMax", previous.coinsMax));
    }
}
