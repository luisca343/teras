package es.boffmedia.teras.dungeon.entity.goal;

import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Movement for a {@link es.boffmedia.teras.dungeon.entity.Movement#FLYER}: it goes where it is going
 * in three dimensions, and nothing pulls it down while it does.
 *
 * <p>The other half of flight, and the half that was missing. {@code createNavigation} has built a
 * {@code FlyingPathNavigation} for FLYER since the mode existed, so a flyer computed a path through
 * the air and then walked the ground under it with a move control that only understands forward and
 * turn — the same shape of bug as the climber that had navigation and no {@code onClimbable}. A
 * declared mode is finished when someone has watched the mob move that way.</p>
 *
 * <p>Deliberately simple: steer toward the target and clamp the speed, rather than vanilla's
 * {@code FlyingMoveControl}, which reads the entity's own yaw and pitch and is built for something
 * that banks. A bat in a cave room is going somewhere eight blocks away; what matters is that it
 * arrives, that it does not fall while thinking about it, and that it does not accelerate into a
 * wall.</p>
 */
public class FlyMoveControl extends MoveControl {

    /** Fraction of the remaining distance covered per tick, before the speed clamp. */
    private static final double APPROACH = 0.18;

    /** How sharply the drift settles on the wanted heading; a flyer that snaps reads as a projectile. */
    private static final double SMOOTHING = 0.65;

    private final DungeonGeoEnemy enemy;

    public FlyMoveControl(DungeonGeoEnemy enemy) {
        super(enemy);
        this.enemy = enemy;
    }

    @Override
    public void tick() {
        if (operation != Operation.MOVE_TO) {
            // Nothing wants it anywhere. It hovers rather than stopping dead: a flyer with zero
            // velocity and no gravity is a decoration hanging in the air.
            enemy.setNoGravity(true);
            enemy.setDeltaMovement(enemy.getDeltaMovement().multiply(0.9, 0.9, 0.9));
            return;
        }
        operation = Operation.WAIT;
        enemy.setNoGravity(true);

        Vec3 toward = new Vec3(wantedX - enemy.getX(), wantedY - enemy.getY(), wantedZ - enemy.getZ());
        double distance = toward.length();
        if (distance < 0.1) {
            enemy.setDeltaMovement(enemy.getDeltaMovement().multiply(0.5, 0.5, 0.5));
            return;
        }
        double speed = speedModifier * enemy.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
        Vec3 wanted = toward.scale(Math.min(APPROACH, speed / distance));
        // Blended rather than assigned, so a change of target curves instead of cornering.
        enemy.setDeltaMovement(enemy.getDeltaMovement().scale(1 - SMOOTHING).add(wanted.scale(SMOOTHING)));

        // Face the way it is travelling. Read off the velocity and not off `wanted`, so the model
        // points where it is actually going during the blend rather than where it intends to be.
        Vec3 motion = enemy.getDeltaMovement();
        if (motion.horizontalDistanceSqr() > 1.0E-5) {
            float yaw = (float) (Mth.atan2(motion.z, motion.x) * (180 / Math.PI)) - 90f;
            enemy.setYRot(rotlerp(enemy.getYRot(), yaw, 20f));
        }
        enemy.yBodyRot = enemy.getYRot();
    }
}
