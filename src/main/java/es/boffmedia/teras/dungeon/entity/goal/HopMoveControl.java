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

    /** Ticks on the ground between hops. Vanilla slimes idle far longer; a dungeon wants pressure. */
    private static final int INTERVAL = 11;

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
        enemy.setYRot(rotlerp(enemy.getYRot(), wantedYRot, 90f));
        enemy.yHeadRot = enemy.getYRot();
        enemy.yBodyRot = enemy.getYRot();

        if (operation != Operation.MOVE_TO) {
            // Nothing wants it anywhere: stand still rather than drift.
            enemy.setZza(0);
            return;
        }
        operation = Operation.WAIT;
        if (!enemy.onGround()) {
            // Mid-hop: keep the forward input so the arc carries it, and never steer — a hop that
            // can be turned in the air is flight.
            enemy.setSpeed(travelSpeed());
            return;
        }
        double dx = wantedX - enemy.getX();
        double dz = wantedZ - enemy.getZ();
        if (dx * dx + dz * dz > 1.0E-7) {
            wantedYRot = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        }
        enemy.setSpeed(travelSpeed());
        if (cooldown-- <= 0) {
            cooldown = INTERVAL;
            // The jump control, not a hand-written velocity. Setting deltaMovement here fought the
            // control's own jump on the same tick, which is what turned the bounce into a shuffle.
            enemy.getJumpControl().jump();
            enemy.playHopAnimation();
        } else {
            // Between hops it is genuinely inert. Zeroing zza alone is not enough: Mob.setSpeed
            // writes zza too, so leaving `speed` set kept feeding travel() a forward input every
            // tick — which is exactly the sliding this control exists to remove.
            enemy.xxa = 0f;
            enemy.zza = 0f;
            enemy.setSpeed(0f);
        }
    }

    /** The variant's own speed, so a heavier blob closes slower without its own control. */
    private float travelSpeed() {
        return (float) (speedModifier * enemy.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
    }
}
