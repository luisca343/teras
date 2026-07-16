package es.boffmedia.teras.integration;

import net.neoforged.fml.ModList;

/**
 * Which Pokémon engine is installed. Teras supports Pixelmon and Cobblemon, one at a time, chosen at
 * runtime by whichever mod is present.
 *
 * <p>Shared by every engine-neutral subsystem (battles, economy, …) so they all agree on the same
 * answer, and so a class that merely wants to <i>ask</i> doesn't have to depend on an unrelated
 * subsystem's provider registry.</p>
 *
 * <p><b>Detection only.</b> This class must never reference an engine's types — it is loaded on every
 * server regardless of which engine (if any) is installed. Provider lookup, and the class-load
 * guarding that goes with it, belongs in each subsystem's own {@code *Providers}.</p>
 */
public final class PokemonEngines {
    private PokemonEngines() {}

    public static final String PIXELMON = "pixelmon";
    public static final String COBBLEMON = "cobblemon";

    public static boolean isPixelmonLoaded() {
        return ModList.get().isLoaded(PIXELMON);
    }

    public static boolean isCobblemonLoaded() {
        return ModList.get().isLoaded(COBBLEMON);
    }

    /** Whether any supported engine is present. */
    public static boolean isAnyLoaded() {
        return isPixelmonLoaded() || isCobblemonLoaded();
    }
}
