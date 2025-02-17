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
    private static final float MAX_DRIFT_ANGLE = 15.0f;
    private Vector3d preBoostVelocity = null; // Store the velocity from before boost started

    // Boost durations in ticks (20 ticks = 1 second)
    private static final int BLUE_BOOST_DURATION = 20;   // 1 second
    private static final int ORANGE_BOOST_DURATION = 40;  // 2 seconds
    private static final int PURPLE_BOOST_DURATION = 60;  // 3 seconds

    private float targetDriftAngle = 0.0f;
    private float vehicleStartYaw = 0.0f;
    private static final float DRIFT_ANGLE_SPEED = 1.5f;

    private int boostTimeRemaining = 0;
    private double currentBoostStrength = 0.0;
    private boolean isBoosting = false;

    public VehicleDriftHandler(PoweredVehicleEntity vehicle) {
        this.vehicle = vehicle;
    }

    public void startDrift(boolean driftRight, float vehicleYaw) {
        if (!isDrifting) {
            isDrifting = true;
            driftTicks = 0;
            boostLevel = 0;
            vehicleStartYaw = vehicleYaw;

            // Set target drift angle based on direction
            targetDriftAngle = driftRight ? MAX_DRIFT_ANGLE : -MAX_DRIFT_ANGLE;
            driftAngle = 0.0f; // Start from 0 and smoothly interpolate

            // Play drift start sound
            vehicle.level.playSound(null,
                    vehicle.getX(),
                    vehicle.getY(),
                    vehicle.getZ(),
                    SoundEvents.SPLASH_POTION_BREAK,
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
        if (isDrifting) {
            driftTicks++;
            updateBoostLevel();

            // Smoothly interpolate drift angle
            if (Math.abs(driftAngle - targetDriftAngle) > 0.1f) {
                float angleDiff = targetDriftAngle - driftAngle;
                driftAngle += Math.signum(angleDiff) * DRIFT_ANGLE_SPEED;
                // Clamp to max angle
                driftAngle = MathHelper.clamp(driftAngle, -MAX_DRIFT_ANGLE, MAX_DRIFT_ANGLE);
            }

            applyDriftPhysics();
            spawnDriftParticles();
        }

        // Handle active boost
        if (isBoosting && boostTimeRemaining > 0) {
            boostTimeRemaining--;
            applyCurrentBoost();
            spawnBoostParticles();

            // Gradually decrease boost strength for smooth transition
            if (boostTimeRemaining < 20) { // Last second
                currentBoostStrength = MathHelper.lerp(1 - (boostTimeRemaining / 20.0), 1.0, currentBoostStrength);
            }

            if (boostTimeRemaining <= 0) {
                isBoosting = false;
                currentBoostStrength = 0.0;
                preBoostVelocity = null;
            }
        }
    }

    private void applyCurrentBoost() {
        if (preBoostVelocity == null) return;

        // Calculate forward direction
        Vector3d forward = new Vector3d(
                -MathHelper.sin(vehicle.yRot * 0.017453292F),
                0,
                MathHelper.cos(vehicle.yRot * 0.017453292F)
        );

        // Get the horizontal speed from pre-boost velocity
        double baseSpeed = new Vector3d(preBoostVelocity.x, 0, preBoostVelocity.z).length();

        // Apply boost multiplier to base speed
        double boostedSpeed = baseSpeed * currentBoostStrength;

        // Maintain current vertical motion
        double verticalMotion = vehicle.getDeltaMovement().y;

        // Set new motion while preserving vertical component
        Vector3d newMotion = forward.scale(boostedSpeed).multiply(1, 0, 1).add(0, verticalMotion, 0);
        vehicle.setDeltaMovement(newMotion);
    }

    private void applyBoost() {
        if (boostLevel == 0) return;

        // Store the current velocity before applying boost
        preBoostVelocity = vehicle.getDeltaMovement();

        // All boost types have 2x multiplier but different durations
        currentBoostStrength = 2.0; // Double speed for all boost types

        // Set duration based on boost level
        switch (boostLevel) {
            case 1:
                boostTimeRemaining = BLUE_BOOST_DURATION;   // 1 second
                break;
            case 2:
                boostTimeRemaining = ORANGE_BOOST_DURATION; // 2 seconds
                break;
            case 3:
                boostTimeRemaining = PURPLE_BOOST_DURATION; // 3 seconds
                break;
            default:
                boostTimeRemaining = 0;
                currentBoostStrength = 1.0;
        }

        // Start the boost
        isBoosting = true;

        // Play boost sound
        vehicle.level.playSound(null,
                vehicle.getX(),
                vehicle.getY(),
                vehicle.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F);
    }

    private void spawnBoostParticles() {
        if (!(vehicle.level instanceof ServerWorld)) return;
        ServerWorld serverWorld = (ServerWorld) vehicle.level;

        float particleIntensity = (float)boostTimeRemaining / PURPLE_BOOST_DURATION;
        int particleCount = (int)(particleIntensity * 25);

        switch (boostLevel) {
            case 1: // Blue boost
                // Soul fire flame for blue effect
                for (int i = 0; i < particleCount * 0.6; i++) {
                    serverWorld.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            vehicle.getX(), vehicle.getY() + 0.5, vehicle.getZ(),
                            1, 0.2, 0.2, 0.2, 0.1 * particleIntensity);
                    if (i % 3 == 0) {
                        serverWorld.sendParticles(ParticleTypes.END_ROD,
                                vehicle.getX(), vehicle.getY() + 0.3, vehicle.getZ(),
                                1, 0.1, 0.1, 0.1, 0.05 * particleIntensity);
                    }
                }
                break;

            case 2: // Orange boost
                for (int i = 0; i < particleCount * 0.8; i++) {
                    serverWorld.sendParticles(ParticleTypes.FLAME,
                            vehicle.getX(), vehicle.getY() + 0.5, vehicle.getZ(),
                            1, 0.2, 0.2, 0.2, 0.1 * particleIntensity);
                    if (i % 2 == 0) {
                        serverWorld.sendParticles(ParticleTypes.LAVA,
                                vehicle.getX(), vehicle.getY() + 0.3, vehicle.getZ(),
                                1, 0.1, 0.1, 0.1, 0.05 * particleIntensity);
                    }
                }
                break;

            case 3: // Purple boost
                for (int i = 0; i < particleCount; i++) {
                    // Witch particles for the purple effect
                    serverWorld.sendParticles(ParticleTypes.WITCH,
                            vehicle.getX(), vehicle.getY() + 0.5, vehicle.getZ(),
                            1, 0.3, 0.3, 0.3, 0.1 * particleIntensity);
                    // Dragon breath for the trail
                    if (i % 2 == 0) {
                        serverWorld.sendParticles(ParticleTypes.DRAGON_BREATH,
                                vehicle.getX(), vehicle.getY() + 0.3, vehicle.getZ(),
                                1, 0.2, 0.2, 0.2, 0.05 * particleIntensity);
                    }
                    // Occasional end rod particles for sparkle
                    if (i % 4 == 0) {
                        serverWorld.sendParticles(ParticleTypes.END_ROD,
                                vehicle.getX(), vehicle.getY() + 0.7, vehicle.getZ(),
                                1, 0.1, 0.1, 0.1, 0.05 * particleIntensity);
                    }
                }
                break;
        }
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

        // Calculate drift yaw based on vehicle's initial direction and drift angle
        float driftYaw = vehicle.yRot + driftAngle;

        // Calculate the movement direction
        Vector3d moveDir = new Vector3d(
                -MathHelper.sin(driftYaw * 0.017453292F),
                0,
                MathHelper.cos(driftYaw * 0.017453292F)
        );

        // Apply sideways force based on drift angle
        double sidewaysForce = Math.abs(driftAngle) * 0.01;
        Vector3d sideDir = moveDir.cross(new Vector3d(0, 1, 0)).normalize();

        // Get current motion and modify it
        Vector3d motion = vehicle.getDeltaMovement();
        double speed = motion.length();

        // Combine forward and sideways movement
        Vector3d newMotion = moveDir.scale(speed * 0.6)
                .add(sideDir.scale(sidewaysForce));

        // Apply the new motion while preserving vertical component
        vehicle.setDeltaMovement(newMotion.x, motion.y, newMotion.z);
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

        // Base drift particles for all levels
        for (Vector3d pos : wheelPositions) {
            // Smoke trail for drifting
            serverWorld.sendParticles(ParticleTypes.CLOUD,
                    pos.x, pos.y, pos.z,
                    1, 0, 0, 0, 0);

            // Boost level particles
            switch (boostLevel) {
                case 1: // Blue sparks
                    // Bright blue particles
                    serverWorld.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.x, pos.y + 0.2, pos.z,
                            2, 0.1, 0.1, 0.1, 0.05);
                    // Additional sparkle effect
                    if (vehicle.level.random.nextInt(3) == 0) {
                        serverWorld.sendParticles(ParticleTypes.END_ROD,
                                pos.x, pos.y + 0.3, pos.z,
                                1, 0.1, 0.1, 0.1, 0.05);
                    }
                    break;

                case 2: // Orange sparks
                    // Orange flame core
                    serverWorld.sendParticles(ParticleTypes.FLAME,
                            pos.x, pos.y + 0.2, pos.z,
                            2, 0.1, 0.1, 0.1, 0.05);
                    // Lava sparks for extra effect
                    if (vehicle.level.random.nextInt(2) == 0) {
                        serverWorld.sendParticles(ParticleTypes.LAVA,
                                pos.x, pos.y + 0.3, pos.z,
                                1, 0.1, 0.1, 0.1, 0.02);
                    }
                    break;

                case 3: // Purple sparks
                    // Purple core effect
                    serverWorld.sendParticles(ParticleTypes.WITCH,
                            pos.x, pos.y + 0.2, pos.z,
                            3, 0.1, 0.1, 0.1, 0.1);
                    // Dragon breath for the trail
                    serverWorld.sendParticles(ParticleTypes.DRAGON_BREATH,
                            pos.x, pos.y + 0.3, pos.z,
                            2, 0.1, 0.1, 0.1, 0.05);
                    // Occasional flash effects
                    if (vehicle.level.random.nextInt(2) == 0) {
                        serverWorld.sendParticles(ParticleTypes.END_ROD,
                                pos.x, pos.y + 0.4, pos.z,
                                1, 0.1, 0.1, 0.1, 0.05);
                    }
                    break;
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

    public boolean isBoosting() {
        return isBoosting;
    }

    public PoweredVehicleEntity getVehicle() {
        return vehicle;
    }
}