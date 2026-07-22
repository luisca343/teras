package es.boffmedia.teras.dungeon.piso;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The signature mechanic a piso runs, and the numbers it runs it with.
 *
 * <p>This replaced a bare {@code String}. The id alone was enough while there was one mechanic and
 * nobody wanted to tune it, and it hid two things:</p>
 *
 * <ul>
 *   <li><b>The tuning was compiled.</b> How long an egg sac took to crack, how many hatched from
 *       it and what hatched were private constants in {@code Nests}. Changing any of them meant a
 *       mod release, for a value an operator has more right to an opinion about than the author.</li>
 *   <li><b>A typo was silent.</b> Nothing enumerated the mechanic ids, so {@code "infestacon"} on a
 *       piso read as "no mechanic" and said nothing. It only surfaced at all when a room in that
 *       piso happened to carry {@code nido} markers.</li>
 * </ul>
 *
 * <p>Params are held as strings and read through typed accessors with defaults, so a mechanic keeps
 * owning what its numbers mean and what they fall back to. A piso overrides what it cares about and
 * says nothing about the rest — the same shape as {@code pesos} and {@code pesosFormas}.</p>
 *
 * @param id     the registry key, or "" for a piso with no mechanic
 * @param params overrides; every one is optional, and an unknown key is the mechanic's business
 */
public record MechanicDef(String id, Map<String, String> params) {

    /** A piso that runs nothing. Cuevas is the baseline and has one of these. */
    public static final MechanicDef NONE = new MechanicDef("", Map.of());

    public MechanicDef {
        id = id == null ? "" : id.trim();
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** The bare-string form every config used before params existed. */
    public static MechanicDef of(String id) {
        return id == null || id.isBlank() ? NONE : new MechanicDef(id, Map.of());
    }

    public boolean isNone() {
        return id.isEmpty();
    }

    public boolean is(String other) {
        return !isNone() && id.equals(other);
    }

    /**
     * A whole-number param. A value that will not parse falls back rather than throwing: a mechanic
     * that refused to run because someone typed {@code "80 ticks"} would take the floor down over a
     * cosmetic mistake, and the fallback is the shipped behaviour.
     */
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

    public String param(String key, String fallback) {
        String raw = params.get(key);
        return raw == null || raw.isBlank() ? fallback : raw.trim();
    }

    /**
     * Why this mechanic cannot run, given the ids that exist, or an empty list.
     *
     * <p>The rule lives here rather than in the registry so it can be tested without a server: the
     * registry has to instantiate real mechanics, and those reach into Minecraft. Same split as
     * {@link RoomPoolIndex} and its build-layer shim — the reasoning is pure, the wiring is not.</p>
     */
    public List<String> problems(Collection<String> knownIds) {
        if (isNone() || knownIds.contains(id)) {
            return List.of();
        }
        return List.of("declares mecanica '" + id + "', which does not exist — nothing will run. "
                + "Known: " + String.join(", ", knownIds));
    }

    /** What {@code piso info} shows — the id, and the overrides if there are any. */
    @Override
    public String toString() {
        if (isNone()) {
            return "(ninguna)";
        }
        return params.isEmpty() ? id : id + " " + params;
    }
}
