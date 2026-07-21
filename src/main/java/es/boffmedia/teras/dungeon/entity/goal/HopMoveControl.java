package es.boffmedia.teras.dungeon.entity.goal;

import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Movement for a {@link es.boffmedia.teras.dungeon.entity.Movement#HOPPER}: it bounces to where it
 * is going, and between bounces it does not move at all.
 *
 * <p>This is a <b>move control</b> rather than a goal, and that is the point. A hop added as a goal
 * runs alongside whatever is already walking the entity, so the enemy glides across the floor and
 * jumps on top of the glide — the thing a slime must never do, and the reason the CustomNPCs clone
 * could not be made to work: there its walking and its melee damage were the same goal, so removing
 * one removed the other. Here the walk is simply not implemented: ordinary goals still choose a
 * <i>destination</i> through {@link #setWantedPosition}, and this decides that reaching it means
 * hopping.</p>
 *
 * <p>Modelled on vanilla's {@code SlimeMoveControl}, with the jump cadence and the turn kept here so
 * a variant's speed attribute still scales how fast it closes.</p>
 */
public class HopMoveControl extends MoveControl {

    /** Ticks between hops when it has somewhere to be. */
    private static final int INTERVAL = 11;
    /** Upward impulse. Enough to clear a slab and to read as a bounce across a room. */
    private static final double JUMP = 0.42;

    private final DungeonGeoEnemy enemy;
    private int cooldown;
    private float wantedYRot;

    public HopMoveControl(DungeonGeoEnemy enemy) {
        super(enemy);
        this.enemy = enemy;
        this.wantedYRot = 180f * enemy.getYRot() / (float) Math.PI;
    }

    @Override
    public void tick() {
        // Face the destination even while grounded, so the hop that follows goes where it looked
        // rather than snapping mid-air.
        enemy.setYRot(rotlerp(enemy.getYRot(), wantedYRot, 30f));
        enemy.yHeadRot = enemy.getYRot();
        enemy.yBodyRot = enemy.getYRot();

        if (operation != Operation.MOVE_TO) {
            // Nothing wants it anywhere: stand still rather than drift.
            enemy.setZza(0);
            return;
        }
        operation = Operation.WAIT;
        if (!enemy.onGround()) {
            // Mid-hop. Momentum carries it; steering in the air would make it fly.
            return;
        }
        double dx = wantedX - enemy.getX();
        double dz = wantedZ - enemy.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-5) {
            enemy.setZza(0);
            return;
        }
        wantedYRot = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        if (--cooldown > 0) {
            // Between hops it is inert, which is what a slime looks like on the ground.
            enemy.setZza(0);
            return;
        }
        cooldown = INTERVAL;
        enemy.getJumpControl().jump();
        enemy.setDeltaMovement(enemy.getDeltaMovement().x, JUMP, enemy.getDeltaMovement().z);
        // The forward push rides the variant's speed attribute, so a heavier blob closes slower
        // without needing its own control.
        float speed = (float) (speedModifier
                * enemy.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
        enemy.setSpeed(speed);
        enemy.setZza(1.0f);
        enemy.playHopAnimation();
    }
}
