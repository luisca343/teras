package es.boffmedia.teras.client;

import com.cinemamod.mcef.MCEFBrowser;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.gui.PantallaSmartRotom;
import es.boffmedia.teras.items.SmartRotom;
import es.boffmedia.teras.mcef.TerasMCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.UUID;

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
        // Config is loaded in common setup (both sides); here we only do the client-only MCEF wiring.
        event.enqueueWork(() -> {
            TerasMCEF.init();
            Teras.LOGGER.info("Teras client setup complete");
        });
    }

    /** Opens the SmartRotom browser screen for the given item's own browser instance. */
    public static void openSmartRotom(ItemStack stack) {
        if (!TerasMCEF.isReady()) {
            Teras.LOGGER.warn("SmartRotom pressed but MCEF not ready yet");
            return;
        }
        UUID id = SmartRotom.getId(stack);
        if (id == null) {
            // The server assigns the id in inventoryTick; it should be synced before the item is used.
            Teras.LOGGER.warn("SmartRotom has no id yet; skipping open");
            return;
        }
        if (!ServerConfig.isSynced()) {
            Teras.LOGGER.warn("SmartRotom pressed before the server sent its config; skipping open");
            return;
        }
        MCEFBrowser browser = TerasMCEF.getOrCreateBrowser(id, ServerConfig.getHome());
        if (browser == null) {
            Teras.LOGGER.warn("SmartRotom browser unavailable for id={}", id);
            return;
        }
        Minecraft.getInstance().setScreen(new PantallaSmartRotom(browser));
    }
}
