package es.boffmedia.teras.client;

import es.boffmedia.teras.init.ItemInit;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles automatic camera zoom when using the SmartRotom camera app
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "teras", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CameraZoomHandler {
    
    // Zoom level presets: 0 = no zoom, 1 = 1.5x, 2 = 2x, 3 = 3x, 4 = 4x
    private static final double[] ZOOM_LEVELS = {1.0, 0.67, 0.5, 0.33, 0.25};
    private static int currentZoomLevel = 2;
    private static double currentZoomMultiplier = ZOOM_LEVELS[currentZoomLevel];
    
    /**
     * Gets the current zoom multiplier being applied
     * @return The zoom multiplier (1.0 = no zoom, 0.5 = 2x zoom, etc.)
     */
    public static double getCurrentZoomMultiplier() {
        return currentZoomMultiplier;
    }
    
    /**
     * Checks if the camera zoom is currently active
     * @return true if zoom is active (multiplier < 1.0)
     */
    public static boolean isZoomActive() {
        return currentZoomMultiplier < 1.0;
    }
    
    /**
     * Gets the effective FOV after zoom is applied
     * @param baseFOV The base FOV value
     * @return The zoomed FOV value
     */
    public static double getZoomedFOV(double baseFOV) {
        return baseFOV * currentZoomMultiplier;
    }
    
    /**
     * Gets the current zoom level (0-4)
     * @return The zoom level index
     */
    public static int getZoomLevel() {
        return currentZoomLevel;
    }
    
    /**
     * Sets the zoom level (0-4)
     * @param level The zoom level to set (clamped to valid range)
     */
    public static void setZoomLevel(int level) {
        currentZoomLevel = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, level));
        currentZoomMultiplier = ZOOM_LEVELS[currentZoomLevel];
    }
    
    /**
     * Gets the total number of zoom levels available
     * @return Number of zoom levels
     */
    public static int getZoomLevelCount() {
        return ZOOM_LEVELS.length;
    }
    
    /**
     * Gets the zoom multiplier for a specific level
     * @param level The zoom level
     * @return The multiplier (1.0 = no zoom)
     */
    public static double getZoomMultiplierForLevel(int level) {
        if (level < 0 || level >= ZOOM_LEVELS.length) return 1.0;
        return ZOOM_LEVELS[level];
    }
    
    /**
     * Gets the zoom factor (e.g., 2.0 for 2x zoom) for a specific level
     * @param level The zoom level
     * @return The zoom factor
     */
    public static double getZoomFactorForLevel(int level) {
        double multiplier = getZoomMultiplierForLevel(level);
        return multiplier > 0 ? 1.0 / multiplier : 1.0;
    }
    
    @SubscribeEvent
    public static void onFOVModifier(EntityViewRenderEvent.FOVModifier event) {
        Minecraft mc = Minecraft.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) {
            currentZoomMultiplier = 1.0;
            return;
        }

        // Check if player is holding SmartRotom in either hand
        ItemStack mainHand = player.getItemInHand(Hand.MAIN_HAND);
        ItemStack offHand = player.getItemInHand(Hand.OFF_HAND);
        
        boolean holdingSmartRotom = false;
        int padId = -1;
        
        if (ItemInit.SMARTROTOM.isPresent()) {
            if (mainHand.getItem() == ItemInit.SMARTROTOM.get()) {
                holdingSmartRotom = true;
                CompoundNBT tag = mainHand.getTag();
                if (tag != null && tag.contains("PadID")) {
                    padId = tag.getInt("PadID");
                }
            } else if (offHand.getItem() == ItemInit.SMARTROTOM.get()) {
                holdingSmartRotom = true;
                CompoundNBT tag = offHand.getTag();
                if (tag != null && tag.contains("PadID")) {
                    padId = tag.getInt("PadID");
                }
            }
        }
        
        if (holdingSmartRotom && padId != -1) {
            ClientProxy.PadData pd = ((ClientProxy) es.boffmedia.teras.Teras.PROXY).getPadByID(padId);
            if (pd != null && pd.view != null) {
                String url = pd.view.getURL();
                if (url != null && url.contains("camara")) {
                    // Apply zoom effect - lower FOV means more zoom
                    currentZoomMultiplier = ZOOM_LEVELS[currentZoomLevel];
                    double zoomFOV = event.getFOV() * currentZoomMultiplier;
                    event.setFOV(zoomFOV);
                    return;
                }
            }
        }
        
        // Reset to no-zoom multiplier if not using camera (but keep the level setting)
        currentZoomMultiplier = ZOOM_LEVELS[currentZoomLevel];
    }
}
