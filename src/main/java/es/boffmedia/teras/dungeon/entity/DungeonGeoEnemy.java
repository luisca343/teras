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
public class DungeonGeoEnemy extends Monster implements GeoEntity {

    private static final EntityDataAccessor<String> VARIANT =
            SynchedEntityData.defineId(DungeonGeoEnemy.class, EntityDataSerializers.STRING);
    /**
     * Ticks left on the attack animation. Synched, and it has to be: the animation controller's
     * predicate runs on the <b>client</b>, while {@link #doHurtTarget} only ever runs on the
     * server. As a plain field the client's copy stayed at zero forever and the attack animation
     * never played for anyone — including the player being hit.
     */
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(DungeonGeoEnemy.class, EntityDataSerializers.INT);

    private static final int ATTACK_ANIMATION_TICKS = 10;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

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

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, GeoEnemyVariant.FALLBACK);
        builder.define(ATTACK_TICKS, 0);
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
        entityData.set(ATTACK_TICKS, ATTACK_ANIMATION_TICKS);
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
                return state.setAndContinue(ATTACK);
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
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
