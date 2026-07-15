package es.boffmedia.teras.battle.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a PokePaste / Pokémon-Showdown team export into {@link ShowdownSet}s. Engine-neutral and
 * dependency-free so it is unit-testable without any Minecraft/engine runtime. Used by the Cobblemon
 * provider (Cobblemon exposes no server-side Showdown importer); the Pixelmon provider uses its own
 * native importer instead.
 *
 * <p>Handles the standard export shape, e.g.:</p>
 * <pre>
 * Pikachu (M) @ Light Ball
 * Ability: Static
 * Level: 50
 * Shiny: Yes
 * Tera Type: Electric
 * EVs: 252 SpA / 4 SpD / 252 Spe
 * Timid Nature
 * IVs: 0 Atk
 * - Thunderbolt
 * - Volt Switch
 * </pre>
 */
public final class ShowdownTeamParser {
    private ShowdownTeamParser() {}

    public static List<ShowdownSet> parse(String paste) {
        List<ShowdownSet> sets = new ArrayList<>();
        if (paste == null || paste.isBlank()) {
            return sets;
        }

        ShowdownSet current = null;
        for (String rawLine : paste.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (current != null && current.hasSpecies()) {
                    sets.add(current);
                }
                current = null;
                continue;
            }
            if (current == null) {
                current = new ShowdownSet();
                parseHeader(current, line);
            } else {
                parseAttribute(current, line);
            }
        }
        if (current != null && current.hasSpecies()) {
            sets.add(current);
        }
        return sets;
    }

    /** First line: {@code [Nickname] [(Species)] [(Gender)] [@ Item]}. */
    private static void parseHeader(ShowdownSet set, String line) {
        // Held item.
        int at = line.indexOf('@');
        if (at >= 0) {
            set.item = line.substring(at + 1).trim();
            line = line.substring(0, at).trim();
        }
        // Trailing gender token "(M)" / "(F)".
        if (line.endsWith("(M)") || line.endsWith("(F)")) {
            set.gender = line.substring(line.length() - 2, line.length() - 1);
            line = line.substring(0, line.length() - 3).trim();
        }
        // Trailing "(Species)" → the leading text is the nickname; otherwise the whole line is species.
        int open = line.lastIndexOf('(');
        if (open >= 0 && line.endsWith(")")) {
            set.species = line.substring(open + 1, line.length() - 1).trim();
            set.nickname = line.substring(0, open).trim();
        } else {
            set.species = line.trim();
        }
    }

    private static void parseAttribute(ShowdownSet set, String line) {
        if (line.startsWith("- ")) {
            set.moves.add(line.substring(2).trim());
        } else if (startsWithIgnoreCase(line, "Ability:")) {
            set.ability = after(line, ':');
        } else if (startsWithIgnoreCase(line, "Level:")) {
            set.level = parseIntOrNull(after(line, ':'));
        } else if (startsWithIgnoreCase(line, "Shiny:")) {
            set.shiny = after(line, ':').equalsIgnoreCase("Yes");
        } else if (startsWithIgnoreCase(line, "Tera Type:")) {
            set.teraType = after(line, ':');
        } else if (startsWithIgnoreCase(line, "EVs:")) {
            parseStats(after(line, ':'), set.evs);
        } else if (startsWithIgnoreCase(line, "IVs:")) {
            parseStats(after(line, ':'), set.ivs);
        } else if (line.regionMatches(true, line.length() - 7, " Nature", 0, 7)) {
            set.nature = line.substring(0, line.length() - 7).trim();
        }
        // Unrecognised lines (Happiness, Gigantamax, etc.) are ignored.
    }

    /** Parses {@code "252 SpA / 4 SpD / 252 Spe"} into stat→value entries keyed by the Showdown label. */
    private static void parseStats(String spec, java.util.Map<String, Integer> out) {
        for (String part : spec.split("/")) {
            String[] kv = part.trim().split("\\s+");
            if (kv.length == 2) {
                Integer value = parseIntOrNull(kv[0]);
                if (value != null) {
                    out.put(kv[1].toUpperCase(java.util.Locale.ROOT), value);
                }
            }
        }
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String after(String line, char sep) {
        int i = line.indexOf(sep);
        return i < 0 ? "" : line.substring(i + 1).trim();
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
