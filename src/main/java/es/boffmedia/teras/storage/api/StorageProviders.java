package es.boffmedia.teras.storage.api;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Picks the {@link StorageProvider} for the installed engine. Class-load safety as in
 * {@link es.boffmedia.teras.give.api.GiveProviders}: the {@code new} stays behind its
 * {@code isLoaded} guard so an absent engine's types are never linked.
 *
 * <p>Pixelmon only. Cobblemon has a PC, but its cross-store swap is a remove-then-set and the wire
 * contract is Pixelmon-shaped (palettes, {@code item.*} ids, 30×30); an unverified implementation
 * could lose a Pokémon mid-swap, where a 503 cannot.</p>
 */
public final class StorageProviders {
    private StorageProviders() {}

    private static StorageProvider active;
    private static boolean resolved;

    /** The active provider, or {@code null} if no supported engine is installed. Resolved once. */
    public static synchronized StorageProvider get() {
        if (resolved) {
            return active;
        }
        resolved = true;
        if (PokemonEngines.isPixelmonLoaded()) {
            active = new es.boffmedia.teras.storage.pixelmon.PixelmonStorageProvider();
            Teras.LOGGER.info("Teras storage engine: {}", active.engineId());
        } else {
            active = null;
            Teras.LOGGER.warn("Pixelmon is not installed; the SmartRotom PC routes "
                    + "(POST /pc, /equipo, /pc/move) will answer 503.");
        }
        return active;
    }
}
