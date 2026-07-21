package es.boffmedia.teras.dungeon.entity;

import es.boffmedia.teras.dungeon.ability.AbilityEngine;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A dungeon enemy with a GeckoLib model. This is the animated half of the bestiary: CustomNPCs
 * covers enemies that need dialogue, scripts and hand-editing, and this covers enemies that need
 * to be something other than a humanoid.
 *
 * <p>Its {@linkplain GeoEnemyVariant variant} is the whole entity: stats are applied server-side
 * on spawn, and the id rides {@link SynchedEntityData} so the renderer can pick the model, texture
 * and scale on the client. That sync is the reason this is a Teras entity rather than a render
 * swap on someone else's — a CustomNPCs NPC has nowhere to put a model id that we control on both
 * sides, and replacing another mod's renderer breaks its NPCs when we get it wrong.</p>
 */
public class DungeonGeoEnemy extends Monster implements GeoEntity,
        net.minecraft.world.entity.monster.RangedAttackMob {

    private static final EntityDataAccessor<String> VARIANT =
            SynchedEntityData.defineId(DungeonGeoEnemy.class, EntityDataSerializers.STRING);
    /**
     * Ticks left on the current action animation. Synched, and it has to be: the animation
     * controller's predicate runs on the <b>client</b>, while the goals that drive it only ever run
     * on the server. As a plain field the client's copy stayed at zero forever and the attack
     * animation never played for anyone — including the player being hit.
     */
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(DungeonGeoEnemy.class, EntityDataSerializers.INT);
    /** Which action clip {@link #ATTACK_TICKS} is counting down — bite, shoot or pounce. */
    private static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(DungeonGeoEnemy.class, EntityDataSerializers.INT);
    /** Whether a climber is against a wall right now, so the client can play the climb loop. */
    private static final EntityDataAccessor<Boolean> CLIMBING =
            SynchedEntityData.defineId(DungeonGeoEnemy.class, EntityDataSerializers.BOOLEAN);

    /** What the client is being told to animate. Ordinals ride the wire — append only. */
    public enum Action { BITE, SHOOT, POUNCE }

    private static final int ATTACK_ANIMATION_TICKS = 10;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation CLIMB = RawAnimation.begin().thenLoop("climb");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("shoot");
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlay("jump");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DungeonGeoEnemy(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    /** Baseline attributes; {@link #applyVariant} overrides them per variant on spawn. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    /**
     * The goals every enemy has regardless of what it does. The ones that depend on the variant are
     * added by {@link #rebuildGoals()} instead, because this runs in the {@code Mob} constructor —
     * before {@code applyVariant} has supplied a behaviour list, and before the synched data has
     * been read back on a reload.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        rebuildGoals();
    }

    /**
     * Composes the variant's behaviours into goals, replacing whatever was there.
     *
     * <p>Called whenever the variant can have changed — on {@code applyVariant} and after NBT is
     * read — because goal registration happens in the constructor, long before an entity knows what
     * it is. Without this a spider loaded from disk would come back as the fallback's plain melee.
     * </p>
     */
    private void rebuildGoals() {
        goalSelector.removeAllGoals(goal -> BEHAVIOUR_GOALS.contains(goal.getClass()));
        GeoEnemyVariant variant = variant();
        // The queen's ceiling web owns the highest priority: it is a set-piece on a long cooldown,
        // and while it runs she should not also be trying to walk up and bite.
        if (variant.has(Behaviour.CEILING_WEB)) {
            goalSelector.addGoal(1, new es.boffmedia.teras.dungeon.entity.goal
                    .SpiderCeilingWebGoal(this));
        }
        if (variant.shoots()) {
            // A ground shooter that walks into melee range stops being a shooter, so this sits above
            // melee and RangedAttackGoal keeps its own distance. The queen is deliberately not here:
            // CEILING_WEB is not isRanged, so she closes and bites instead of parking at range.
            goalSelector.addGoal(2, new RangedAttackGoal(this,
                    1.0, Math.max(20, variant.rangedCooldown()), 16f));
        }
        if (variant.has(Behaviour.LEAP)) {
            goalSelector.addGoal(3, new es.boffmedia.teras.dungeon.entity.goal.SpiderPounceGoal(this));
        }
        if (variant.has(Behaviour.MELEE) || variant.behaviours().isEmpty()) {
            // An empty list still melees: an enemy that does nothing at all is never what was meant.
            goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
        }
        // Navigation is built in the Mob constructor too, so a spider read back from NBT would
        // path along the floor until this is redone.
        this.navigation = createNavigation(level());
    }

    /** Goal classes {@link #rebuildGoals()} owns, so it never strips the constant ones. */
    private static final java.util.Set<Class<?>> BEHAVIOUR_GOALS = java.util.Set.of(
            MeleeAttackGoal.class, RangedAttackGoal.class,
            es.boffmedia.teras.dungeon.entity.goal.SpiderPounceGoal.class,
            es.boffmedia.teras.dungeon.entity.goal.SpiderCeilingWebGoal.class);

    /**
     * Wall-climbing for the spiders. Chosen here rather than as a goal because navigation is the
     * ground rules underneath goals, not a decision the enemy makes.
     */
    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return variant().movement() == Movement.CLIMBER
                ? new net.minecraft.world.entity.ai.navigation.WallClimberNavigation(this, level)
                : super.createNavigation(level);
    }

    @Override
    public void performRangedAttack(net.minecraft.world.entity.LivingEntity target, float power) {
        GeoEnemyVariant variant = variant();
        DungeonBolt.Kind kind = variant.has(Behaviour.WEB_SHOT)
                ? DungeonBolt.Kind.WEB : DungeonBolt.Kind.BOLT;
        DungeonBolt.shoot(this, target, kind, variant.rangedDamage(), 1.2f);
        triggerAction(Action.SHOOT, ATTACK_ANIMATION_TICKS);
    }

    /** Tells the client which clip to play, for the ticks given. Server-side; the fields are synched. */
    public void triggerAction(Action action, int ticks) {
        entityData.set(ACTION, action.ordinal());
        entityData.set(ATTACK_TICKS, ticks);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, GeoEnemyVariant.FALLBACK);
        builder.define(ATTACK_TICKS, 0);
        builder.define(ACTION, Action.BITE.ordinal());
        builder.define(CLIMBING, false);
    }

    public String variantId() {
        return entityData.get(VARIANT);
    }

    public GeoEnemyVariant variant() {
        return GeoEnemyVariant.of(variantId());
    }

    /** Sets the variant and rewrites this entity's attributes from it. Server-side, on spawn. */
    public void applyVariant(String id) {
        GeoEnemyVariant variant = GeoEnemyVariant.of(id);
        entityData.set(VARIANT, variant.id());
        rebuildGoals();
        setAttribute(Attributes.MAX_HEALTH, variant.health());
        setAttribute(Attributes.ATTACK_DAMAGE, variant.damage());
        setAttribute(Attributes.MOVEMENT_SPEED, variant.speed());
        setAttribute(Attributes.ARMOR, variant.armor());
        setAttribute(Attributes.FOLLOW_RANGE, variant.followRange());
        setHealth(getMaxHealth());
    }

    private void setAttribute(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                              double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TerasVariant", variantId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TerasVariant")) {
            applyVariant(tag.getString("TerasVariant"));
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        triggerAction(Action.BITE, ATTACK_ANIMATION_TICKS);
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity victim) {
            AbilityEngine.onMeleeHit(this, victim,
                    (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        return hit;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            AbilityEngine.onDamaged(this, source.getEntity(), amount);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        int ticks = entityData.get(ATTACK_TICKS);
        if (ticks > 0) {
            entityData.set(ATTACK_TICKS, ticks - 1);
        }
        if (!level().isClientSide) {
            // A climber pressed against a wall is climbing it; the flag drives the climb loop and,
            // synched, reaches the client that draws it. Cheap enough to test every tick.
            boolean climbing = variant().movement() == Movement.CLIMBER && horizontalCollision;
            if (entityData.get(CLIMBING) != climbing) {
                entityData.set(CLIMBING, climbing);
            }
            // The same ability set CustomNPCs enemies run, so a server without that mod still
            // fights something with phases rather than a health bar that walks at you.
            AbilityEngine.tick(this);
        }
    }

    /** Larger variants get a proportionally larger hitbox, so what you see is what you hit. */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDefaultDimensions(pose).scale(variant().scale());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (entityData.get(ATTACK_TICKS) > 0) {
                // The action decides the clip. SHOOT and JUMP exist on both rigs (guardian's are
                // placeholders it never triggers), so a variant only ever plays a clip it has.
                return switch (actionOrdinal()) {
                    case 1 -> state.setAndContinue(SHOOT);   // Action.SHOOT
                    case 2 -> state.setAndContinue(JUMP);    // Action.POUNCE
                    default -> state.setAndContinue(ATTACK); // Action.BITE
                };
            }
            if (entityData.get(CLIMBING)) {
                return state.setAndContinue(CLIMB);
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
    }

    private int actionOrdinal() {
        int a = entityData.get(ACTION);
        return a >= 0 && a < Action.values().length ? a : Action.BITE.ordinal();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }
}
