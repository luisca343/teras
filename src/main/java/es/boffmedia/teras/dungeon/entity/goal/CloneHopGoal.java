package es.boffmedia.teras.dungeon.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Makes a CustomNPCs clone bounce instead of walk — what a slime clone needs to read as a slime.
 *
 * <p>A clone renders as whatever entity its model names, but it <b>moves</b> as an NPC: ordinary
 * ground navigation, so a slime slides across the floor upright. The rendered entity is a dummy
 * that is never ticked, so it does not squash or animate either — the hop has to come from the real
 * entity's motion, which is the only part a player actually sees move.</p>
 *
 * <p>The goal claims {@link Flag#JUMP} alone, deliberately. Claiming {@code MOVE} would suppress
 * CustomNPCs' own attack goal — which is what closes distance <i>and</i> what deals its melee
 * damage — leaving a slime that bounces prettily and can never hurt anyone. Sharing the floor with
 * that goal means the clone walks and hops at once, which is what a slime approaching looks like.
 * {@code JUMP} is the same flag CustomNPCs' own pounce AI uses, so the two cannot fight over a
 * clone that has both.</p>
 */
public class CloneHopGoal extends Goal {

    /** Upward impulse. Enough to clear a slab and to read as a hop from across a room. */
    private static final double HOP_UP = 0.42;
    /** Forward impulse toward the target, applied with the hop rather than between hops. */
    private static final double HOP_FORWARD = 0.28;
    private static final int INTERVAL = 14;

    private final Mob mob;
    private int cooldown;

    public CloneHopGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        // Only from the ground: adding lift mid-air would turn a hop into flight.
        if (--cooldown > 0 || !mob.onGround()) {
            return;
        }
        cooldown = INTERVAL;
        Vec3 toward = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
        double distance = toward.length();
        Vec3 push = distance < 0.01 ? Vec3.ZERO : toward.scale(HOP_FORWARD / distance);
        // Added to the current motion rather than replacing it, so the walk the NPC's own goal is
        // driving carries through the hop instead of being cancelled by it every fourteen ticks.
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(motion.x + push.x, HOP_UP, motion.z + push.z);
        mob.hasImpulse = true;
    }
}
