package es.boffmedia.teras.client;

import com.cinemamod.mcef.MCEFBrowser;
import com.google.gson.Gson;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.camera.CameraZoom;
import es.boffmedia.teras.client.gui.PantallaSmartRotom;
import es.boffmedia.teras.dex.api.DexScan;
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
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class TerasClient {
    private TerasClient() {}

    /** Only used to JS-escape scan strings before they reach {@code executeJavaScript}. */
    private static final Gson GSON = new Gson();

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Config is loaded in common setup (both sides); here we only do the client-only MCEF wiring.
        event.enqueueWork(() -> {
            TerasMCEF.init();
            Teras.LOGGER.info("Teras client setup complete");
        });
    }

    /**
     * Points this item's browser at the scanned Pokémon's dex entry, <b>without</b> opening the screen:
     * the SmartRotom shows the entry in-hand, and taking over the view would hide the Pokémon just
     * aimed at.
     */
    public static void openDex(ItemStack stack, DexScan scan) {
        if (browserFor(stack) == null) {
            return;
        }
        // Gson, not concatenation: the form reaches JS as a string literal.
        TerasMCEF.runJS(SmartRotom.getId(stack),
                "openDex(" + scan.dex() + ", " + GSON.toJson(scan.form()) + ")");
    }

    /**
     * Fires the camera shutter if {@code stack}'s browser is on the camera page, and reports whether it
     * did. The page's own {@code takeScreenshot()} then queries back for the capture, so the options
     * (format, UI, quality) stay the page's to choose.
     */
    public static boolean tryCameraShutter(ItemStack stack) {
        UUID id = CameraZoom.cameraIdOf(stack);
        if (id == null) {
            return false;
        }
        TerasMCEF.runJS(id, "takeScreenshot()");
        return true;
    }

    /** Opens the SmartRotom browser screen for the given item's own browser instance. */
    public static void openSmartRotom(ItemStack stack) {
        MCEFBrowser browser = browserFor(stack);
        if (browser == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new PantallaSmartRotom(browser));
    }

    /**
     * This item's browser, creating it if needed, or {@code null} if it can't exist yet (MCEF still
     * starting, no id assigned, config not yet synced) — each case logged. The dex scan needs the
     * browser without the screen open, so the guards live here rather than in {@link #openSmartRotom}.
     */
    private static MCEFBrowser browserFor(ItemStack stack) {
        if (!TerasMCEF.isReady()) {
            Teras.LOGGER.warn("SmartRotom used but MCEF not ready yet");
            return null;
        }
        UUID id = SmartRotom.getId(stack);
        if (id == null) {
            // The server assigns the id in inventoryTick; it should be synced before the item is used.
            Teras.LOGGER.warn("SmartRotom has no id yet; skipping");
            return null;
        }
        if (!ServerConfig.isSynced()) {
            Teras.LOGGER.warn("SmartRotom used before the server sent its config; skipping");
            return null;
        }
        MCEFBrowser browser = TerasMCEF.getOrCreateBrowser(id, ServerConfig.getHome());
        if (browser == null) {
            Teras.LOGGER.warn("SmartRotom browser unavailable for id={}", id);
        }
        return browser;
    }
}
