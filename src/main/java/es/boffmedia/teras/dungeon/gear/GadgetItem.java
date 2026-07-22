package es.boffmedia.teras.dungeon.gear;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The one item every gadget is built on, in the same one-item-per-kind shape as the rest of gear:
 * which gadget a stack <i>is</i> stays the {@code teras:gear_id} component, so a new one is an entry
 * in the catalog rather than a registered item plus assets plus a code change.
 *
 * <p>What is <b>not</b> config-definable is the effect itself — same truth as {@code mecanica} and
 * {@code AbilityKind}: the four cases below are the vocabulary, and config composes and retunes from
 * it. A genuinely new gadget is a constant in {@link GearAbility} plus a case here.</p>
 *
 * <p>Every effect is written without a projectile entity. The two aimed ones raycast from the
 * player's eyes, which costs nothing, cannot be lost in a chunk boundary, and means a gadget is one
 * method rather than an entity type with a renderer and a network id. The cost is that a flask
 * arrives instantly instead of arcing — a real trade, and the right one until a gadget wants to be
 * dodged.</p>
 */
public class GadgetItem extends Item {

    public GadgetItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        GearDef def = GearHolder.defOf(stack);
        AbilityDef active = def == null ? null : firstActive(def);
        if (active == null) {
            // A gadget with no active ability is a right-click that does nothing. Fail here rather
            // than swinging the arm, so the piece reads as broken instead of as inert.
            return InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer user)) {
            // Swing on the client so the use reads as instant; the effect is decided on the server.
            return InteractionResultHolder.success(stack);
        }
        fire(serverLevel, user, active);
        int seconds = active.intParam(GearAbility.P_COOLDOWN,
                active.ability().defaultCooldownSeconds());
        // Per item, not per stack: two flares are two flares, and sharing the cooldown is what keeps
        // "carry six of them" from being the answer to every gadget.
        player.getCooldowns().addCooldown(this, Math.max(20, seconds * 20));
        return InteractionResultHolder.consume(stack);
    }

    private static AbilityDef firstActive(GearDef def) {
        for (AbilityDef ability : def.abilities()) {
            if (ability.ability().isActive()) {
                return ability;
            }
        }
        return null;
    }

    private static void fire(ServerLevel level, ServerPlayer user, AbilityDef ability) {
        GearAbility effect = ability.ability();
        double radius = ability.doubleParam(GearAbility.P_RADIUS, effect.defaultRadius());
        double magnitude = ability.magnitude(effect.defaultMagnitude());
        switch (effect) {
            case BENGALA -> flare(level, user, radius, (int) (magnitude * 20));
            case PETARDO -> charge(level, user, radius, (float) magnitude);
            case FRASCO -> flask(level, user, radius, (int) (magnitude * 20));
            case GARFIO -> hook(level, user, radius, magnitude);
            default -> { }
        }
    }

    /** Outlines everything hostile nearby, through walls, for a while. */
    private static void flare(ServerLevel level, ServerPlayer user, double radius, int ticks) {
        level.sendParticles(ParticleTypes.FLAME, user.getX(), user.getY() + 1.2, user.getZ(),
                60, 0.4, 0.6, 0.4, 0.08);
        level.playSound(null, user.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS, 0.9f, 1.6f);
        for (Monster monster : level.getEntitiesOfClass(Monster.class,
                user.getBoundingBox().inflate(radius), LivingEntity::isAlive)) {
            monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, false));
        }
    }

    /** A charge at your feet: a little damage, and a lot of shove. */
    private static void charge(ServerLevel level, ServerPlayer user, double radius, float damage) {
        level.sendParticles(ParticleTypes.EXPLOSION, user.getX(), user.getY() + 0.5, user.getZ(),
                6, 0.6, 0.3, 0.6, 0);
        level.playSound(null, user.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.8f, 1.5f);
        for (Monster monster : level.getEntitiesOfClass(Monster.class,
                user.getBoundingBox().inflate(radius), LivingEntity::isAlive)) {
            monster.hurt(level.damageSources().explosion(user, user), damage);
            // Away from the user rather than along their look: a charge goes off where it is put.
            monster.knockback(1.4, user.getX() - monster.getX(), user.getZ() - monster.getZ());
        }
    }

    /** Slows whatever is standing where your aim lands. */
    private static void flask(ServerLevel level, ServerPlayer user, double radius, int ticks) {
        Vec3 impact = aim(level, user, 14);
        level.sendParticles(ParticleTypes.ITEM_SLIME, impact.x, impact.y + 0.2, impact.z,
                40, radius / 3, 0.2, radius / 3, 0.01);
        level.playSound(null, net.minecraft.core.BlockPos.containing(impact),
                SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.8f);
        for (Monster monster : level.getEntitiesOfClass(Monster.class,
                new net.minecraft.world.phys.AABB(impact, impact).inflate(radius),
                LivingEntity::isAlive)) {
            monster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
        }
    }

    /** Pulls the user toward the first solid block along their aim. */
    private static void hook(ServerLevel level, ServerPlayer user, double range, double strength) {
        Vec3 anchor = aim(level, user, range);
        Vec3 pull = anchor.subtract(user.position());
        if (pull.lengthSqr() < 4) {
            return;
        }
        // Normalised and re-scaled rather than used raw: a hook that pulls harder the further the
        // wall is turns a long shot into a launch across the room.
        Vec3 impulse = pull.normalize().scale(strength).add(0, 0.35, 0);
        user.setDeltaMovement(impulse);
        user.hurtMarked = true;   // tells the server to send the velocity; without it nothing moves
        user.resetFallDistance();
        level.playSound(null, user.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(),
                SoundSource.PLAYERS, 0.9f, 1.2f);
    }

    /** Where the user is looking, stopped at the first block within {@code range}. */
    private static Vec3 aim(ServerLevel level, ServerPlayer user, double range) {
        Vec3 eyes = user.getEyePosition();
        Vec3 end = eyes.add(user.getLookAngle().scale(range));
        HitResult hit = level.clip(new ClipContext(eyes, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }
}
