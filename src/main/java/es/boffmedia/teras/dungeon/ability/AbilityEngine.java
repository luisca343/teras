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
    private static final ResourceLocation FLANK_SPEED =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_flank_speed");
    private static final ResourceLocation FLANK_DAMAGE =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_flank_damage");

    /**
     * Whether {@code self} has already crossed an enrage threshold — read by the CNPC damage hook.
     *
     * <p>A prefix scan, not an exact match: the fired-tag carries the threshold it fired at, and an
     * enemy may enrage at more than one. Any of them means enraged.</p>
     */
    public static boolean isEnraged(Entity self) {
        String prefix = FIRED_PREFIX + AbilityKind.ENRAGE;
        for (String tag : self.getTags()) {
            if (tag.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
                    double at = def.param("healthPct", 0.4);
                    if (healthPct <= at && fireOnce(self, def.kind(), at)) {
                        enrage(level, self, def);
                    }
                }
                case SUMMON -> {
                    double at = def.param("healthPct", 0.5);
                    if (healthPct <= at && fireOnce(self, def.kind(), at)) {
                        summon(level, self, def);
                    }
                }
                case ALERTA -> {
                    // The countdown lives on the entity because it is the entity's state, and only
                    // the animated enemy has anywhere to keep it. A clone declaring ALERTA does
                    // nothing rather than silently doing it wrong.
                    if (self instanceof es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy geo
                            && geo.tickAlert(def.intParam("ticks", 120))) {
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
            switch (def.kind()) {
                case THORNS -> {
                    float reflected = (float) (amount * def.param("fraction", 0.25));
                    if (reflected > 0) {
                        attacker.hurt(self.damageSources().thorns(self), reflected);
                    }
                }
                case FLANK_RAGE -> {
                    if (self.level() instanceof ServerLevel level
                            && Flank.isRear(self.getX(), self.getZ(), self.yBodyRot,
                                    attacker.getX(), attacker.getZ(), def.param("rearArc", 120.0))
                            && fireOnce(self, AbilityKind.FLANK_RAGE, 0)) {
                        flankRage(level, self, def);
                    }
                }
                default -> { }
            }
        }
    }

    /** The flank punished: the same telegraphed speed and damage as {@link #enrage}, its own tuning. */
    private static void flankRage(ServerLevel level, LivingEntity self, AbilityDef def) {
        addModifier(self, Attributes.MOVEMENT_SPEED, FLANK_SPEED, def.param("speedMult", 0.4));
        addModifier(self, Attributes.ATTACK_DAMAGE, FLANK_DAMAGE, def.param("damageMult", 0.3));
        onEnrageFeedback(level, self);
    }

    /**
     * The shared onset: a burst of anger, a lower, louder roar, and — for an animated enemy — the
     * synched flag that carries the tint, the hurried clips and the aura, plus a rear-up gesture.
     */
    private static void onEnrageFeedback(ServerLevel level, LivingEntity self) {
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, self.getX(), self.getY(1.0), self.getZ(),
                16, 0.4, 0.5, 0.4, 0.0);
        RunEngine.playAbilityCue(self, DungeonSound.ENEMY_ENRAGED, 1.6f, 0.8f);
        if (self instanceof es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy geo) {
            geo.markEnraged();
        }
    }

    /**
     * What happens after it dies.
     *
     * <p>Called from the animated enemy's own death hook. There is no equivalent on the CustomNPCs
     * path yet, so {@link AbilityKind#ESTALLIDO} and {@link AbilityKind#TESORO} are geo-only — which
     * is stated here rather than left to be discovered, because "the clone declared it and nothing
     * happened" is the exact failure this codebase keeps producing.</p>
     */
    public static void onDeath(LivingEntity self) {
        List<AbilityDef> defs = Abilities.of(self);
        if (defs.isEmpty() || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        for (AbilityDef def : defs) {
            switch (def.kind()) {
                case ESTALLIDO -> burst(level, self, def);
                case TESORO -> hoard(level, self, def);
                default -> { }
            }
        }
    }

    /**
     * The death cloud: everything living inside {@code radio} takes {@code magnitud} damage and
     * catches the {@code arg} effect.
     *
     * <p>Enemies are exempt, for the same reason THORNS exempts them — one faction, and a spore
     * cloud that thinned the wave would make killing the sac a tactic rather than a mistake.</p>
     */
    private static void burst(ServerLevel level, LivingEntity self, AbilityDef def) {
        double radius = def.param("radius", 3.0);
        float damage = (float) def.param("magnitud", 3.0);
        level.sendParticles(ParticleTypes.SNEEZE, self.getX(), self.getY() + 0.4, self.getZ(),
                40, radius / 2, 0.3, radius / 2, 0.02);
        level.playSound(null, self.blockPosition(),
                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                net.minecraft.sounds.SoundSource.HOSTILE, 0.7f, 0.6f);
        AABB box = self.getBoundingBox().inflate(radius);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (victim == self || Abilities.enemyId(victim) != null || !victim.isAlive()) {
                continue;
            }
            if (damage > 0) {
                victim.hurt(level.damageSources().magic(), damage);
            }
            applyEffect(victim, def);
        }
    }

    /** Extra coins into the room, for an enemy that is worth killing rather than dangerous. */
    private static void hoard(ServerLevel level, LivingEntity self, AbilityDef def) {
        int coins = def.intParam("magnitud", 5);
        if (coins > 0) {
            es.boffmedia.teras.dungeon.run.CoinDrops.spawnCoins(level, self.position(), coins);
        }
    }

    /**
     * True the first time it is asked for this entity, ability <b>and threshold</b>; the tag
     * persists with the entity.
     *
     * <p>The threshold is part of the key because an enemy may carry the same ability at two
     * depths, and until it was, only the first ever fired: a slime declaring a split at 66% and
     * another at 33% split once and then stopped, with both lines present in the config and nothing
     * anywhere reporting that the second was dead. Keying on the kind alone is what made "declare it
     * twice" look like a thing you could do.</p>
     *
     * <p>{@link #isEnraged} scans for the kind prefix rather than matching a whole tag, so it still
     * answers for an enrage whatever threshold it fired at.</p>
     */
    private static boolean fireOnce(Entity self, AbilityKind kind, double threshold) {
        return self.addTag(FIRED_PREFIX + kind + "@" + Math.round(threshold * 100));
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
        onEnrageFeedback(level, self);
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
