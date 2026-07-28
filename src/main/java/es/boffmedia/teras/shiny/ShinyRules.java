package es.boffmedia.teras.shiny;

import es.boffmedia.teras.shiny.api.ShinyCandidate;

import java.util.Set;

/**
 * Whether a Pokémon deserves the shiny cue, and at what distance. Pure — no Minecraft, no engine —
 * so the whole decision is unit-tested without a server, the same discipline
 * {@code dungeon/model} follows.
 *
 * <p>This is the layer the 1.16.5 {@code ShinyTracker} never had. There the rule was an eight-term
 * boolean inside a method that also mutated two sets and logged per entity per tick, so it could
 * neither be read nor tested.</p>
 */
public final class ShinyRules {
    private ShinyRules() {}

    /**
     * Palettes that count as shiny.
     *
     * <p>{@code shiny2} is Pixelmon's second shiny palette and was already in the 1.16.5 rule.
     * Reading the <i>palette</i> rather than a boolean is not a stylistic choice: Pixelmon 9.3.16
     * removed {@code Pokemon.isShiny()} — {@code setShiny} survives, the getter does not — so the
     * palette name is the only thing left to ask.</p>
     */
    public static final Set<String> SHINY_PALETTES = Set.of("shiny", "shiny2");

    /** True when {@code palette} is one this server treats as shiny. Null-safe and case-insensitive. */
    public static boolean isShinyPalette(String palette) {
        return palette != null && SHINY_PALETTES.contains(palette.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Whether {@code candidate} should ever sparkle. Every exclusion is documented on
     * {@link ShinyCandidate}'s own fields — this method is only their conjunction.
     */
    public static boolean sparkles(ShinyCandidate candidate) {
        return candidate != null
                && isShinyPalette(candidate.palette())
                && candidate.wild()
                && !candidate.boss()
                && candidate.catchable();
    }

    /** Whether a squared distance is inside {@code range} blocks. */
    public static boolean inRange(double distanceSqr, double range) {
        return range > 0 && distanceSqr <= range * range;
    }

    /**
     * Whether something offset by {@code (dx, dy, dz)} falls inside a cone of {@code coneDegrees}
     * (full width) centred on the direction {@code (lookX, lookY, lookZ)} — roughly, whether it is
     * on screen.
     *
     * <p>The cue fires once per Pokémon, so <b>where that one shot lands matters</b>. Range alone is
     * a sphere: it will spend the sighting on something twenty blocks behind the player's head, and
     * the single chime they get is for a Pokémon they never see. A cone spends it when they are
     * facing the thing.</p>
     *
     * <p>{@code coneDegrees} of zero or less disables the test — back to a sphere. At 360 or more
     * everything passes, which is the same thing said the other way.</p>
     */
    public static boolean inViewCone(double lookX, double lookY, double lookZ,
                                     double dx, double dy, double dz, double coneDegrees) {
        if (coneDegrees <= 0 || coneDegrees >= 360) {
            return true;
        }
        double offset = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (offset < 1.0E-4) {
            return true; // Standing inside it; there is no direction to compare against.
        }
        double look = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (look < 1.0E-4) {
            return true;
        }
        double cosine = (lookX * dx + lookY * dy + lookZ * dz) / (offset * look);
        return cosine >= Math.cos(Math.toRadians(coneDegrees / 2.0));
    }
}
