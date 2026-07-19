package es.boffmedia.teras.client.region.journeymap;

import es.boffmedia.teras.Teras;
import journeymap.api.v2.client.IClientAPI;

/**
 * Turns JourneyMap's minimap off while a dungeon run is on, and back to whatever the player had
 * before. A dungeon is meant to be read from its own Isaac minimap — a full world map showing the
 * floor's real layout gives away every room before it is discovered.
 *
 * <p>Lives in this package because it imports {@code journeymap.*}: callers MUST check
 * {@code ModList.isLoaded("journeymap")} before naming this class, the same rule as
 * {@link TerasJourneyMapPlugin} and {@code WorldEditBridge}. Blocking JourneyMap's <i>screens</i>
 * needs no API at all and lives with the dungeon client code.</p>
 */
public final class JourneyMapSuppressor {
    private JourneyMapSuppressor() {}

    /** What the player's minimap was set to before a run suppressed it. */
    private static Boolean restoreTo;

    public static void setSuppressed(boolean suppressed) {
        TerasJourneyMapPlugin plugin = TerasJourneyMapPlugin.instance();
        if (plugin == null || plugin.api() == null) {
            return;
        }
        IClientAPI api = plugin.api();
        try {
            if (suppressed) {
                // Remember only on the first suppression: repeated payloads must not overwrite the
                // player's real setting with the off state we ourselves just applied.
                if (restoreTo == null) {
                    restoreTo = api.minimapEnabled();
                }
                api.toggleMinimap(false);
            } else if (restoreTo != null) {
                api.toggleMinimap(restoreTo);
                restoreTo = null;
            }
        } catch (Throwable t) {
            Teras.LOGGER.warn("Dungeons: could not toggle the JourneyMap minimap: {}", t.toString());
        }
    }
}
