package es.boffmedia.teras.dex.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dex.api.DexProvider;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.integration.PokemonEngines;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Cobblemon Pokédex access. Compiles against Cobblemon ({@code compileOnly}), so it is only ever named
 * from behind an {@code isCobblemonLoaded()} guard — see {@link es.boffmedia.teras.dex.api.DexProviders}.
 * The species/form/palette mapping lives in {@link CobblemonDex}.
 *
 * <p>{@link #scan} recognises living Pokémon only: Cobblemon has no statue equivalent.</p>
 */
public final class CobblemonDexProvider implements DexProvider {

    @Override
    public String engineId() {
        return PokemonEngines.COBBLEMON;
    }

    @Override
    public DexScan scan(Entity entity) {
        return entity instanceof PokemonEntity pokemonEntity
                ? CobblemonDex.scan(pokemonEntity.getPokemon())
                : null;
    }

    @Override
    public void markSeen(ServerPlayer player, Entity entity) {
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return;
        }
        Pokemon pokemon = pokemonEntity.getPokemon();
        if (pokemon == null) {
            return;
        }
        try {
            PokedexManager pokedex = pokedexOf(player);
            if (pokedex == null) {
                return;
            }
            // ENCOUNTERED is Cobblemon's SEEN; fires PokedexDataChangedEvent, which CobblemonDexSync
            // turns into the backend POST.
            pokedex.encounter(pokemon);
        } catch (Throwable t) {
            // Runs off a packet handler.
            Teras.LOGGER.error("SmartRotom dex scan by {}: registration failed",
                    player.getGameProfile().getName(), t);
        }
    }

    /** The player's dex out of Cobblemon's instanced player-data store, or {@code null} if unavailable. */
    private static PokedexManager pokedexOf(ServerPlayer player) {
        if (Cobblemon.playerDataManager == null) {
            Teras.LOGGER.warn("SmartRotom dex scan by {}: Cobblemon player data manager not ready; skipping",
                    player.getGameProfile().getName());
            return null;
        }
        InstancedPlayerData data =
                Cobblemon.playerDataManager.get(player, PlayerInstancedDataStoreTypes.INSTANCE.getPOKEDEX());
        if (data instanceof PokedexManager pokedex) {
            return pokedex;
        }
        Teras.LOGGER.warn("SmartRotom dex scan by {}: POKEDEX store returned {}, not a PokedexManager; skipping",
                player.getGameProfile().getName(), data == null ? "null" : data.getClass().getName());
        return null;
    }
}
