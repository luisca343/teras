package es.boffmedia.teras.util.objects.karts;

import com.mrcrayfish.vehicle.entity.PoweredVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

public class VehicleHitHandler {
    private static final double VERTICAL_FORCE = 0.5; // Upward bounce
    private static final double SPIN_FORCE = 45.0; // Degrees per tick
    private static final int STUN_DURATION = 40; // Duration in ticks (2 seconds)
    private static final double KNOCKBACK_FORCE = 0.8; // Horizontal force

    private final PoweredVehicleEntity vehicle;
    private int stunTicks = 0;
    private float targetRotation;
    private double verticalVelocity;
    private boolean isStunned = false;

    public VehicleHitHandler(PoweredVehicleEntity vehicle) {
        this.vehicle = vehicle;
    }

    public void applyHit(Vector3d hitDirection) {
        if (isStunned) return;

        // Disable engine temporarily
        vehicle.setEngine(false);
        isStunned = true;
        stunTicks = STUN_DURATION;

        // Calculate hit response
        // Random spin direction
        float spinDirection = vehicle.level.random.nextBoolean() ? 1.0f : -1.0f;
        targetRotation = vehicle.yRot + (360.0f * spinDirection);

        // Apply vertical bounce
        verticalVelocity = VERTICAL_FORCE;

        // Apply horizontal knockback
        Vector3d knockback = hitDirection.normalize().multiply(KNOCKBACK_FORCE, 0, KNOCKBACK_FORCE);
        vehicle.setDeltaMovement(knockback.x, verticalVelocity, knockback.z);

        // Play hit sound and particles
        playHitEffects();
    }

    public void tick() {
        if (!isStunned) return;

        if (stunTicks > 0) {
            // Update rotation
            float currentRotation = vehicle.yRot;
            float targetDelta = targetRotation - currentRotation;
            float rotationStep = (float)(SPIN_FORCE * (stunTicks / (double)STUN_DURATION));

            if (Math.abs(targetDelta) > rotationStep) {
                vehicle.yRot += Math.signum(targetDelta) * rotationStep;
                // Keep player rotation synced with vehicle
                if (vehicle.getControllingPassenger() instanceof ServerPlayerEntity) {
                    vehicle.getControllingPassenger().yRot = vehicle.yRot;
                }
            }

            // Update vertical movement
            Vector3d motion = vehicle.getDeltaMovement();
            verticalVelocity = Math.max(verticalVelocity - 0.08, -0.8); // Apply gravity
            vehicle.setDeltaMovement(motion.x * 0.95, verticalVelocity, motion.z * 0.95);

            stunTicks--;

            // Recovery
            if (stunTicks <= 0) {
                recoverFromStun();
            }
        }
    }

    private void recoverFromStun() {
        isStunned = false;
        vehicle.setEngine(true);
        // Apply a small boost to help the player recover
        Vector3d lookVector = new Vector3d(
                -Math.sin(vehicle.yRot * 0.017453292F),
                0,
                Math.cos(vehicle.yRot * 0.017453292F)
        );
        vehicle.setDeltaMovement(lookVector.scale(0.3));
    }

    private void playHitEffects() {
        // Play crash sound
        vehicle.level.playSound(null,
                vehicle.getX(),
                vehicle.getY(),
                vehicle.getZ(),
                SoundEvents.ANVIL_LAND, // Or any other appropriate sound
                SoundCategory.PLAYERS,
                1.0F,
                1.0F);

        // Spawn particles
        if (vehicle.level instanceof ServerWorld) {
            ServerWorld serverWorld = (ServerWorld)vehicle.level;
            for (int i = 0; i < 20; i++) {
                double px = vehicle.getX() + (vehicle.level.random.nextDouble() - 0.5) * 2;
                double py = vehicle.getY() + vehicle.level.random.nextDouble() * 2;
                double pz = vehicle.getZ() + (vehicle.level.random.nextDouble() - 0.5) * 2;

                serverWorld.sendParticles(ParticleTypes.EXPLOSION,
                        px, py, pz,
                        1, 0, 0, 0, 0);
            }
        }
    }

    public boolean isStunned() {
        return isStunned;
    }
}