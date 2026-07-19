package es.boffmedia.teras.dungeon.ability;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * One ability on one enemy, in engine-free terms. Tuning rides an open parameter map rather than
 * fixed fields so a new {@link AbilityKind} needs no change to the JSON reader or to this record —
 * the same reason {@code EnemyPreset} stays plain data.
 *
 * @param kind   what it does
 * @param arg    the id it needs, if any: a mob effect for {@code ON_HIT}, a spawn spec for
 *               {@code SUMMON} ({@code entity:minecraft:zombie}, {@code cnpc:7:name},
 *               {@code geo:husk_guardian}), otherwise empty
 * @param params tuning, read through {@link #param} so a missing key is the documented default
 *               rather than a crash on someone's hand-edited config
 */
public record AbilityDef(AbilityKind kind, String arg, Map<String, Double> params) {

    public AbilityDef {
        // Sorted, not just immutable: the installer renders these back into enemies.json, and an
        // unordered map would reshuffle every key on each rewrite, turning a no-op reinstall into
        // a noisy diff for whoever is hand-tuning the file.
        params = Collections.unmodifiableSortedMap(new TreeMap<>(params));
    }

    public double param(String name, double fallback) {
        return params.getOrDefault(name, fallback);
    }

    public int intParam(String name, int fallback) {
        return (int) Math.round(param(name, fallback));
    }
}
