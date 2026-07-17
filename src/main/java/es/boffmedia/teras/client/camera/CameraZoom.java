package es.boffmedia.teras.client.camera;

import com.cinemamod.mcef.MCEFBrowser;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.items.SmartRotom;
import es.boffmedia.teras.mcef.TerasMCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Zooms the player's view while the SmartRotom's camera page is in hand.
 *
 * <p>The zoom applies only when a held SmartRotom's browser is on the camera page — {@link #isActive()}
 * is the single answer to that, derived from the held item each time rather than cached. (1.16.5 cached
 * a multiplier from inside the FOV event, so {@code zoomActive} stayed true after you put the camera
 * away.)</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CameraZoom {
    private CameraZoom() {}

    /** The camera page's URL marker — the web app's contract, as in 1.16.5. */
    private static final String CAMERA_PAGE = "camara";

    /**
     * Zoom per level, as the factor the web states: 1× (off) up to 4×.
     *
     * <p>The factor is the source of truth and the FOV multiplier is derived, not the other way round.
     * 1.16.5 stored the multipliers ({@code 0.67}, {@code 0.33}) and inverted them for the web, which
     * reported {@code 1.4925373134328357x} and {@code 3.0303030303030303x} — the rounding of an
     * intended 1.5× and 3×, straight into the page's level list.</p>
     */
    private static final double[] FACTORS = {1.0, 1.5, 2.0, 3.0, 4.0};

    private static int level = 2;

    public static int level() {
        return level;
    }

    public static int levelCount() {
        return FACTORS.length;
    }

    /** Sets the zoom level, clamped to a valid index. */
    public static void setLevel(int newLevel) {
        level = Math.max(0, Math.min(FACTORS.length - 1, newLevel));
    }

    /** Steps the zoom by {@code delta} levels and reports whether it actually moved. */
    public static boolean step(int delta) {
        int previous = level;
        setLevel(level + delta);
        return level != previous;
    }

    /** The current zoom as the web states it: {@code 2.0} for 2×. */
    public static double currentFactor() {
        return zoomFactorForLevel(level);
    }

    /** The FOV multiplier for a level: 0.5 for 2× zoom. Lower means more zoomed in. */
    public static double multiplierForLevel(int index) {
        double factor = zoomFactorForLevel(index);
        return factor > 0 ? 1.0 / factor : 1.0;
    }

    /** The human-facing factor for a level: 2.0 for 2× zoom. */
    public static double zoomFactorForLevel(int index) {
        return (index < 0 || index >= FACTORS.length) ? 1.0 : FACTORS[index];
    }

    /** Whether a held SmartRotom is showing the camera page, and the zoom is therefore being applied. */
    public static boolean isActive() {
        return activeCameraId() != null;
    }

    /** The multiplier in effect right now — {@code 1.0} unless the camera is actually up. */
    public static double currentMultiplier() {
        return isActive() ? multiplierForLevel(level) : 1.0;
    }

    /** {@code baseFov} with the current zoom applied. */
    public static double zoomedFov(double baseFov) {
        return baseFov * currentMultiplier();
    }

    /** The browser id of a held SmartRotom on the camera page, or {@code null}. */
    static UUID activeCameraId() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            UUID id = cameraIdOf(player.getItemInHand(hand));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** {@code stack}'s browser id if it is a SmartRotom showing the camera page, else {@code null}. */
    public static UUID cameraIdOf(ItemStack stack) {
        if (!(stack.getItem() instanceof SmartRotom)) {
            return null;
        }
        UUID id = SmartRotom.getId(stack);
        if (id == null) {
            return null;
        }
        MCEFBrowser browser = TerasMCEF.getBrowser(id);
        if (browser == null) {
            return null;
        }
        String url = browser.getURL();
        return (url != null && url.toLowerCase(Locale.ROOT).contains(CAMERA_PAGE)) ? id : null;
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (isActive()) {
            event.setFOV(event.getFOV() * multiplierForLevel(level));
        }
    }
}
