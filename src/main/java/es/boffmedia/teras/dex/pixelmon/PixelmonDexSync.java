package es.boffmedia.teras.dex.pixelmon;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokedex.event.PokedexEvent;
import com.pixelmonmod.pixelmon.api.pokedex.status.PokedexRegistrationStatus;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dex.DexStatus;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.util.net.SmartRotomService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Mirrors every Pixelmon Pokédex change to the SmartRotom backend. Compiles against Pixelmon, so it is
 * only registered from behind an {@code isPixelmonLoaded()} guard ({@link es.boffmedia.teras.dex.DexBridge}).
 *
 * <p>Pixelmon fires {@code PokedexEvent.Post} on any real status change, whatever caused it — a scan, a
 * capture, a battle. This listener is the mod's only POST to {@code /smartrotom/pokemon/register};
 * nothing else may post there. See {@code docs/DEX.md}.</p>
 */
public final class PixelmonDexSync {
    private PixelmonDexSync() {}

    private static boolean registered;

    /** Idempotent — {@code DexBridge} calls this once from common setup. */
    public static void install() {
        if (registered) {
            return;
        }
        registered = true;
        // Pixelmon's own bus, not NeoForge.EVENT_BUS: both are IEventBus and PokedexEvent is a plain
        // neoforged Event, so the wrong bus compiles and silently never fires.
        Pixelmon.EVENT_BUS.register(PixelmonDexSync.class);
        Teras.LOGGER.info("Teras dex sync installed (pixelmon)");
    }

    @SubscribeEvent
    public static void onPokedexChanged(PokedexEvent.Post event) {
        // Setup runs on both dists; only the host reports. Non-null in single-player (integrated
        // server), null on a client connected to a remote server.
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return;
        }
        UUID player = event.getPlayerUUID();
        PokedexRegistrationStatus status = event.getNewStatus();
        if (player == null || status == null) {
            return;
        }
        // UNKNOWN is a de-registration, which the backend contract cannot express.
        if (status == PokedexRegistrationStatus.UNKNOWN) {
            return;
        }
        DexScan scan = PixelmonDex.scan(event.getPokemon());
        if (scan == null) {
            return;
        }
        SmartRotomService.registerPokedex(player, scan,
                status == PokedexRegistrationStatus.SEEN ? DexStatus.SEEN : DexStatus.CAUGHT);
    }
}
