package es.boffmedia.teras.give.api;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Detects which Pokémon engine is installed and hands out the matching {@link GiveProvider}.
 *
 * <p><b>Class-load safety</b>, as in {@link es.boffmedia.teras.battle.api.BattleProviders}: each
 * {@code new} must stay behind its {@code isLoaded} guard so the absent engine's provider — and the
 * engine types it references — are never linked.</p>
 */
public final class GiveProviders {
    private GiveProviders() {}

    private static GiveProvider active;
    private static boolean resolved;

    /**
     * The active provider, or {@code null} if neither engine is installed. Resolved once and cached.
     * Pixelmon wins if both are present, matching {@code BattleProviders}.
     */
    public static synchronized GiveProvider get() {
        if (resolved) {
            return active;
        }
        resolved = true;
        if (PokemonEngines.isPixelmonLoaded()) {
            active = new es.boffmedia.teras.give.pixelmon.PixelmonGiveProvider();
        } else if (PokemonEngines.isCobblemonLoaded()) {
            active = new es.boffmedia.teras.give.cobblemon.CobblemonGiveProvider();
        } else {
            active = null;
            Teras.LOGGER.warn("No supported Pokémon engine (Pixelmon/Cobblemon) is installed; "
                    + "POST /givepokemon will fail every request.");
        }
        if (active != null) {
            Teras.LOGGER.info("Teras give engine: {}", active.engineId());
        }
        return active;
    }
}
