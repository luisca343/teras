package es.boffmedia.teras.battle.api;

import es.boffmedia.teras.battle.config.BattleConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * Engine-neutral entry point for starting Teras battles. One implementation per supported Pokémon
 * engine ({@code pixelmon}, {@code cobblemon}); exactly one is active at runtime, chosen by
 * {@link BattleProviders} from whichever mod is installed.
 *
 * <p>All methods are invoked on the server thread and take an already-loaded {@link BattleConfig}
 * (the blocking config/team fetch happens beforehand, off-thread). The implementation is responsible
 * for building the rival team from {@link BattleConfig#getTeamPaste()} in its own Pokémon type,
 * spawning any trainer entity, applying the rules it supports, and wiring up its own end/log
 * handling — the caller only asks for a battle to start.</p>
 */
public interface BattleProvider {

    /** Stable engine id, matching the mod id ({@code "pixelmon"} / {@code "cobblemon"}). */
    String engineId();

    /**
     * Starts a single-player battle described by {@code config}: a trainer battle when
     * {@link BattleConfig#esEntrenador()} is true, otherwise a wild encounter.
     */
    void startConfigBattle(ServerPlayer player, BattleConfig config);

    /**
     * Starts a 2v2 multi battle: {@code player} + a partner trainer versus two rival trainers.
     * Providers that cannot express multi battles may log and no-op.
     */
    void startMultiBattle(ServerPlayer player, BattleConfig partner, BattleConfig rival1, BattleConfig rival2);
}
