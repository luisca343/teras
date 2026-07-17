package es.boffmedia.teras.dex.pixelmon;

import com.pixelmonmod.pixelmon.api.pokedex.PokeDexStorageProxy;
import com.pixelmonmod.pixelmon.api.pokedex.PokedexStorage;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.entities.pixelmon.AbstractBaseEntity;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dex.api.DexProvider;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.integration.PokemonEngines;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Pixelmon Pokédex access. Compiles against Pixelmon ({@code compileOnly}), so it is only ever named
 * from behind an {@code isPixelmonLoaded()} guard — see {@link es.boffmedia.teras.dex.api.DexProviders}.
 *
 * <p>{@link AbstractBaseEntity} is the common supertype of {@code PixelmonEntity} and
 * {@code StatueEntity} and holds the backing Pokémon, so one branch covers both. The 9.3.16 API
 * differences from 1.16.5 are tabulated in {@code docs/DEX.md}.</p>
 */
public final class PixelmonDexProvider implements DexProvider {

    /** Passed to Pixelmon as the registration's cause; surfaces in {@code PokedexEvent}. */
    private static final String SOURCE = "SmartRotom";

    @Override
    public String engineId() {
        return PokemonEngines.PIXELMON;
    }

    @Override
    public DexScan scan(Entity entity) {
        return entity instanceof AbstractBaseEntity pokemonEntity
                ? PixelmonDex.scan(pokemonEntity.getPokemon())
                : null;
    }

    @Override
    public void markSeen(ServerPlayer player, Entity entity) {
        if (!(entity instanceof AbstractBaseEntity pokemonEntity)) {
            return;
        }
        Pokemon pokemon = pokemonEntity.getPokemon();
        if (pokemon == null) {
            return;
        }
        try {
            // getStorageNow is getStorage(player).join(): it blocks, but setSeen fires PokedexEvent
            // Pre/Post, which listeners expect on the server thread.
            PokedexStorage storage = PokeDexStorageProxy.getStorageNow(player);
            if (storage == null) {
                Teras.LOGGER.warn("SmartRotom dex scan by {}: no Pokédex storage available; skipping",
                        player.getGameProfile().getName());
                return;
            }
            // Fires Pre (cancellable) and Post itself, only on a real status change.
            storage.setSeen(pokemon, SOURCE);
        } catch (Throwable t) {
            // Runs off a packet handler, and join() surfaces a failed storage load unchecked.
            Teras.LOGGER.error("SmartRotom dex scan by {}: registration failed",
                    player.getGameProfile().getName(), t);
        }
    }
}
