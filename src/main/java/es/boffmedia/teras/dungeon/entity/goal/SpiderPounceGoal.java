package es.boffmedia.teras.dungeon.entity.goal;

import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The spider's pounce: from a short distance, a telegraphed leap that closes the gap so the bite
 * lands. Replaces the vanilla {@link net.minecraft.world.entity.ai.goal.LeapAtTargetGoal}, which
 * leaps on any horizontal approach and offers no hook to drive the jump animation — this one gates
 * on a cooldown so the move reads as a decision, and fires the {@code POUNCE} action so the client
 * plays the leap clip.
 *
 * <p>The bite itself is left to the melee goal: the pounce only delivers the spider to arm's reach,
 * and closing that gap is the whole point of the move.</p>
 */
public class SpiderPounceGoal extends Goal {

    private final DungeonGeoEnemy spider;
    private int cooldown;
    private LivingEntity target;

    public SpiderPounceGoal(DungeonGeoEnemy spider) {
        this.spider = spider;
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        target = spider.getTarget();
        if (target == null || !spider.onGround()) {
            return false;
        }
        double d = spider.distanceToSqr(target);
        // 2..7 blocks: close enough to reach, far enough that a leap beats a walk.
        return d >= 4.0 && d <= 49.0 && spider.hasLineOfSight(target);
    }

    /** One-shot: the leap is an impulse, and letting it own MOVE for longer would fight the nav. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        Vec3 to = new Vec3(target.getX() - spider.getX(), 0, target.getZ() - spider.getZ());
        if (to.lengthSqr() > 1.0e-4) {
            to = to.normalize().scale(0.85);
        }
        spider.setDeltaMovement(to.x, 0.45, to.z);
        spider.getLookControl().setLookAt(target, 30f, 30f);
        spider.triggerAction(DungeonGeoEnemy.Action.POUNCE, 12);
        cooldown = 70 + spider.getRandom().nextInt(60);
    }
}
