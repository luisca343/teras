package es.boffmedia.teras.dex.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Engine-neutral Pokédex access for the SmartRotom scan. One implementation per supported engine
 * ({@code pixelmon}, {@code cobblemon}); exactly one is active at runtime, chosen by
 * {@link DexProviders}. Mirrors {@link es.boffmedia.teras.battle.api.BattleProvider}.
 *
 * <p>Takes an {@link Entity} rather than a dex number because both engines register from their own
 * Pokémon object, which the entity owns — so form and palette are whatever that Pokémon is.</p>
 */
public interface DexProvider {

    /** Stable engine id, matching the mod id ({@code "pixelmon"} / {@code "cobblemon"}). */
    String engineId();

    /**
     * Reads {@code entity} as a Pokémon, or {@code null} if this engine doesn't recognise it as one.
     * Safe on both sides — it only reads species/form/palette, which both engines sync to the client.
     */
    DexScan scan(Entity entity);

    /**
     * Registers {@code entity}'s species as SEEN in {@code player}'s Pokédex. Server thread only, and
     * idempotent — the engine no-ops on an unchanged status.
     *
     * <p>Does not touch the backend: each engine fires a dex-changed event, and {@code dex.*.*DexSync}
     * turns that into the single SmartRotom POST. See {@code docs/DEX.md}.</p>
     */
    void markSeen(ServerPlayer player, Entity entity);

    /**
     * The player's whole Pokédex, or {@code null} if this server has none for them. The bulk resync
     * behind {@code POST /updatedex}; the per-registration push in {@code dex.*.*DexSync} is what
     * keeps the backend current day to day.
     *
     * <p><b>Blocks, and must not be called on the server thread</b> — an offline player's dex is
     * loaded from disk, and an engine may schedule that load onto the server thread. Implementations
     * hop to the server thread themselves for the part that reads game state.</p>
     */
    DexSnapshot readAll(MinecraftServer server, UUID player) throws Exception;
}
