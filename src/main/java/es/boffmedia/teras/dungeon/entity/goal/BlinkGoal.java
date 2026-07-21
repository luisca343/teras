package es.boffmedia.teras.dungeon.entity.goal;

import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A short teleport away when something closes — what stops a caster being a free target.
 *
 * <p>{@code BLINK} was declared in the vocabulary and had no goal at all, so a variant carrying it
 * simply did nothing with it. This is the implementation the enum always promised.</p>
 *
 * <p>It moves the enemy <b>away from</b> whatever crowded it rather than to a random point, so a
 * blink always buys the distance it exists to buy, and it lands only on ground the enemy could have
 * walked to — a blink through a wall would put it outside the room the party is sealed into.</p>
 */
public class BlinkGoal extends Goal {

    /** How close a threat must get before it blinks. */
    private static final double TRIGGER = 3.5;
    private static final int COOLDOWN = 100;
    private static final int ATTEMPTS = 12;
    private static final double MIN_JUMP = 4.0;
    private static final double MAX_JUMP = 7.0;

    private final DungeonGeoEnemy enemy;
    private int cooldown;

    public BlinkGoal(DungeonGeoEnemy enemy) {
        this.enemy = enemy;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = enemy.getTarget();
        if (target == null || !target.isAlive() || --cooldown > 0) {
            return false;
        }
        return enemy.distanceToSqr(target) < TRIGGER * TRIGGER;
    }

    @Override
    public void start() {
        LivingEntity target = enemy.getTarget();
        if (target == null) {
            return;
        }
        cooldown = COOLDOWN;
        // Directly away from the threat, then fanned out — the first candidate is the one that buys
        // the most distance, and the fan is what stops it blinking into the same corner every time.
        double awayX = enemy.getX() - target.getX();
        double awayZ = enemy.getZ() - target.getZ();
        double length = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (length < 1.0E-4) {
            awayX = 1;
            awayZ = 0;
            length = 1;
        }
        double baseAngle = Math.atan2(awayZ / length, awayX / length);
        for (int i = 0; i < ATTEMPTS; i++) {
            double spread = (i % 2 == 0 ? 1 : -1) * (i / 2) * 0.4;
            double angle = baseAngle + spread;
            double distance = MIN_JUMP + enemy.getRandom().nextDouble() * (MAX_JUMP - MIN_JUMP);
            double x = enemy.getX() + Math.cos(angle) * distance;
            double z = enemy.getZ() + Math.sin(angle) * distance;
            if (tryBlink(x, z)) {
                return;
            }
        }
    }

    /** Lands only where the enemy could stand: solid footing, clear body, no drop into the void. */
    private boolean tryBlink(double x, double z) {
        BlockPos ground = groundUnder(x, z);
        if (ground == null) {
            return false;
        }
        double y = ground.getY() + 1;
        if (!enemy.level().noCollision(enemy, enemy.getBoundingBox()
                .move(x - enemy.getX(), y - enemy.getY(), z - enemy.getZ()))) {
            return false;
        }
        puff();
        enemy.teleportTo(x, y, z);
        puff();
        enemy.level().playSound(null, enemy.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.7f, 1.3f);
        enemy.getNavigation().stop();
        return true;
    }

    /**
     * The highest solid block within a couple of blocks of the enemy's own level, or null. Searching
     * a band rather than the whole column is what keeps a blink from dropping it off a ledge or
     * planting it on top of one.
     */
    private BlockPos groundUnder(double x, double z) {
        int startY = (int) Math.floor(enemy.getY());
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos pos = BlockPos.containing(x, startY + dy, z);
            BlockPos below = pos.below();
            if (!enemy.level().getBlockState(below).isAir()
                    && enemy.level().getBlockState(pos).isAir()) {
                return below;
            }
        }
        return null;
    }

    private void puff() {
        if (enemy.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.PORTAL, enemy.getX(), enemy.getY() + 0.8,
                    enemy.getZ(), 18, 0.3, 0.5, 0.3, 0.3);
        }
    }
}
