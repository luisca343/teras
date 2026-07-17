package es.boffmedia.teras.dex.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokedexDataChangedEvent;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.pokedex.scanner.PokedexEntityData;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dex.DexStatus;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.util.net.SmartRotomService;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * The Cobblemon half of the dex → backend mirror, and the only Cobblemon-side POST to
 * {@code /smartrotom/pokemon/register}; see {@link es.boffmedia.teras.dex.pixelmon.PixelmonDexSync} and
 * {@code docs/DEX.md}. Compiles against Cobblemon, so it is only registered from behind an
 * {@code isCobblemonLoaded()} guard ({@link es.boffmedia.teras.dex.DexBridge}).
 *
 * <p>Cobblemon's events are its own: subscription goes through {@link CobblemonEvents} observables,
 * not {@code @SubscribeEvent}.</p>
 */
public final class CobblemonDexSync {
    private CobblemonDexSync() {}

    private static boolean registered;

    /** Idempotent — {@code DexBridge} calls this once from common setup. */
    public static void install() {
        if (registered) {
            return;
        }
        registered = true;
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(CobblemonDexSync::onPokedexChanged);
        Teras.LOGGER.info("Teras dex sync installed (cobblemon)");
    }

    private static void onPokedexChanged(PokedexDataChangedEvent.Post event) {
        // Setup runs on both dists; only the host reports.
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return;
        }
        UUID player = event.getPlayerUUID();
        PokedexEntryProgress knowledge = event.getKnowledge();
        // ENCOUNTERED is Pixelmon's SEEN; NONE is a de-registration the backend cannot express.
        if (player == null || knowledge == null || knowledge == PokedexEntryProgress.NONE) {
            return;
        }
        PokedexEntityData source = event.getDataSource();
        DexScan scan = CobblemonDex.scan(source == null ? null : source.getPokemon());
        if (scan == null) {
            return;
        }
        SmartRotomService.registerPokedex(player, scan,
                knowledge == PokedexEntryProgress.CAUGHT ? DexStatus.CAUGHT : DexStatus.SEEN);
    }
}
