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
    private static final float MAX_DRIFT_ANGLE = 45.0f;
    private Vector3d preBoostVelocity = null; // Store the velocity from before boost started

    private Vector3d lastPosition = null;
    private float targetDriftAngle = 0.0f;
    private double currentSpeed = 0.0;


    private static final float MIN_DRIFT_ANGLE = 20.0f;  // Minimum angle to maintain drift
    private static final float DRIFT_ENTRY_SPEED = 0.3f; // Minimum speed to initiate drift
    private static final float GRIP_FACTOR = 0.85f;      // How much the kart "grips" during drift (0-1)
    private static final float DRIFT_TURN_RATE = 2.5f;   // How fast the drift angle changes
    private static final float OUTWARD_FORCE = 0.15f;    // Force pushing kart outward during drift
    private static final float DRIFT_RECOVERY_RATE = 4f; // How fast drift angle returns to normal

    public VehicleDriftHandler(PoweredVehicleEntity vehicle) {
        this.vehicle = vehicle;
    }

    public void startDrift(boolean driftRight) {
        if (!isDrifting && getCurrentSpeed() >= DRIFT_ENTRY_SPEED) {
            isDrifting = true;
            driftTicks = 0;
            boostLevel = 0;
            // Initial drift angle based on direction, starts at minimum
            targetDriftAngle = driftRight ? MIN_DRIFT_ANGLE : -MIN_DRIFT_ANGLE;
            driftAngle = targetDriftAngle * 0.5f; // Smooth entry

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

    private double getCurrentSpeed() {
        Vector3d motion = vehicle.getDeltaMovement();
        return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
    }



    public void endDrift() {
        if (isDrifting) {
            isDrifting = false;
            applyBoost();

            // Smooth drift angle recovery
            new Thread(() -> {
                try {
                    float originalAngle = driftAngle;
                    for (int i = 0; i < 5; i++) { // 5 tick recovery
                        driftAngle = originalAngle * (1 - (i / 5.0f));
                        Thread.sleep(50); // 1 tick = 50ms
                    }
                    driftAngle = 0.0f;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private static final int BOOST_DURATION = 40; // 2 seconds at 20 ticks per second
    private int boostTimeRemaining = 0;
    private double currentBoostStrength = 0.0;
    private boolean isBoosting = false;

    public void tick() {
        if (isDrifting) {
            driftTicks++;
            updateBoostLevel();
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
                preBoostVelocity = null; // Clear stored velocity
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

        // Calculate boost multiplier based on level
        switch (boostLevel) {
            case 1:
                currentBoostStrength = 1.3; // 30% speed increase for blue spark
                break;
            case 2:
                currentBoostStrength = 1.6; // 60% speed increase for orange spark
                break;
            case 3:
                currentBoostStrength = 2.0; // Double speed for purple spark
                break;
            default:
                currentBoostStrength = 1.0;
        }

        // Start the boost
        isBoosting = true;
        boostTimeRemaining = BOOST_DURATION;

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

        float particleIntensity = (float)boostTimeRemaining / BOOST_DURATION;
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

        // Update current speed
        currentSpeed = getCurrentSpeed();

        // Don't drift if too slow
        if (currentSpeed < DRIFT_ENTRY_SPEED * 0.7) {
            endDrift();
            return;
        }

        // Update target drift angle based on player input
        // Assuming positive is right turn, negative is left turn
        float turnInput = player.xxa; // Player's strafe input
        if (Math.abs(turnInput) > 0.1f) {
            float turnDirection = Math.signum(targetDriftAngle);
            targetDriftAngle += turnInput * DRIFT_TURN_RATE * turnDirection;
            targetDriftAngle = MathHelper.clamp(targetDriftAngle,
                    -MAX_DRIFT_ANGLE, MAX_DRIFT_ANGLE);
        }

        // Smoothly interpolate current drift angle towards target
        float angleDiff = targetDriftAngle - driftAngle;
        driftAngle += angleDiff * 0.2f;

        // Calculate the desired movement direction based on drift angle
        float yaw = vehicle.yRot + driftAngle;
        Vector3d moveDir = new Vector3d(
                -MathHelper.sin(yaw * 0.017453292F),
                0,
                MathHelper.cos(yaw * 0.017453292F)
        );

        // Calculate perpendicular vector for outward force
        Vector3d perpDir = moveDir.cross(new Vector3d(0, 1, 0)).normalize();
        perpDir = perpDir.scale(Math.signum(driftAngle)); // Match drift direction

        // Get current motion
        Vector3d motion = vehicle.getDeltaMovement();

        // Combine forward and sideways forces
        // Forward force is reduced during drift (grip factor)
        Vector3d newMotion = moveDir.scale(currentSpeed * GRIP_FACTOR)
                .add(perpDir.scale(OUTWARD_FORCE * Math.abs(driftAngle/MAX_DRIFT_ANGLE) * currentSpeed));

        // Maintain vertical motion
        newMotion = newMotion.multiply(1, 0, 1).add(0, motion.y, 0);

        // Apply the new motion
        vehicle.setDeltaMovement(newMotion);

        // Update vehicle and player rotation
        // We apply the visual drift angle to the vehicle but keep player rotation smoother
        vehicle.yRot = yaw;
        player.yRot = vehicle.yRot - (driftAngle * 0.5f); // Player faces more forward
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

    public PoweredVehicleEntity getVehicle() {
        return vehicle;
    }
}