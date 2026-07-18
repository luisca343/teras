package es.boffmedia.teras.storage.pixelmon;

import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import es.boffmedia.teras.integration.PokemonEngines;
import es.boffmedia.teras.storage.api.StorageProvider;
import es.boffmedia.teras.storage.api.StorageSession;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Pixelmon 9.3.16 {@link StorageProvider}. Only reachable behind
 * {@link PokemonEngines#isPixelmonLoaded()}.
 *
 * <p><b>Never call {@code StorageProxy.get*Now} from the server thread.</b> Those are
 * {@code get*(uuid).join()}, and Pixelmon's <i>standard</i> storage scheduler is the
 * {@code MinecraftServer} itself — so an uncached player's load is a task queued onto the very
 * thread joining it. Loading happens here instead, off-thread; only resolved storages cross into
 * {@link PixelmonStorageSession}.</p>
 */
public final class PixelmonStorageProvider implements StorageProvider {

    /**
     * Budget for both storages together. Sized so this plus the caller's server-thread wait stays
     * under the backend's 10s axios timeout — {@code /pc/move} is a swap, so a request that lands
     * unreported is one the user redoes and thereby undoes.
     */
    private static final long LOAD_TIMEOUT_MILLIS = 4_000;

    @Override
    public String engineId() {
        return PokemonEngines.PIXELMON;
    }

    @Override
    public StorageSession open(UUID player) throws Exception {
        // Both kicked off before either is awaited, so they load concurrently.
        CompletableFuture<PlayerPartyStorage> party = StorageProxy.getParty(player);
        CompletableFuture<PCStorage> pc = StorageProxy.getPCForPlayer(player);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOAD_TIMEOUT_MILLIS);
        PlayerPartyStorage loadedParty = party.get(remaining(deadline), TimeUnit.NANOSECONDS);
        PCStorage loadedPc = pc.get(remaining(deadline), TimeUnit.NANOSECONDS);
        if (loadedParty == null && loadedPc == null) {
            return null;
        }
        return new PixelmonStorageSession(player, loadedParty, loadedPc);
    }

    /** Never negative: {@code get(0)} polls, whereas a negative wait is not meaningful. */
    private static long remaining(long deadlineNanos) {
        return Math.max(0, deadlineNanos - System.nanoTime());
    }
}
