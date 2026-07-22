package es.boffmedia.teras.dungeon.entity.goal;

import es.boffmedia.teras.dungeon.entity.DungeonBolt;
import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import es.boffmedia.teras.dungeon.mecanica.WebPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The queen's signature: climb above the target and rain webbing down.
 *
 * <p>Two phases. In <b>ASCEND</b> she rises toward the ceiling — driven directly rather than through
 * navigation, because "hang from overhead" is not a path a ground graph produces; gravity is held
 * off while she climbs so the move is reliable in any room. In <b>WEB</b> she lobs thin web at the
 * target's feet on a fast cadence and drops the occasional dense strand beside them — a temporary
 * wall, never on top of them, because a solid block on the player would be a trap rather than a
 * hazard. When it ends she simply falls, which reads as the drop back into the fight.</p>
 *
 * <p>Everything decays ({@link WebPlacer}), so however much she webs, the room can never seal itself.
 * On a long cooldown: the ascent is a set-piece, not a stance.</p>
 */
public class SpiderCeilingWebGoal extends Goal {

    private enum Phase { ASCEND, WEB }

    /** How far above the target counts as "overhead" before she starts webbing. */
    private static final double CLIMB_HEIGHT = 5.0;
    private static final int MAX_ASCEND_TICKS = 50;
    private static final int WEB_TICKS = 70;

    private final DungeonGeoEnemy spider;
    private Phase phase;
    private int phaseTicks;
    private int cooldown = 120;
    private LivingEntity target;

    public SpiderCeilingWebGoal(DungeonGeoEnemy spider) {
        this.spider = spider;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        target = spider.getTarget();
        return target != null && target.isAlive()
                && spider.distanceToSqr(target) < 32 * 32
                && spider.level() instanceof ServerLevel;
    }

    @Override
    public boolean canContinueToUse() {
        return phase != null && target != null && target.isAlive();
    }

    @Override
    public void start() {
        phase = Phase.ASCEND;
        phaseTicks = 0;
        // Claim gravity before switching it off. DungeonGeoEnemy suspends it too, for the ordinary
        // ceiling cling every climber has, and it restores gravity on any tick it decides it is not
        // hanging — which mid-ascent would drop the queen out of her own set-piece.
        spider.setScriptedFlight(true);
        spider.setNoGravity(true);
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        spider.getLookControl().setLookAt(target, 30f, 30f);
        phaseTicks++;
        if (phase == Phase.ASCEND) {
            ascend();
            if (spider.getY() - target.getY() >= CLIMB_HEIGHT || phaseTicks >= MAX_ASCEND_TICKS
                    || spider.verticalCollision) {
                phase = Phase.WEB;
                phaseTicks = 0;
            }
        } else {
            web();
            if (phaseTicks >= WEB_TICKS) {
                stop();
            }
        }
    }

    /** Rise, drifting toward the target so she ends up overhead rather than straight up a far wall. */
    private void ascend() {
        Vec3 toward = new Vec3(target.getX() - spider.getX(), 0, target.getZ() - spider.getZ());
        if (toward.lengthSqr() > 1.0e-4) {
            toward = toward.normalize().scale(0.12);
        }
        spider.setDeltaMovement(toward.x, 0.22, toward.z);
        spider.hasImpulse = true;
    }

    private void web() {
        // Held aloft while she webs — the drop comes when the goal ends.
        Vec3 d = spider.getDeltaMovement();
        spider.setDeltaMovement(d.x * 0.6, 0.0, d.z * 0.6);
        if (phaseTicks % 7 == 0) {
            DungeonBolt.shoot(spider, target, DungeonBolt.Kind.WEB, 0f, 1.0f);
            spider.triggerAction(DungeonGeoEnemy.Action.SHOOT, 10);
        }
        if (phaseTicks % 24 == 12 && spider.level() instanceof ServerLevel level) {
            // A dense strand two blocks to the target's side — a partial wall, never underfoot.
            Vec3 side = new Vec3(target.getX() - spider.getX(), 0, target.getZ() - spider.getZ());
            side = side.lengthSqr() > 1.0e-4 ? side.normalize() : new Vec3(1, 0, 0);
            BlockPos wall = target.blockPosition().offset(
                    (int) Math.round(side.z * 2), 0, (int) Math.round(-side.x * 2));
            WebPlacer.placeDense(level, wall, false);
        }
    }

    @Override
    public void stop() {
        phase = null;
        phaseTicks = 0;
        spider.setNoGravity(false);
        spider.setScriptedFlight(false);
        cooldown = 240 + spider.getRandom().nextInt(120);
    }
}
