package es.boffmedia.teras.util.objects.karts;

import com.mrcrayfish.vehicle.entity.PoweredVehicleEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

public class VehicleDriftHandler {
    private final PoweredVehicleEntity vehicle;
    private boolean isDrifting = false;
    private int driftTicks = 0;
    private float driftAngle = 0.0f;
    private int boostLevel = 0; // 0 = no boost, 1 = blue spark, 2 = orange spark, 3 = purple spark
    private static final int BLUE_SPARK_THRESHOLD = 20;   // 1 second
    private static final int ORANGE_SPARK_THRESHOLD = 60; // 3 seconds
    private static final int PURPLE_SPARK_THRESHOLD = 100; // 5 seconds
    private static final float DRIFT_TURN_SPEED = 2.0f;
    private static final float MAX_DRIFT_ANGLE = 45.0f;

    public VehicleDriftHandler(PoweredVehicleEntity vehicle) {
        this.vehicle = vehicle;
    }

    public void startDrift(boolean driftRight) {
        if (!isDrifting) {
            isDrifting = true;
            driftTicks = 0;
            boostLevel = 0;
            // Initial drift angle based on direction
            driftAngle = driftRight ? 15.0f : -15.0f;

            // Play drift start sound
            vehicle.level.playSound(null,
                    vehicle.getX(),
                    vehicle.getY(),
                    vehicle.getZ(),
                    SoundEvents.SPLASH_POTION_BREAK, // You might want to use a different sound
                    SoundCategory.PLAYERS,
                    1.0F,
                    1.5F);
        }
    }

    public void endDrift() {
        if (isDrifting) {
            isDrifting = false;
            applyBoost();
            driftAngle = 0.0f;
            driftTicks = 0;
        }
    }

    public void tick() {
        if (!isDrifting) return;

        driftTicks++;
        updateBoostLevel();
        applyDriftPhysics();
        spawnDriftParticles();
    }

    private void updateBoostLevel() {
        if (driftTicks >= PURPLE_SPARK_THRESHOLD) {
            boostLevel = 3;
        } else if (driftTicks >= ORANGE_SPARK_THRESHOLD) {
            boostLevel = 2;
        } else if (driftTicks >= BLUE_SPARK_THRESHOLD) {
            boostLevel = 1;
        }
    }

    private void applyDriftPhysics() {
        // Get the controlling player
        if (!(vehicle.getControllingPassenger() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) vehicle.getControllingPassenger();

        // Calculate the desired movement direction based on drift angle
        float yaw = vehicle.yRot + driftAngle;
        Vector3d moveDir = new Vector3d(
                -MathHelper.sin(yaw * 0.017453292F),
                0,
                MathHelper.cos(yaw * 0.017453292F)
        );

        // Apply sideways force based on drift angle
        double sidewaysForce = Math.abs(driftAngle) * 0.01;
        Vector3d sideDir = moveDir.cross(new Vector3d(0, 1, 0)).normalize();

        // Get current motion and modify it
        Vector3d motion = vehicle.getDeltaMovement();
        double speed = motion.length();

        // Combine forward and sideways movement
        Vector3d newMotion = moveDir.scale(speed * 0.8)
                .add(sideDir.scale(sidewaysForce));

        // Apply the new motion
        vehicle.setDeltaMovement(newMotion);

        // Update vehicle and player rotation
        vehicle.yRot = yaw;
        player.yRot = yaw;
    }

    private void spawnDriftParticles() {
        if (!(vehicle.level instanceof ServerWorld)) return;
        ServerWorld serverWorld = (ServerWorld) vehicle.level;

        // Calculate wheel positions (simplified)
        double wheelOffset = 0.5;
        Vector3d[] wheelPositions = new Vector3d[] {
                new Vector3d(vehicle.getX() - wheelOffset, vehicle.getY(), vehicle.getZ() - wheelOffset),
                new Vector3d(vehicle.getX() + wheelOffset, vehicle.getY(), vehicle.getZ() - wheelOffset),
                new Vector3d(vehicle.getX() - wheelOffset, vehicle.getY(), vehicle.getZ() + wheelOffset),
                new Vector3d(vehicle.getX() + wheelOffset, vehicle.getY(), vehicle.getZ() + wheelOffset)
        };

        // Spawn particles at each wheel
        for (Vector3d pos : wheelPositions) {
            serverWorld.sendParticles(ParticleTypes.CLOUD,
                    pos.x, pos.y, pos.z,
                    1, 0, 0, 0, 0);
        }
    }

    private void applyBoost() {
        if (boostLevel == 0) return;

        // Calculate boost strength based on level
        double boostStrength;
        switch (boostLevel) {
            case 1:
                boostStrength = 1.3; // Blue spark boost
                break;
            case 2:
                boostStrength = 1.6; // Orange spark boost
                break;
            case 3:
                boostStrength = 2.0; // Purple spark boost
                break;
            default:
                boostStrength = 1.0;
        }

        // Apply boost in vehicle's forward direction
        Vector3d forward = new Vector3d(
                -MathHelper.sin(vehicle.yRot * 0.017453292F),
                0,
                MathHelper.cos(vehicle.yRot * 0.017453292F)
        );

        vehicle.setDeltaMovement(forward.scale(boostStrength));

        // Play boost sound
        vehicle.level.playSound(null,
                vehicle.getX(),
                vehicle.getY(),
                vehicle.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F);

        // Spawn boost particles
        if (vehicle.level instanceof ServerWorld) {
            ServerWorld serverWorld = (ServerWorld) vehicle.level;
            for (int i = 0; i < 20; i++) {
                serverWorld.sendParticles(ParticleTypes.FLAME,
                        vehicle.getX(), vehicle.getY() + 0.5, vehicle.getZ(),
                        1, 0, 0, 0, 0.1);
            }
        }
    }

    public boolean isDrifting() {
        return isDrifting;
    }

    public int getBoostLevel() {
        return boostLevel;
    }

    public float getDriftAngle() {
        return driftAngle;
    }

    public PoweredVehicleEntity getVehicle() {
        return vehicle;
    }
}