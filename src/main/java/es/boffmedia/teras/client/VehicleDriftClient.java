package es.boffmedia.teras.client;

import com.mrcrayfish.vehicle.entity.PoweredVehicleEntity;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageDriftState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class VehicleDriftClient {
    private boolean isDrifting = false;
    private boolean isDriftingRight = false;
    private static final int SPACE_KEY = GLFW.GLFW_KEY_SPACE;
    private static final long TURN_KEY_GRACE_PERIOD = 500; // 500ms = 0.5 seconds
    private long turnKeyReleaseTime = 0;
    private boolean isInGracePeriod = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isDrifting || !isInGracePeriod) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        // Check if we're still in the grace period
        long currentTime = System.currentTimeMillis();
        if (currentTime - turnKeyReleaseTime > TURN_KEY_GRACE_PERIOD) {
            // Grace period expired - end drift
            endDrift();
            return;
        }

        // Check if appropriate key is pressed again
        boolean rightPressed = minecraft.options.keyRight.isDown();
        boolean leftPressed = minecraft.options.keyLeft.isDown();
        if ((isDriftingRight && rightPressed) || (!isDriftingRight && leftPressed)) {
            // Correct key pressed again - cancel grace period
            isInGracePeriod = false;
            turnKeyReleaseTime = 0;
        }
    }

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

            if (isPressed && !isDrifting) {
                // Starting a new drift
                startDrift(minecraft);
            } else if (!isPressed && isDrifting) {
                // Space key released, stop drifting immediately
                endDrift();
            }
        } else if (isDrifting) {
            // Check for turn key events
            boolean isRightKey = event.getKey() == minecraft.options.keyRight.getKey().getValue();
            boolean isLeftKey = event.getKey() == minecraft.options.keyLeft.getKey().getValue();

            if ((isRightKey && isDriftingRight) || (isLeftKey && !isDriftingRight)) {
                if (event.getAction() == GLFW.GLFW_RELEASE) {
                    // Turn key released - start grace period
                    turnKeyReleaseTime = System.currentTimeMillis();
                    isInGracePeriod = true;
                } else if (event.getAction() == GLFW.GLFW_PRESS) {
                    // Turn key pressed again during grace period
                    isInGracePeriod = false;
                    turnKeyReleaseTime = 0;
                }
            }
        }
    }

    private void startDrift(Minecraft minecraft) {
        isDrifting = true;
        isInGracePeriod = false;
        turnKeyReleaseTime = 0;
        isDriftingRight = minecraft.options.keyRight.isDown();

        if (!isDriftingRight && !minecraft.options.keyLeft.isDown()) {
            // If no turn key is pressed, use the vehicle's current rotation
            float yaw = minecraft.player.yRot % 360;
            if (yaw < 0) yaw += 360;
            isDriftingRight = (yaw >= 45 && yaw < 225);
        }

        Messages.INSTANCE.sendToServer(new CMessageDriftState(true, isDriftingRight));
    }

    private void endDrift() {
        isDrifting = false;
        isInGracePeriod = false;
        turnKeyReleaseTime = 0;
        Messages.INSTANCE.sendToServer(new CMessageDriftState(false, isDriftingRight));
    }
}