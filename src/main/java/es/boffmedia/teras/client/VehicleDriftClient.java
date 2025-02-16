package es.boffmedia.teras.client;

import com.mrcrayfish.vehicle.entity.PoweredVehicleEntity;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageDriftState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class VehicleDriftClient {
    private boolean isDrifting = false;
    private static final int SPACE_KEY = GLFW.GLFW_KEY_SPACE;

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPlayerEntity player = minecraft.player;

        if (player == null) return;

        Entity ridingEntity = player.getVehicle();
        if (!(ridingEntity instanceof PoweredVehicleEntity)) return;

        // Check if space key was just pressed or released
        if (event.getKey() == SPACE_KEY) {
            boolean isPressed = event.getAction() == GLFW.GLFW_PRESS;

            // Only send packet if drift state is changing
            if (isPressed != isDrifting) {
                isDrifting = isPressed;

                // Determine drift direction based on turning input
                boolean driftRight = minecraft.options.keyRight.isDown();
                if (!driftRight && !minecraft.options.keyLeft.isDown()) {
                    // If no turn key is pressed, use the vehicle's current rotation
                    // to determine drift direction
                    float yaw = player.yRot % 360;
                    if (yaw < 0) yaw += 360;
                    driftRight = (yaw >= 45 && yaw < 225);
                }

                // Send drift state to server
                Messages.INSTANCE.sendToServer(new CMessageDriftState(isDrifting, driftRight));
            }
        }
    }
}