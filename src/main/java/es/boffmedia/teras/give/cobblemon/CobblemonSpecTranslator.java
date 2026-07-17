package es.boffmedia.teras.give.cobblemon;

import es.boffmedia.teras.Teras;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translates a Pixelmon {@code PokemonSpec} string into Cobblemon {@code PokemonProperties} syntax,
 * or refuses. No Cobblemon types here on purpose: the translation is pure string work, and keeping it
 * engine-free lets it be unit-tested without Cobblemon on the classpath, the same way the battle
 * layer's parsers are.
 *
 * <p><b>It refuses far more than it maps, and that is the whole point.</b> The backend stores every
 * spec in Pixelmon syntax ({@code Incineroar lvl:50 otn:Wolfey ivhp:31}). Cobblemon parses
 * <b>leniently</b> — {@code lvl:50} is not rejected, it is ignored, yielding a level-1 Pokémon — so an
 * unmapped token silently downgrades the reward. Only mappings verified against the Cobblemon jar are
 * performed ({@code species}, {@code level}, {@code shiny}); any other token fails the whole grant.
 * Refused on purpose: {@code ivhp:}/{@code otn:} — Cobblemon's {@code ivs}/{@code ot} are not the same
 * thing and their value syntax is unverified. Widen only by checking the jar, never by assuming.</p>
 */
final class CobblemonSpecTranslator {
    private CobblemonSpecTranslator() {}

    /** Cobblemon properties for {@code spec}, or {@code null} if any token can't be mapped for certain. */
    static String translate(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        boolean speciesSeen = false;

        for (String token : spec.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int colon = token.indexOf(':');
            if (colon < 0) {
                if ("shiny".equalsIgnoreCase(token)) {
                    out.add("shiny=true");
                } else if (!speciesSeen) {
                    out.add("species=" + token.toLowerCase(Locale.ROOT));
                    speciesSeen = true;
                } else {
                    return refuse(spec, token);
                }
                continue;
            }
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            switch (key) {
                case "lvl", "level" -> out.add("level=" + value);
                case "shiny" -> out.add("shiny=" + value);
                default -> {
                    return refuse(spec, token);
                }
            }
        }

        if (!speciesSeen) {
            Teras.LOGGER.error("givePokemon: spec '{}' names no species; refusing", spec);
            return null;
        }
        return String.join(" ", out);
    }

    private static String refuse(String spec, String token) {
        Teras.LOGGER.error("givePokemon: refusing spec '{}' — Cobblemon has no verified translation "
                + "for '{}', and granting without it would hand over a different Pokémon. Add a "
                + "mapping only after checking it against the Cobblemon jar.", spec, token);
        return null;
    }
}
