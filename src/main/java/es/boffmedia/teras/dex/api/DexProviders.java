package es.boffmedia.teras.dex.api;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Detects which Pokémon engine is installed and hands out the matching {@link DexProvider}.
 *
 * <p><b>Class-load safety</b>, as in {@link es.boffmedia.teras.battle.api.BattleProviders}: each
 * {@code new} must stay behind its {@code isLoaded} guard so the absent engine's provider — and the
 * engine types it references — are never linked.</p>
 *
 * <p>Resolved separately from the battle provider: the dex is also read on the client, where no battle
 * provider is wanted.</p>
 */
public final class DexProviders {
    private DexProviders() {}

    private static DexProvider active;
    private static boolean resolved;

    /**
     * The active provider, or {@code null} if neither engine is installed. Resolved once and cached.
     * Pixelmon wins if both are present, matching {@code BattleProviders}.
     */
    public static synchronized DexProvider get() {
        if (resolved) {
            return active;
        }
        resolved = true;
        if (PokemonEngines.isPixelmonLoaded()) {
            active = new es.boffmedia.teras.dex.pixelmon.PixelmonDexProvider();
        } else if (PokemonEngines.isCobblemonLoaded()) {
            active = new es.boffmedia.teras.dex.cobblemon.CobblemonDexProvider();
        } else {
            active = null;
            Teras.LOGGER.warn("No supported Pokémon engine (Pixelmon/Cobblemon) is installed; "
                    + "the SmartRotom dex scan will be unavailable.");
        }
        if (active != null) {
            Teras.LOGGER.info("Teras dex engine: {}", active.engineId());
        }
        return active;
    }
}
