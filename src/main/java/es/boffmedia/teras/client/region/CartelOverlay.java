package es.boffmedia.teras.client.region;

import com.mojang.blaze3d.systems.RenderSystem;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.region.model.TerasRegion;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The sliding "you entered a town" cartel — port of the 1.16.5 {@code RegionEventsClient}
 * (Forge {@code RenderGameOverlayEvent} → a {@code LayeredDraw.Layer} registered by
 * {@link RegionClientSetup}). Slide math is kept verbatim: 500 ms slide in from above the screen
 * edge, hold, mirrored slide out; the cartel is a 256×256 texture scaled to half the screen height.
 *
 * <p>Where the old code silently dropped a cartel whose texture was missing, this falls back to the
 * vanilla title with the region's display name — a misconfigured banner id should be visible, and
 * regions without art still greet the player.</p>
 */
public final class CartelOverlay {
    private CartelOverlay() {}

    private static final int SIZE = 256;
    private static final long TRANSITION_MS = 500;

    private static ResourceLocation texture;
    private static long startMillis;
    private static long totalMillis;
    private static boolean visible;

    /** Entry from {@link es.boffmedia.teras.client.ClientNetHandler}; render thread. */
    public static void show(String banner, int holdSeconds) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation cartel = ResourceLocation.fromNamespaceAndPath(
                Teras.MOD_ID, "textures/carteles/" + banner + ".png");
        if (mc.getResourceManager().getResource(cartel).isEmpty()) {
            mc.gui.setTimes(10, holdSeconds * 20 + 20, 10);
            mc.gui.setTitle(Component.literal(TerasRegion.titleCase(banner)));
            return;
        }
        texture = cartel;
        totalMillis = holdSeconds * 1000L + TRANSITION_MS * 2;
        startMillis = Util.getMillis();
        visible = true;
    }

    /** Registered as a GUI layer by {@link RegionClientSetup}. */
    static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!visible) return;
        Minecraft mc = Minecraft.getInstance();
        // The layer manager's hideGui gate covers only vanilla's own layers, so check it here.
        if (mc.options.hideGui) return;

        long elapsed = Util.getMillis() - startMillis;
        if (elapsed >= totalMillis) {
            visible = false;
            return;
        }
        long posY = 0;
        if (elapsed < TRANSITION_MS) {
            posY = elapsed / 5 - 100;
        }
        if (elapsed > totalMillis - TRANSITION_MS) {
            posY = (totalMillis - elapsed) / 5 - 100;
        }

        float scale = (float) graphics.guiHeight() / 2 / SIZE;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        graphics.blit(texture, 0, (int) posY, 0, 0, SIZE, SIZE, SIZE, SIZE);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }
}
