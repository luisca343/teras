package es.boffmedia.teras.dex.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dex.api.DexProvider;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.dex.api.DexSnapshot;
import es.boffmedia.teras.integration.PokemonEngines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    /**
     * Cobblemon keys its dex by species identifier, so the national number comes from resolving each
     * back to its {@code Species}. Works for offline players: the store is addressable by uuid.
     */
    @Override
    public DexSnapshot readAll(MinecraftServer server, UUID player) throws Exception {
        if (Cobblemon.playerDataManager == null) {
            return null;
        }
        return server.submit(() -> snapshot(player)).get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** Server thread: reads the live dex store. */
    private static DexSnapshot snapshot(UUID player) {
        InstancedPlayerData data = Cobblemon.playerDataManager.get(
                player, PlayerInstancedDataStoreTypes.INSTANCE.getPOKEDEX());
        if (!(data instanceof PokedexManager pokedex)) {
            return null;
        }
        Set<Integer> seen = new TreeSet<>();
        Set<Integer> caught = new TreeSet<>();
        for (Map.Entry<ResourceLocation, SpeciesDexRecord> entry : pokedex.getSpeciesRecords().entrySet()) {
            Species species = PokemonSpecies.getByIdentifier(entry.getKey());
            if (species == null) {
                continue;
            }
            PokedexEntryProgress progress = entry.getValue().getKnowledge();
            if (progress == PokedexEntryProgress.CAUGHT) {
                caught.add(species.getNationalPokedexNumber());
            } else if (progress == PokedexEntryProgress.ENCOUNTERED) {
                seen.add(species.getNationalPokedexNumber());
            }
        }
        // A species belongs to one list, matching the Pixelmon provider and 1.16.5.
        seen.removeAll(caught);
        return new DexSnapshot(new ArrayList<>(seen), new ArrayList<>(caught));
    }

    private static final long READ_TIMEOUT_SECONDS = 5;
}
