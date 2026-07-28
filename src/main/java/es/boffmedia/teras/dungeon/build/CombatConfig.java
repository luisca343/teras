package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/**
 * Whether Teras computes damage instead of Minecraft.
 *
 * <p>One field, and its own type anyway: the flag is read from a dozen places across combat and
 * ships <b>true</b>, which is the one default in this config where an absent key does not mean
 * "keep the old behaviour" (ROGUELIKE §4 — a default-off flag cost two playtests reporting the
 * feature as unbuilt).</p>
 */
public record CombatConfig(boolean enabled) {

    static CombatConfig defaults() {
        return new CombatConfig(true);
    }

    static CombatConfig read(YamlConfig yaml, CombatConfig previous) {
        return new CombatConfig(yaml.section("combate").bool("activado", previous.enabled));
    }
}
