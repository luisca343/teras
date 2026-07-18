package es.boffmedia.teras.dex.pixelmon;

import com.pixelmonmod.pixelmon.api.pokedex.PokeDexStorageProxy;
import com.pixelmonmod.pixelmon.api.pokedex.PokedexStorage;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.entities.pixelmon.AbstractBaseEntity;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dex.api.DexProvider;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.dex.api.DexSnapshot;
import es.boffmedia.teras.integration.PokemonEngines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    /**
     * Read from the dex's own NBT snapshot rather than its accessors: 9.3.16 replaced
     * {@code PlayerPokedex.getSeenMap()} with per-Pokémon lookups keyed by an exact
     * {@code PokemonBase} (dex + form + gender + palette), so there is no longer any way to ask
     * "what does this player have" without already knowing the answer. {@code save()} is public and
     * its keys are a persistence format, which outlives field renames.
     */
    @Override
    public DexSnapshot readAll(MinecraftServer server, UUID player) throws Exception {
        // Off-thread: Pixelmon's dex loader may schedule onto the server thread, and joining there
        // would deadlock — the same trap as PixelmonStorageProvider.
        PokedexStorage dex = PokeDexStorageProxy.getStorage(player)
                .get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (dex == null) {
            return null;
        }
        Snapshot read = server.submit(() -> new Snapshot(
                        dex.save(server.registryAccess()), dex.countSeen(), dex.countCaught()))
                .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        DexSnapshot parsed = parse(read.tag());
        // The caller bulk-OVERWRITES its dex table with this, so an empty answer is destructive, not
        // merely useless. If the dex says it holds entries and the parse found none, the NBT layout
        // has moved — fail the request rather than report an empty Pokédex as fact.
        if (parsed.SEEN().isEmpty() && parsed.CAUGHT().isEmpty()
                && read.seenCount() + read.caughtCount() > 0) {
            throw new IllegalStateException("Pokédex NBT layout not recognised for " + player
                    + " (dex reports " + read.seenCount() + " seen / " + read.caughtCount()
                    + " caught, parsed none)");
        }
        return parsed;
    }

    private record Snapshot(CompoundTag tag, int seenCount, int caughtCount) {}

    /**
     * Folds the dex's NBT into two dex-number lists. A species is listed once at its strongest status
     * — caught beats seen — because forms are separate entries here but 1.16.5 keyed by dex number.
     *
     * <p>Two shapes: a bare {@code DexData} writes {@code statuses} at the top, while the
     * {@code StoredPokedex} the proxy actually returns nests one of those per pokedex under
     * {@code pokedexes}. Both are read, and the national numbers union across pokedexes.</p>
     */
    private static DexSnapshot parse(CompoundTag tag) {
        Set<Integer> seen = new TreeSet<>();
        Set<Integer> caught = new TreeSet<>();
        collect(tag, seen, caught);
        ListTag pokedexes = tag.getList(POKEDEXES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < pokedexes.size(); i++) {
            collect(pokedexes.getCompound(i), seen, caught);
        }
        seen.removeAll(caught);
        return new DexSnapshot(new ArrayList<>(seen), new ArrayList<>(caught));
    }

    private static void collect(CompoundTag dexData, Set<Integer> seen, Set<Integer> caught) {
        ListTag statuses = dexData.getList(STATUSES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < statuses.size(); i++) {
            CompoundTag entry = statuses.getCompound(i);
            int dexNumber = entry.getInt(NDEX_KEY);
            if (dexNumber <= 0) {
                continue;
            }
            String status = entry.getCompound(STATUS_DATA_KEY).getString(STATUS_KEY);
            if (CAUGHT_STATUS.equals(status)) {
                caught.add(dexNumber);
            } else if (SEEN_STATUS.equals(status)) {
                seen.add(dexNumber);
            }
        }
    }

    private static final long LOAD_TIMEOUT_SECONDS = 4;
    private static final long READ_TIMEOUT_SECONDS = 5;
    private static final String POKEDEXES_KEY = "pokedexes";
    private static final String STATUSES_KEY = "statuses";
    private static final String NDEX_KEY = "ndex";
    private static final String STATUS_DATA_KEY = "status_data";
    private static final String STATUS_KEY = "status";
    private static final String SEEN_STATUS = "SEEN";
    private static final String CAUGHT_STATUS = "CAUGHT";
}
