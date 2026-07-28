package es.boffmedia.teras.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.TerasNet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * La esquiva's key, and the one thing the client contributes to it: which way the player is pressing.
 *
 * <p>Bound to {@code V} — free in vanilla, and near the movement hand. Rebindable, and scoped to
 * {@link KeyConflictContext#IN_GAME} so it cannot fire through a screen.</p>
 *
 * <p>The client decides <b>nothing else</b>. Whether the roll happens, how far it goes and how long
 * it protects are all server-side; this sends a request and lets it be refused, which is why holding
 * the key down costs a packet and never a desync.</p>
 *
 * <p>Registration lives in {@link DodgeSetup}, matching {@code CameraKeys} / {@code CameraSetup}.
 * That split is convention rather than necessity — see {@code DodgeSetup} for what the bytecode
 * actually does with a class that mixes the two buses.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class DodgeKeys {
    private DodgeKeys() {}

    static final String CATEGORY = "key.categories.teras";

    static final KeyMapping DODGE = new KeyMapping(
            "key.teras.esquiva", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Drained every tick even when nothing will come of it: consumeClick() returns presses
        // buffered since the last call, so leaving them queued would fire them all on entering a run.
        boolean pressed = false;
        while (DODGE.consumeClick()) {
            pressed = true;
        }
        if (!pressed) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        TerasNet.sendDodge(player.input.forwardImpulse, player.input.leftImpulse);
    }
}
