package es.boffmedia.teras.dungeon.gear;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * One ability a piece of gear carries, and the numbers it runs with.
 *
 * <p>This replaced a bare {@link GearAbility} plus a single {@code magnitude} field on
 * {@link GearDef}. Two things were wrong with that shape:</p>
 *
 * <ul>
 *   <li><b>One number per ability.</b> {@code ONDA} wanted a radius as well as a fraction and got a
 *       hard-coded 2.5; {@code DESGARRO} wanted a duration and a level and got a level of 1 written
 *       into the code. Anything an ability needed beyond its first number was compiled in.</li>
 *   <li><b>One ability per piece.</b> A sword that both burned and stole life could not be
 *       expressed, so the catalog quietly never tried.</li>
 * </ul>
 *
 * <p>Same shape as {@code MechanicDef} in the dungeon config, deliberately: a registry key plus
 * string params read through typed accessors with defaults. Reusing a pattern already proven here
 * beats inventing a second one.</p>
 *
 * <h2>What config can and cannot do</h2>
 *
 * <p>Worth being exact, because "add any capability from config" is only half true. Config
 * <b>composes from the abilities the code implements</b> — it can retune them, give a piece several,
 * and change what each does numerically. It cannot invent a new kind of behaviour: that is a new
 * {@link GearAbility} and its hook in {@code GearEvents}. Same truth as {@code mecanica}, and for
 * the same reason.</p>
 *
 * @param ability which implemented hook this is
 * @param params  its numbers; every one optional, each ability documenting its own keys
 */
public record AbilityDef(GearAbility ability, Map<String, String> params) {

    /** The primary number nearly every ability has — a fraction, a count or a duration. */
    public static final String MAGNITUDE = "magnitud";

    public AbilityDef {
        ability = ability == null ? GearAbility.NINGUNA : ability;
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** The shape every legacy definition had: one ability, one number. */
    public static AbilityDef of(GearAbility ability, double magnitude) {
        return new AbilityDef(ability, Map.of(MAGNITUDE, String.valueOf(magnitude)));
    }

    public static AbilityDef none() {
        return new AbilityDef(GearAbility.NINGUNA, Map.of());
    }

    public boolean isNone() {
        return ability == GearAbility.NINGUNA;
    }

    /** The primary number, or {@code fallback} when the piece does not set one. */
    public double magnitude(double fallback) {
        return doubleParam(MAGNITUDE, fallback);
    }

    /**
     * A fractional param. A value that will not parse falls back rather than throwing — an ability
     * that refused to fire because someone typed {@code "0,15"} would be a silent hole in a piece's
     * behaviour, and the fallback is the shipped number.
     */
    public double doubleParam(String key, double fallback) {
        String raw = params.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int intParam(String key, int fallback) {
        String raw = params.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String param(String key, String fallback) {
        String raw = params.get(key);
        return raw == null || raw.isBlank() ? fallback : raw.trim();
    }

    /**
     * Why this ability cannot run, given the ones the code implements, or an empty list.
     *
     * <p>Pure and taking the known set as an argument for the same reason {@code MechanicDef} does:
     * so the rule is testable without a running game.</p>
     */
    public List<String> problems(Collection<GearAbility> known) {
        if (isNone() || known.contains(ability)) {
            return List.of();
        }
        return List.of("has ability '" + ability + "', which nothing implements");
    }

    @Override
    public String toString() {
        return isNone() ? "(ninguna)" : params.isEmpty() ? ability.name() : ability + " " + params;
    }
}
