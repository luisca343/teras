package es.boffmedia.teras.quests;

import es.boffmedia.teras.Teras;
import net.neoforged.neoforge.common.NeoForge;
import noppes.npcs.api.NpcAPI;

/**
 * Performs the actual handler registration. Split out from {@link QuestBridge} because this class
 * touches CustomNPCs (and, conditionally, Pixelmon) types — it is only ever reached once
 * {@link QuestBridge#isAvailable()} has confirmed the mod is loaded.
 */
final class QuestRegistrar {
    private QuestRegistrar() {}

    static void register() {
        // Dialog/quest events are CustomNPCs' own, dispatched on the bus behind its API instance —
        // not the NeoForge game bus. Registering the class picks up its static @SubscribeEvent methods.
        NpcAPI.Instance().events().register(QuestEvents.class);
        Teras.LOGGER.info("Quest system enabled (CustomNPCs detected)");

        if (QuestBridge.isHuntAvailable()) {
            // Pixelmon's events, by contrast, are plain NeoForge game-bus events.
            NeoForge.EVENT_BUS.register(es.boffmedia.teras.quests.pixelmon.MisionesCaza.class);
            Teras.LOGGER.info("Hunt quests enabled (Pixelmon + CustomNPCs detected)");
        } else {
            Teras.LOGGER.info("Hunt quests disabled (Pixelmon not installed)");
        }
    }
}
