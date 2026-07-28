package es.boffmedia.teras.shiny.api;

/**
 * What an engine reports about one Pokémon entity, reduced to the four things the sparkle rule
 * cares about. Engine-neutral by construction: every field is a primitive or a plain string, so
 * {@link es.boffmedia.teras.shiny.ShinyRules} can be tested without either engine on the classpath.
 *
 * @param palette the engine's palette name. Pixelmon reports its real palette ({@code shiny},
 *                {@code shiny2}, an event palette, or {@code none}); Cobblemon has no palettes and
 *                reports {@code shiny}/{@code none}, exactly as
 *                {@link es.boffmedia.teras.dex.api.DexScan} does — the two are deliberately the same
 *                vocabulary so a palette added to one place is understood in the other
 * @param wild    false for anything that belongs to a player: a sent-out party member, a battle
 *                clone, a statue. A shiny you already own is not a sighting
 * @param boss    Pixelmon boss Pokémon. Excluded because a boss announces itself — in Legends
 *                Arceus alphas have their own cue, and stacking the shiny chime on top of it makes
 *                both mean less. Always false on Cobblemon, which has no bosses
 * @param catchable false for anything the player cannot actually claim (Pixelmon's uncatchable
 *                flag, used by scripted and decorative spawns). Sparkling at something unobtainable
 *                is a promise the game does not keep
 */
public record ShinyCandidate(String palette, boolean wild, boolean boss, boolean catchable) {

    /** Matches {@link es.boffmedia.teras.dex.api.DexScan#NONE} so both layers spell "no palette" alike. */
    public static final String NONE = "none";

    public ShinyCandidate {
        palette = (palette == null || palette.isEmpty()) ? NONE : palette;
    }
}
