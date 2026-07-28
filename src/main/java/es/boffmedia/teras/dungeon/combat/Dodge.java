package es.boffmedia.teras.dungeon.combat;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.run.DungeonHealth;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La esquiva: the combat verb, and the anchor every dodge boon will attach to.
 *
 * <h2>Why it is not ParCool's roll</h2>
 *
 * <p>See {@link DodgeState}. Short version: the server cannot see a ParCool roll, and a rebuild that
 * owns every window in combat cannot rent out the most important one.</p>
 *
 * <h2>Why the direction comes from the client</h2>
 *
 * <p>A dodge has to go where the player is <i>pressing</i>, and only the client knows that — the
 * server sees a position and a look angle, neither of which distinguishes backing away from
 * advancing. So the two movement impulses ride along in the payload and are <b>normalized and
 * clamped here</b>: a crafted packet can choose a direction, which it could anyway by turning, but
 * it cannot choose a distance.</p>
 */
public final class Dodge {
    private Dodge() {}

    /** Horizontal burst, in blocks per tick. Sprinting is about a third of this. */
    private static final double IMPULSE = 0.82;

    /** Just enough lift to clear a slab lip; a dodge that catches on the floor reads as a bug. */
    private static final double LIFT = 0.06;

    private static final Map<UUID, DodgeState> STATES = new ConcurrentHashMap<>();

    /**
     * Rolls, if the player may.
     *
     * @param forward the client's forward impulse, positive towards where they are looking
     * @param left    the client's left impulse, matching vanilla's {@code Input.leftImpulse}
     * @return false when refused — out of a run, disabled, or still on cooldown
     */
    public static boolean perform(ServerPlayer player, float forward, float left) {
        if (!DungeonsConfig.combatEnabled() || !DungeonHealth.isInRun(player)) {
            return false;
        }
        DodgeState state = STATES.computeIfAbsent(player.getUUID(), id -> new DodgeState());
        double enfriamiento = CombatSheets.of(player).get(Stat.ENFRIAMIENTO);
        if (!state.begin(player.serverLevel().getGameTime(), enfriamiento)) {
            return false;
        }

        Vec3 direction = direction(player, forward, left);
        double impulse = DodgeReach.clamp(IMPULSE, freeAhead(player, direction));
        // Vertical velocity is kept, not cleared. Clamping it to zero and resetting fall distance —
        // which this did — turned the esquiva into a fall negator on a two-second cooldown, and the
        // dungeon prices falls: parkour routes, el plomo, and the chest placed where only a jump
        // reaches all assume a drop costs something. A roll is a horizontal burst; it is not a
        // parachute.
        player.setDeltaMovement(direction.x * impulse,
                player.getDeltaMovement().y + LIFT,
                direction.z * impulse);
        // Without this the server's own movement stays server-side and the client rubber-bands back.
        player.hurtMarked = true;

        tell(player);
        return true;
    }

    /** Whether an incoming hit should miss entirely. */
    public static boolean invulnerable(ServerPlayer player) {
        DodgeState state = STATES.get(player.getUUID());
        return state != null && state.invulnerable(player.serverLevel().getGameTime());
    }

    /**
     * Drops a player's window and cooldown.
     *
     * <p>Owed by a run ending ({@code RunEngine.clearRunEffects}) and by a death
     * ({@code RunEngine.onLivingDeath}) — a cooldown that outlived its fight would be counting down
     * against the next one.</p>
     */
    public static void forget(UUID player) {
        STATES.remove(player);
    }

    /**
     * The direction pressed, in world space.
     *
     * <p>Falls back to straight ahead when nothing is held: a standing player who rolls means
     * forward, and a dodge that refuses to move is indistinguishable from a dodge that did not
     * fire.</p>
     */
    private static Vec3 direction(ServerPlayer player, float forward, float left) {
        float yaw = player.getYRot() * ((float) Math.PI / 180f);
        Vec3 ahead = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        // At yaw 0 a player faces +Z, and their left hand points +X — which is also the direction
        // vanilla's own strafe takes a positive leftImpulse (Entity.getInputVector). Getting this
        // sign wrong is invisible in a unit test and unmistakable in play.
        Vec3 leftward = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 pressed = ahead.scale(clamp(forward)).add(leftward.scale(clamp(left)));
        return pressed.lengthSqr() < 1.0e-4 ? ahead : pressed.normalize();
    }

    /**
     * Blocks of clear space along {@code direction}, or -1 when nothing is in the way.
     *
     * <p>Cast from chest height rather than from the feet: a roll over a step or a slab lip would
     * otherwise read the floor rising in front of it as a wall, and the whole point of {@link #LIFT}
     * is that such a lip is cleared. Only the horizontal component is traced — the dodge barely
     * moves vertically, so a ceiling is not what stops it.</p>
     */
    private static double freeAhead(ServerPlayer player, Vec3 direction) {
        Vec3 from = player.position().add(0, player.getBbHeight() * 0.6, 0);
        Vec3 to = from.add(direction.scale(DodgeReach.travel(IMPULSE)));
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? -1 : hit.getLocation().distanceTo(from);
    }

    private static float clamp(float impulse) {
        return Math.max(-1f, Math.min(1f, impulse));
    }

    /** Rule 3: one tell, and it has to reach the people who did not press the key. */
    private static void tell(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7f, 1.4f);
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.25, 0.05, 0.25, 0.02);
    }
}
