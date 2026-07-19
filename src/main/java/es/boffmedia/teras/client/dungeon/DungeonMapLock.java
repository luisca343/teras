package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Keeps the world map out of a dungeon run: JourneyMap's minimap is switched off through its API,
 * and its screens — fullscreen map, waypoint manager — are refused while a run is active. Inside a
 * dungeon the only map is the run's own, which shows what has been discovered and nothing else;
 * a world map would hand over the floor's whole layout, and its waypoint teleports would walk
 * straight through sealed rooms.
 *
 * <p>Screens are matched by package rather than class: the mod list is fixed, and JourneyMap has
 * several map-bearing screens whose names are not API. The minimap side is guarded by
 * {@code ModList} so nothing here links without JourneyMap installed.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DungeonMapLock {
    private DungeonMapLock() {}

    private static final String JOURNEYMAP_PACKAGE = "journeymap.";

    private static boolean locked;

    /** Called when the run state flips; safe to call repeatedly with the same value. */
    public static void setLocked(boolean value) {
        if (locked == value) {
            return;
        }
        locked = value;
        if (ModList.get().isLoaded("journeymap")) {
            es.boffmedia.teras.client.region.journeymap.JourneyMapSuppressor.setSuppressed(value);
        }
        if (value) {
            closeOpenMapScreen();
        }
    }

    /** A map already open when the run starts would otherwise stay up: only opening is blocked. */
    private static void closeOpenMapScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && mc.screen.getClass().getName().startsWith(JOURNEYMAP_PACKAGE)) {
            mc.setScreen(null);
        }
    }

    public static boolean isLocked() {
        return locked;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (locked && event.getNewScreen().getClass().getName().startsWith(JOURNEYMAP_PACKAGE)) {
            event.setCanceled(true);
        }
    }
}
