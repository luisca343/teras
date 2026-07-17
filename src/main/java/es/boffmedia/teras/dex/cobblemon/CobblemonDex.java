package es.boffmedia.teras.dex.cobblemon;

import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import es.boffmedia.teras.dex.api.DexScan;

/**
 * Reads a Cobblemon {@link Pokemon} into the engine-neutral {@link DexScan} — the Cobblemon twin of
 * {@link es.boffmedia.teras.dex.pixelmon.PixelmonDex}, shared by {@link CobblemonDexProvider} and
 * {@link CobblemonDexSync}.
 *
 * <p>Absorbs the two places the engines differ: Cobblemon keys its dex by species
 * {@code ResourceLocation} and carries the national number separately, and it has no palettes (the
 * nearest equivalent is the shiny aspect).</p>
 */
final class CobblemonDex {
    private CobblemonDex() {}

    /** {@code pokemon} as a scan, or {@code null} if it has no species. */
    static DexScan scan(Pokemon pokemon) {
        if (pokemon == null) {
            return null;
        }
        Species species = pokemon.getSpecies();
        if (species == null) {
            return null;
        }
        FormData form = pokemon.getForm();
        return DexScan.of(
                species.getNationalPokedexNumber(),
                form == null ? null : form.getName(),
                pokemon.getShiny() ? "shiny" : DexScan.NONE);
    }
}
