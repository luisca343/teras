package es.boffmedia.teras.battle.team.api;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Picks the {@link TeamProvider} for the installed engine. Class-load safety as in
 * {@link es.boffmedia.teras.storage.api.StorageProviders}: the {@code new} stays behind its
 * {@code isLoaded} guard.
 *
 * <p>Pixelmon only. Teams are stored as the engine's own Pokémon NBT, so a Cobblemon implementation
 * is a different file format rather than a second branch — and nothing asks for one yet.</p>
 */
public final class TeamProviders {
    private TeamProviders() {}

    private static TeamProvider active;
    private static boolean resolved;

    /** The active provider, or {@code null} if no supported engine is installed. Resolved once. */
    public static synchronized TeamProvider get() {
        if (resolved) {
            return active;
        }
        resolved = true;
        if (PokemonEngines.isPixelmonLoaded()) {
            active = new es.boffmedia.teras.battle.team.pixelmon.PixelmonTeamProvider();
            Teras.LOGGER.info("Teras battle-team engine: {}", active.engineId());
        } else {
            active = null;
            Teras.LOGGER.warn("Pixelmon is not installed; the battle-team routes "
                    + "(POST /getallbattleteams, /updatebattleteam) will answer 503.");
        }
        return active;
    }
}
