package es.boffmedia.teras.client;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.gui.PantallaSmartRotom;
import es.boffmedia.teras.mcef.TerasMCEF;
import es.boffmedia.teras.util.TerasConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup: loads config and registers the SmartRotom JS bridge with MCEF.
 * Referenced from {@link es.boffmedia.teras.items.SmartRotom} only behind a client-side guard,
 * so it is never classloaded on a dedicated server.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TerasClient {
    private TerasClient() {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            TerasConfig.load();
            TerasMCEF.init();
            Teras.LOGGER.info("Teras client setup complete");
        });
    }

    /** Opens the SmartRotom browser screen. */
    public static void openSmartRotom() {
        if (!TerasMCEF.isReady()) {
            Teras.LOGGER.warn("SmartRotom pressed but MCEF not ready yet");
            return;
        }
        Minecraft.getInstance().setScreen(new PantallaSmartRotom());
    }
}
