package es.boffmedia.teras.client.camera;

import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The camera's flashlight, as a client-side night-vision effect.
 *
 * <p>No directional beam is possible without shaders, and real emitted light is not an option: NeoForge's
 * {@code AuxiliaryLightManager} is serialized into chunk NBT, so a light following the player would be
 * written to disk in every chunk walked through. See {@code docs/CAMERA.md}.</p>
 *
 * <h2>Why this cannot be left switched on</h2>
 * <ul>
 *   <li><b>It is derived, not just applied.</b> {@link #isOn()} requires the camera page to still be in
 *       hand, so putting the SmartRotom away, closing the app or dying turns it off on the next tick —
 *       there is no list of places to remember to switch it off.</li>
 *   <li><b>The effect expires on its own.</b> It is applied for {@link #EFFECT_TICKS} and refreshed every
 *       tick, so anything that stops the refresh — a crash, an unhandled case — clears it within seconds
 *       rather than leaving the player with permanent night vision.</li>
 *   <li><b>It never takes someone's potion.</b> Vanilla merges by keeping the longer duration, so this
 *       can't shorten a real one, and {@link #clearIfOurs} only removes an instance short enough to be
 *       this one.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class CameraFlashlight {
    private CameraFlashlight() {}

    /**
     * Effect duration per refresh. Above 200 so vanilla never pulses it
     * ({@code GameRenderer.getNightVisionScale} fades below that), and no higher than it needs to be, so
     * a missed refresh clears within ~10s.
     */
    private static final int EFFECT_TICKS = 210;

    /** The app's switch. Whether it does anything is {@link #isOn()}. */
    private static boolean enabled;

    /** Whether the effect currently on the player is one we put there. Gates every removal. */
    private static boolean applied;

    /** Whether the flashlight is actually lighting anything: switched on <em>and</em> camera in hand. */
    public static boolean isOn() {
        return enabled && CameraZoom.isActive();
    }

    /** The switch itself, regardless of whether the camera is up — what the page's button reflects. */
    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /** Flips the switch and returns its new position. */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (isOn()) {
            apply();
        } else if (applied) {
            // Only ever clear something we put there. Without this gate, a player who has never touched
            // the camera loses a real night-vision potion the moment it drops under EFFECT_TICKS.
            clear();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // The next server gets its own camera, and its own player: don't carry the switch across, and
        // don't let the next world's night vision look like ours.
        enabled = false;
        applied = false;
    }

    private static void apply() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // ambient/visible/showIcon all false: no particles, and no effect icon in the HUD or inventory.
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_TICKS, 0, false, false, false));
        applied = true;
    }

    private static void clear() {
        applied = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        MobEffectInstance effect = player.getEffect(MobEffects.NIGHT_VISION);
        if (effect != null && isOurs(effect.getAmplifier(), effect.getDuration())) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /**
     * Whether an effect this shape is one of ours rather than a real potion someone drank while the
     * flashlight was on — vanilla merges the two into one instance, keeping the longer duration, so
     * what is left when a potion is involved is always longer than anything we apply.
     */
    static boolean isOurs(int amplifier, int duration) {
        return amplifier == 0 && duration <= EFFECT_TICKS;
    }
}
