package es.boffmedia.teras.storage.pixelmon;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.event.PlayerCloseStorageEvent;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.storage.StorageSync;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Flushes a storage refresh when the player closes the in-game PC.
 *
 * <p>Redundant with {@code PokemonStorageMixin} in the common case and kept anyway: it is a public
 * API rather than an injection into an internal, so if a Pixelmon update moves
 * {@code notifyListeners} out from under the mixin, the scenario that matters most — the player
 * rearranging boxes in-game, then opening the SmartRotom — still refreshes.</p>
 */
public final class PixelmonStorageEvents {
    private PixelmonStorageEvents() {}

    private static boolean registered;

    /** Idempotent; called once from common setup. */
    public static void install() {
        if (registered) {
            return;
        }
        registered = true;
        // Pixelmon's own bus, not NeoForge.EVENT_BUS: both are IEventBus and the event is a plain
        // neoforged Event, so the wrong bus compiles and silently never fires.
        Pixelmon.EVENT_BUS.register(PixelmonStorageEvents.class);
        Teras.LOGGER.info("Teras storage sync installed (pixelmon)");
    }

    @SubscribeEvent
    public static void onCloseStorage(PlayerCloseStorageEvent event) {
        StorageSync.markDirty(event.getPlayer());
    }
}
