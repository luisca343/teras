package es.boffmedia.teras.shiny.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Engine-neutral read of a Pokémon entity for the shiny cue. One implementation per supported engine
 * ({@code pixelmon}, {@code cobblemon}), chosen by {@link ShinyProviders}. Mirrors
 * {@link es.boffmedia.teras.dex.api.DexProvider} and {@code battle.api.BattleProvider}.
 *
 * <p><b>Why not reuse {@code DexProvider}?</b> It already reports a palette, and the sparkle rule
 * reads palettes — but the dex has no opinion on whether a Pokémon is owned, a boss, or catchable,
 * and no business gaining one. Widening it would put spotting semantics in the subsystem that mirrors
 * the Pokédex to the backend. One extra pair of small classes buys both subsystems staying about what
 * they are named after. The palette <i>vocabulary</i> is shared deliberately — see
 * {@link ShinyCandidate#palette()}.</p>
 */
public interface ShinyProvider {

    /** Stable engine id, matching the mod id ({@code "pixelmon"} / {@code "cobblemon"}). */
    String engineId();

    /**
     * Reads {@code entity} as a Pokémon, or {@code null} if this engine does not recognise it as one.
     * Server thread. Reads only state the engine keeps on the entity — no storage, no blocking.
     */
    ShinyCandidate read(Entity entity);

    /**
     * Whether {@code player} is in a battle. The cue is suppressed for them while they are: the
     * camera is locked to the battlefield, so a sparkle fired behind them is one they cannot turn to
     * look at, and the shiny they are <i>fighting</i> is already on screen.
     */
    boolean isBattling(ServerPlayer player);
}
