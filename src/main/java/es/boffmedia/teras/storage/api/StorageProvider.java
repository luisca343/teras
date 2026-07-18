package es.boffmedia.teras.storage.api;

import java.util.UUID;

/**
 * Opens a player's Pokémon storage for the SmartRotom PC, one implementation per engine — mirroring
 * {@code battle.api.BattleProvider} and {@code give.api.GiveProvider}. Chosen by
 * {@link StorageProviders}.
 *
 * <p>Loading and using a storage want opposite threads, so they are separate calls: {@link #open}
 * blocks (an offline player's storage is on disk) and the {@link StorageSession} it returns touches
 * game state. Collapsing them would deadlock — an engine may schedule its own load <i>onto</i> the
 * server thread, and waiting there for a task that thread must run is a hang no timeout undoes.</p>
 */
public interface StorageProvider {

    /** The engine this provider drives ({@code pixelmon} / {@code cobblemon}). */
    String engineId();

    /**
     * Loads {@code player}'s party and PC, or {@code null} if this server has no storage for them.
     * Both are loaded even when the caller wants one: an online player's are already in memory, and
     * the PC page asks for both.
     *
     * <p><b>Blocks; never call on the server thread.</b></p>
     *
     * @throws java.util.concurrent.TimeoutException if the load does not finish in time
     */
    StorageSession open(UUID player) throws Exception;
}
