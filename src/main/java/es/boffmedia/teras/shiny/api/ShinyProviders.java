package es.boffmedia.teras.shiny.api;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Detects which Pokémon engine is installed and hands out the matching {@link ShinyProvider}.
 *
 * <p><b>Class-load safety</b>, as in {@link es.boffmedia.teras.dex.api.DexProviders}: each
 * {@code new} must stay behind its {@code isLoaded} guard, so the absent engine's provider — and the
 * engine types its method bodies name — are never linked. A mod-presence {@code if} does not protect
 * a class whose <i>method body</i> names the guarded type; only keeping the reference behind the
 * guard does.</p>
 */
public final class ShinyProviders {
    private ShinyProviders() {}

    private static ShinyProvider active;
    private static boolean resolved;

    /**
     * The active provider, or {@code null} if neither engine is installed. Resolved once and cached.
     * Pixelmon wins if both are present, matching every other provider registry in the mod.
     */
    public static synchronized ShinyProvider get() {
        if (resolved) {
            return active;
        }
        resolved = true;
        if (PokemonEngines.isPixelmonLoaded()) {
            active = new es.boffmedia.teras.shiny.pixelmon.PixelmonShinyProvider();
        } else if (PokemonEngines.isCobblemonLoaded()) {
            active = new es.boffmedia.teras.shiny.cobblemon.CobblemonShinyProvider();
        } else {
            active = null;
        }
        if (active != null) {
            Teras.LOGGER.info("Teras shiny tracker engine: {}", active.engineId());
        }
        return active;
    }
}
