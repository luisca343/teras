package es.boffmedia.teras.dungeon.ability;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.encounter.EnemySpawner;
import es.boffmedia.teras.dungeon.encounter.SpawnTables;
import es.boffmedia.teras.dungeon.run.DungeonSound;
import es.boffmedia.teras.dungeon.run.RunEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * The behaviour behind {@link AbilityKind}, written against plain {@link LivingEntity} so both
 * enemy paths share it: CustomNPCs clones reach it from {@code CnpcAbilityEvents} on the NPC event
 * bus, and {@code teras:dungeon_enemy} calls it straight from its own hooks. Nothing here imports
 * {@code noppes.npcs}, so it links on a server without CustomNPCs.
 *
 * <p>One-shot abilities record that they fired as a scoreboard tag on the entity rather than in a
 * map here: the entity is the natural owner of its own state, and a static map keyed by UUID would
 * leak every enemy that ever died in a run.</p>
 */
public final class AbilityEngine {
    private AbilityEngine() {}

    private static final String FIRED_PREFIX = "teras_ab_";
    private static final ResourceLocation ENRAGE_SPEED =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_enrage_speed");
    private static final ResourceLocation ENRAGE_DAMAGE =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_enrage_damage");

    /** Whether {@code self} has already crossed an enrage threshold — read by the CNPC damage hook. */
    public static boolean isEnraged(Entity self) {
        return self.getTags().contains(FIRED_PREFIX + AbilityKind.ENRAGE);
    }

    /**
     * Health-threshold abilities. Called every tick for the geo enemy and from the NPC bus's update
     * event for clones; both are cheap because {@link Abilities#of} short-circuits on the tag.
     */
    public static void tick(LivingEntity self) {
        List<AbilityDef> defs = Abilities.of(self);
        if (defs.isEmpty() || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        float healthPct = self.getMaxHealth() <= 0 ? 1f : self.getHealth() / self.getMaxHealth();
        for (AbilityDef def : defs) {
            switch (def.kind()) {
                case ENRAGE -> {
                    if (healthPct <= def.param("healthPct", 0.4) && fireOnce(self, def.kind())) {
                        enrage(level, self, def);
                    }
                }
                case SUMMON -> {
                    if (healthPct <= def.param("healthPct", 0.5) && fireOnce(self, def.kind())) {
                        summon(level, self, def);
                    }
                }
                default -> { }
            }
        }
    }

    /**
     * Side effects of a melee hit that has already landed.
     *
     * <p>{@code damage} is passed in rather than read off {@code ATTACK_DAMAGE} because CustomNPCs
     * computes its hit from {@code INPCMelee.strength} and leaves that attribute at zero — a cleave
     * scaled off the attribute would splash for nothing on exactly the enemies most likely to have
     * it. Enrage's own multiplier is applied on the CNPC event for the same reason, so it is
     * already baked into what arrives here.</p>
     */
    public static void onMeleeHit(LivingEntity attacker, LivingEntity victim, float damage) {
        List<AbilityDef> defs = Abilities.of(attacker);
        if (defs.isEmpty() || !(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        for (AbilityDef def : defs) {
            switch (def.kind()) {
                case CLEAVE -> cleave(level, attacker, victim, damage, def);
                case ON_HIT -> applyEffect(victim, def);
                default -> { }
            }
        }
    }

    /**
     * Incoming damage: reflects the {@code THORNS} share back at a living attacker.
     *
     * <p>Never at another dungeon enemy. Two thorns-carrying enemies that damaged each other would
     * reflect into each other's handler and back again; they have no reason to fight anyway, being
     * one faction, so refusing is free.</p>
     */
    public static void onDamaged(LivingEntity self, Entity source, float amount) {
        if (!(source instanceof LivingEntity attacker) || amount <= 0
                || Abilities.enemyId(attacker) != null) {
            return;
        }
        for (AbilityDef def : Abilities.of(self)) {
            if (def.kind() != AbilityKind.THORNS) {
                continue;
            }
            float reflected = (float) (amount * def.param("fraction", 0.25));
            if (reflected > 0) {
                attacker.hurt(self.damageSources().thorns(self), reflected);
            }
        }
    }

    /** True the first time it is asked for this entity and ability; the tag persists with it. */
    private static boolean fireOnce(Entity self, AbilityKind kind) {
        return self.addTag(FIRED_PREFIX + kind);
    }

    private static void cleave(ServerLevel level, LivingEntity attacker, LivingEntity victim,
                               float damage, AbilityDef def) {
        float splash = damage * (float) def.param("fraction", 0.5);
        if (splash <= 0) {
            return;
        }
        double radius = def.param("radius", 3.0);
        AABB box = victim.getBoundingBox().inflate(radius);
        for (Player bystander : level.getEntitiesOfClass(Player.class, box)) {
            if (bystander == victim || bystander.isSpectator() || !bystander.isAlive()) {
                continue;
            }
            bystander.hurt(attacker.damageSources().mobAttack(attacker), splash);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.5),
                victim.getZ(), 2, radius / 3, 0.1, radius / 3, 0);
    }

    private static void applyEffect(LivingEntity victim, AbilityDef def) {
        Holder<MobEffect> effect = effectHolder(def.arg());
        if (effect == null) {
            return;
        }
        victim.addEffect(new MobEffectInstance(effect,
                def.intParam("duration", 100), def.intParam("amplifier", 0)));
    }

    private static Holder<MobEffect> effectHolder(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Optional<Holder.Reference<MobEffect>> found =
                BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(id));
        if (found.isEmpty()) {
            Teras.LOGGER.warn("Dungeons: unknown mob effect '{}' on an ON_HIT ability", id);
            return null;
        }
        return found.get();
    }

    /**
     * Enrage is telegraphed on purpose — a boss that quietly doubles its damage reads as the fight
     * being broken, not as a phase.
     */
    private static void enrage(ServerLevel level, LivingEntity self, AbilityDef def) {
        addModifier(self, Attributes.MOVEMENT_SPEED, ENRAGE_SPEED, def.param("speedMult", 0.3));
        addModifier(self, Attributes.ATTACK_DAMAGE, ENRAGE_DAMAGE, def.param("damageMult", 0.5));
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, self.getX(), self.getY(1.0), self.getZ(),
                12, 0.4, 0.4, 0.4, 0.0);
        RunEngine.playAbilityCue(self, DungeonSound.ENEMY_ENRAGED);
    }

    private static void addModifier(LivingEntity self,
                                    Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                    ResourceLocation id, double amount) {
        AttributeInstance instance = self.getAttribute(attribute);
        if (instance == null || instance.getModifier(id) != null) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(id, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /**
     * Adds join the fight already in progress, so they have to enter the room's kill ledger or the
     * room clears the moment the summoner dies with its adds still standing.
     */
    private static void summon(ServerLevel level, LivingEntity self, AbilityDef def) {
        SpawnTables.SpawnEntry entry = SpawnTables.parseSpec(def.arg());
        if (entry == null) {
            Teras.LOGGER.warn("Dungeons: SUMMON ability has an unusable spec '{}'", def.arg());
            return;
        }
        // Adds appearing out of a boss that did not visibly do anything reads as a bug rather than
        // as a mechanic. An animated summoner plays its cast clip; a CustomNPCs clone has no rig to
        // drive, so it simply does not get one.
        if (self instanceof es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy geo) {
            geo.triggerAction(es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy.Action.CAST, 28);
        }
        int count = def.intParam("count", 2);
        double spread = def.param("spread", 2.0);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / Math.max(1, count);
            BlockPos pos = BlockPos.containing(
                    self.getX() + Math.cos(angle) * spread,
                    self.getY(),
                    self.getZ() + Math.sin(angle) * spread);
            Entity add = EnemySpawner.spawnSummon(level, entry, pos);
            if (add != null && !RunEngine.registerSummon(self, add)) {
                // Nothing is tracking the summoner's room — a floor that ended mid-ability, or an
                // enemy spawned outside a run. Leaving the add standing would be a stray mob.
                add.discard();
            }
        }
    }
}
