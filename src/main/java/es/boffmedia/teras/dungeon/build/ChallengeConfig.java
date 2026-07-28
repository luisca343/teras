package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/** The challenge room's waves: how many, when a floor adds one, how much bigger, what it pays. */
public record ChallengeConfig(int waves, int extraWaveStage, int waveGrowthPct, int reward) {

    static ChallengeConfig defaults() {
        return new ChallengeConfig(2, 5, 30, 30);
    }

    static ChallengeConfig read(YamlConfig yaml, ChallengeConfig previous) {
        YamlConfig challenge = yaml.section("desafio");
        return new ChallengeConfig(
                Math.max(1, challenge.integer("oleadasBase", previous.waves)),
                challenge.integer("oleadaExtraDesdeEtapa", previous.extraWaveStage),
                challenge.integer("crecimientoOleadaPct", previous.waveGrowthPct),
                challenge.integer("recompensaMonedas", previous.reward));
    }
}
