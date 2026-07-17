package es.boffmedia.teras.dex.pixelmon;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.api.pokemon.species.palette.PaletteProperties;
import es.boffmedia.teras.dex.api.DexScan;

/**
 * Reads a Pixelmon {@link Pokemon} into the engine-neutral {@link DexScan} — the one place its
 * species/form/palette are pulled out, shared by {@link PixelmonDexProvider} and
 * {@link PixelmonDexSync}.
 */
final class PixelmonDex {
    private PixelmonDex() {}

    /** {@code pokemon} as a scan, or {@code null} if it has no species. */
    static DexScan scan(Pokemon pokemon) {
        if (pokemon == null) {
            return null;
        }
        Species species = pokemon.getSpecies();
        if (species == null) {
            return null;
        }
        Stats form = pokemon.getForm();
        PaletteProperties palette = pokemon.getPalette();
        return DexScan.of(
                species.getDex(),
                species.getName(),
                form == null ? null : form.getName(),
                palette == null ? null : palette.getName());
    }
}
