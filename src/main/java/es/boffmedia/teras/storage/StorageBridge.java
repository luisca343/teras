package es.boffmedia.teras.storage;

import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Installs the engine's storage-change listeners, mirroring {@code DexBridge}. Names no engine class
 * itself, so nothing from an absent engine is loaded.
 */
public final class StorageBridge {
    private StorageBridge() {}

    public static void registerIfPresent() {
        if (PokemonEngines.isPixelmonLoaded()) {
            es.boffmedia.teras.storage.pixelmon.PixelmonStorageEvents.install();
        }
    }
}
