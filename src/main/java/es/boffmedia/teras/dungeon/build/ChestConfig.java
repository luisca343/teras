package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/**
 * What a chest costs when it is not coins (PISOS §69). Every price is charged at the click, never
 * at the approach: ParCool is in the pack, so a ledge costs stamina rather than access.
 */
public record ChestConfig(float spikeDamage, float trapDamage, int trapChancePct) {

    static ChestConfig defaults() {
        return new ChestConfig(4, 6, 35);
    }

    static ChestConfig read(YamlConfig yaml, ChestConfig previous) {
        YamlConfig chest = yaml.section("cofres");
        return new ChestConfig(
                chest.integer("danoPuas", Math.round(previous.spikeDamage)),
                chest.integer("danoTrampa", Math.round(previous.trapDamage)),
                chest.integer("probTrampaPct", previous.trapChancePct));
    }
}
