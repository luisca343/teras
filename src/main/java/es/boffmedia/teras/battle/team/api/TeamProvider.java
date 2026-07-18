package es.boffmedia.teras.battle.team.api;

import es.boffmedia.teras.battle.team.BattleTeam;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

/**
 * A player's saved battle teams, for the SmartRotom PC's teams panel. One implementation per engine;
 * chosen by {@link TeamProviders}.
 *
 * <p>Both methods <b>block and must not be called on the server thread</b>: they read and write files,
 * and reach into Pokémon storage that an engine may load onto that thread. Implementations hop to the
 * server thread themselves for the parts that touch game state.</p>
 */
public interface TeamProvider {

    /** The engine this provider drives ({@code pixelmon} / {@code cobblemon}). */
    String engineId();

    /** Every saved team, in name order. Empty when the player has none. */
    List<BattleTeam> readAll(MinecraftServer server, UUID player) throws Exception;

    /**
     * Copies the Pokémon at {@code sourceBox}/{@code sourceSlot} (box {@code -1} is the party) into
     * {@code teamSlot} of {@code teamName}, creating the team if it does not exist. Returns whether
     * it was written.
     *
     * <p>A <b>copy</b>, as 1.16.5 stored it: the team holds a snapshot of the Pokémon, not a pointer
     * to a box position, so moving the original around the PC afterwards does not disturb the team.
     * The snapshot does go stale if the Pokémon is then levelled or re-trained.</p>
     */
    boolean updateSlot(MinecraftServer server, UUID player, String teamName, int teamSlot,
                       int sourceBox, int sourceSlot) throws Exception;
}
