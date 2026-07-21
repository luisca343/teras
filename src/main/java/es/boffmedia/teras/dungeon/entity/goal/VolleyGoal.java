package es.boffmedia.teras.dungeon.entity.goal;

import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A telegraphed hit on the ground where the target is standing — the behaviour answered by moving
 * rather than by out-healing.
 *
 * <p>{@code VOLLEY} was in the vocabulary from the start and had no goal, so a variant declaring it
 * was handed a plain {@code RangedAttackGoal} by {@link DungeonGeoEnemy#rebuildGoals} and fired an
 * ordinary bolt <i>at</i> the target: the one behaviour whose whole point is that it does not track
 * you behaved identically to the one that does. This is what it was always supposed to be.</p>
 *
 * <p>The shape of the move is the telegraph: a marked patch of ground, a wind-up long enough to
 * walk out of, and then the hit — so standing still is the mistake and the counter costs nothing
 * but attention.</p>
 */
public class VolleyGoal extends Goal {

    /** Ticks between the mark appearing and the ground erupting. Long enough to walk clear. */
    private static final int WINDUP = 30;
    private static final double RADIUS = 2.6;

    private final DungeonGeoEnemy enemy;
    private final int cooldownTicks;
    private int cooldown;
    private int windup;
    private Vec3 mark;

    public VolleyGoal(DungeonGeoEnemy enemy) {
        this.enemy = enemy;
        this.cooldownTicks = Math.max(40, enemy.variant().rangedCooldown());
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = enemy.getTarget();
        if (target == null || !target.isAlive() || --cooldown > 0) {
            return false;
        }
        // Pointless up close — that is what its melee is for — and it must be able to see the floor
        // it is aiming at.
        return enemy.distanceToSqr(target) > 9 && enemy.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return windup > 0 && enemy.getTarget() != null;
    }

    @Override
    public void start() {
        LivingEntity target = enemy.getTarget();
        // The mark is where the target stood when it was cast, not where it stands now. That is the
        // entire counter-play: the ground is committed and the target is not.
        mark = target == null ? enemy.position() : target.position();
        windup = WINDUP;
        cooldown = cooldownTicks;
        enemy.triggerAction(DungeonGeoEnemy.Action.CAST, 12);
        enemy.level().playSound(null, enemy.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK,
                SoundSource.HOSTILE, 0.8f, 1.4f);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = enemy.getTarget();
        if (target != null) {
            enemy.getLookControl().setLookAt(target, 30f, 30f);
        }
        if (mark == null) {
            return;
        }
        if (--windup > 0) {
            telegraph();
            return;
        }
        erupt();
        mark = null;
    }

    /** The ring of particles that makes the patch readable from across a room. */
    private void telegraph() {
        if (!(enemy.level() instanceof ServerLevel level) || windup % 4 != 0) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            level.sendParticles(ParticleTypes.SMALL_FLAME,
                    mark.x + Math.cos(angle) * RADIUS, mark.y + 0.1, mark.z + Math.sin(angle) * RADIUS,
                    1, 0, 0, 0, 0);
        }
    }

    private void erupt() {
        if (!(enemy.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, mark.x, mark.y + 0.5, mark.z, 6,
                RADIUS / 2, 0.2, RADIUS / 2, 0);
        level.playSound(null, mark.x, mark.y, mark.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 0.7f, 1.6f);
        float damage = enemy.variant().rangedDamage();
        AABB area = new AABB(mark.x - RADIUS, mark.y - 1, mark.z - RADIUS,
                mark.x + RADIUS, mark.y + 2, mark.z + RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim != enemy && !(victim instanceof DungeonGeoEnemy)) {
                victim.hurt(enemy.damageSources().indirectMagic(enemy, enemy), damage);
            }
        }
    }

    @Override
    public void stop() {
        mark = null;
        windup = 0;
    }
}
