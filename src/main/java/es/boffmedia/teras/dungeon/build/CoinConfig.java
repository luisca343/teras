package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/**
 * The in-run currency: what enemies pay, how it is picked up, what a death costs and what it is
 * worth on the way out.
 *
 * <p>{@code clearReward} and {@code bankDeathPenaltyPct} are the ₽ knobs, both zero by default — a
 * run's income is coins, converted in one lump when it is completed.</p>
 */
public record CoinConfig(int normalMin, int normalMax, int miniBossMin, int miniBossMax,
                         int bossMin, int bossMax, int stageScalingPct, double pickupRadius,
                         int deathPenaltyPct, int toPesos,
                         long clearReward, int bankDeathPenaltyPct) {

    static CoinConfig defaults() {
        return new CoinConfig(1, 3, 8, 15, 20, 40, 15, 2, 20, 10, 0, 0);
    }

    static CoinConfig read(YamlConfig yaml, CoinConfig previous) {
        YamlConfig coins = yaml.section("monedas");
        return new CoinConfig(
                coins.integer("normalMin", previous.normalMin),
                coins.integer("normalMax", previous.normalMax),
                coins.integer("miniJefeMin", previous.miniBossMin),
                coins.integer("miniJefeMax", previous.miniBossMax),
                coins.integer("jefeMin", previous.bossMin),
                coins.integer("jefeMax", previous.bossMax),
                coins.integer("escaladoPorPisoPct", previous.stageScalingPct),
                Math.max(0.5, coins.integer("radioRecogida",
                        (int) Math.round(previous.pickupRadius))),
                coins.integer("muertePct", previous.deathPenaltyPct),
                coins.integer("cambioPesos", previous.toPesos),
                yaml.longValue("recompensaSala", previous.clearReward),
                yaml.integer("penalizacionMuertePct", previous.bankDeathPenaltyPct));
    }
}
