package es.boffmedia.teras.dex;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Installs the dex → SmartRotom backend mirror for whichever engine is present, mirroring
 * {@link es.boffmedia.teras.economy.EconomyBridge} and {@link es.boffmedia.teras.quests.QuestBridge}.
 *
 * <p>Must stay free of engine imports: it loads on every server, so the engine-coupled classes in
 * {@code dex.pixelmon} / {@code dex.cobblemon} may only be named inside an already-guarded branch.</p>
 *
 * <p>Separate from {@link es.boffmedia.teras.dex.api.DexProviders}: this is the write-side mirror,
 * installed once at setup, while the provider is a lazily-resolved reader used on both sides.</p>
 */
public final class DexBridge {
    private DexBridge() {}

    /** Installs the engine's dex-change listener. Called from common setup. */
    public static void registerIfPresent() {
        if (PokemonEngines.isPixelmonLoaded()) {
            es.boffmedia.teras.dex.pixelmon.PixelmonDexSync.install();
        } else if (PokemonEngines.isCobblemonLoaded()) {
            es.boffmedia.teras.dex.cobblemon.CobblemonDexSync.install();
        } else {
            Teras.LOGGER.info("No Pokémon engine installed; Pokédex registrations will not be "
                    + "mirrored to SmartRotom.");
        }
    }
}
