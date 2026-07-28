package es.boffmedia.teras.dungeon.combat;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where vanilla's damage number is thrown away and ours is used instead (ROGUELIKE §4.2).
 *
 * <h2>How much of a hit this owns depends on what kind of hit it is</h2>
 *
 * <p>{@link HitClass} makes that call, and it is the most important decision in the package. A
 * <b>swing</b> is fully ours: the amount is replaced, the attacker's verb decides what it was worth,
 * and poise is chipped. Anything else an attacker <b>caused</b> — a bolt, a volley, an explosion, a
 * reflected hit, an authored proc — keeps the amount its author wrote and only has the mitigation
 * curve applied to it. And a hit with <b>nobody behind it</b> is left completely alone.</p>
 *
 * <p>That last exemption is load-bearing rather than incidental: the dungeon deals a great deal of
 * deliberate damage that is not combat at all. The sacrifice plate's bite, the curse room's toll, a
 * chest's spikes and fall damage are all <i>prices</i>, tuned in coins-and-hearts terms against the
 * health lockdown, and every one of them goes through {@code RunEngine.chargeToll} with a source that
 * names no entity. Running a price through an armour curve would mean a well-equipped party paid less
 * for the same trade, which inverts the entire point of a cost.</p>
 *
 * <h2>The flag</h2>
 *
 * <p>{@code combate.activado} ships <b>true</b>: this is what the dungeon's combat is, and a fresh
 * config that shipped it off meant every new install was missing the system it was installed for. The
 * flag stays so a live server can hand combat back to Minecraft in one line.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class CombatEngine {
    private CombatEngine() {}

    /**
     * Poise, per entity, for as long as it is not at rest.
     *
     * <p>Entries are created on the first chip and dropped again the moment an entity is whole and
     * unstaggered, so this holds combatants in a fight rather than every mob on the server. Keyed by
     * UUID rather than by reference so a dead or unloaded entity cannot be kept alive by it.</p>
     */
    private static final Map<UUID, Aplomo> POISE = new ConcurrentHashMap<>();

    /** Where each player is in their light chain, and whether their heavy is armed. */
    private static final Map<UUID, SwingState> SWINGS = new ConcurrentHashMap<>();

    /** How many of the break's particles to throw. */
    private static final int STAGGER_PARTICLES = 12;

    /**
     * Recomputes a hit.
     *
     * <p>{@link EventPriority#LOW} so that anything cancelling a hit outright — invulnerability,
     * a shipped guard — has already spoken; we are replacing a number, not deciding whether there is
     * one.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!DungeonsConfig.combatEnabled()) {
            return;
        }
        LivingEntity defender = event.getEntity();
        if (!(defender.level() instanceof ServerLevel level) || !inDungeon(level)) {
            return;
        }

        DamageSource source = event.getSource();
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        HitClass kind = HitClass.of(attacker != null,
                attacker != null && source.getDirectEntity() == attacker, melee(source));

        // The dodge answers before the numbers, and only for a hit somebody is landing on you: a roll
        // that only reduced damage would be a worse shield, and i-frames are what the verb is for.
        // Deliberately NOT above this line — an esquiva that also cancelled the curse room's toll and
        // the sacrifice plate's bite would make every price in the dungeon free for seven ticks out of
        // forty, which is the one thing the exemption above exists to prevent.
        if (kind.mitigated() && defender instanceof ServerPlayer player && Dodge.invulnerable(player)) {
            event.setCanceled(true);
            return;
        }
        if (!kind.mitigated()) {
            return;
        }

        int depth = depthOf(defender, attacker);
        StatBlock attackerSheet = CombatSheets.of(attacker);
        StatBlock defenderSheet = CombatSheets.of(defender);

        double damage;
        if (kind.replacesAmount()) {
            DamageMath.Hit hit = DamageMath.resolve(attackerSheet, defenderSheet, depth,
                    level.getRandom().nextDouble());
            damage = hit.damage() * verbScale(level, attacker, defender);
            if (hit.crit()) {
                // Rule 3: an effect nobody can see fire is an effect nobody learns.
                level.playSound(null, defender.getX(), defender.getY(), defender.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        } else {
            // The author's number, through the curve. Scaled rather than replaced so a bolt tuned to
            // hurt less than a swing goes on doing that, while armour and penetration still decide how
            // much of it lands.
            damage = event.getAmount() * (1 - DamageMath.mitigation(
                    defenderSheet.get(Stat.ARMADURA), attackerSheet.get(Stat.PENETRACION), depth));
        }
        event.setAmount((float) Math.max(0, damage));
    }

    /**
     * What the attacker's verb makes this swing worth, chipping the defender's guard on the way.
     *
     * <p>Only a swing gets here. A player's verb is their place in the light chain; an enemy swings
     * plainly, and its variety comes from its own abilities rather than from a chain it drives — but it
     * still chips, so a guard is something both sides have. That symmetry is the point of
     * {@link Stat#APLOMO} existing for players at all.</p>
     */
    private static double verbScale(ServerLevel level, LivingEntity attacker, LivingEntity defender) {
        double scale = 1;
        double chip = Aplomo.ENEMY_CHIP;
        if (attacker instanceof ServerPlayer player) {
            SwingState swing = SWINGS.computeIfAbsent(player.getUUID(), id -> new SwingState());
            int step = swing.light(level.getGameTime());
            scale = SwingState.damageMultiplier(step);
            chip = SwingState.poiseChip(step);
        }
        // What an opening is for. Read before the chip below, which may close it.
        if (staggered(defender)) {
            scale *= SwingState.STAGGER_DAMAGE;
        }
        chipPoise(level, defender, chip);
        return scale;
    }

    /**
     * Whether this source is a body landing on a body.
     *
     * <p>The type is the load-bearing test and the direct-entity check alone is not enough:
     * {@code thorns} and {@code explosion} both name the attacker as the direct entity as well, and
     * neither is a swing. {@code MOB_ATTACK}, {@code MOB_ATTACK_NO_AGGRO} and {@code PLAYER_ATTACK} are
     * the three vanilla types that mean one.</p>
     */
    private static boolean melee(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    /** Breaks a guard when enough lands inside the window, and says so when it goes. */
    private static void chipPoise(ServerLevel level, LivingEntity defender, double chip) {
        if (chip <= 0) {
            return;
        }
        Aplomo aplomo = POISE.computeIfAbsent(defender.getUUID(),
                id -> new Aplomo(CombatSheets.aplomoFor(defender)));
        if (!aplomo.chip(chip)) {
            return;
        }
        // A low thud, not an inventory event. This was item.shield.break, which a playtest heard —
        // correctly — as their own gear being destroyed, several times a fight.
        level.playSound(null, defender.getX(), defender.getY(), defender.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.sendParticles(ParticleTypes.POOF,
                defender.getX(), defender.getEyeY(), defender.getZ(),
                STAGGER_PARTICLES, 0.3, 0.2, 0.3, 0.02);
    }

    /** Whether {@code entity}'s guard is currently broken — the opening a heavy attack buys. */
    public static boolean staggered(LivingEntity entity) {
        Aplomo aplomo = POISE.get(entity.getUUID());
        return aplomo != null && aplomo.staggered();
    }

    /**
     * Ticks the guards that are not at rest, and forgets the ones that are.
     *
     * <p>Self-pruning on purpose: an entity at full poise and unstaggered is indistinguishable from
     * one that has never been hit, so remembering it is pure cost. This is what keeps the map the
     * size of a fight rather than the size of the server.</p>
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (POISE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Aplomo>> entries = POISE.entrySet().iterator();
        while (entries.hasNext()) {
            Aplomo aplomo = entries.next().getValue();
            aplomo.tick();
            if (!aplomo.staggered() && aplomo.current() >= aplomo.max()) {
                entries.remove();
            }
        }
    }

    /** Drops everything a run was holding; called when a run ends so nothing outlives it. */
    public static void forget(UUID entity) {
        POISE.remove(entity);
        SWINGS.remove(entity);
    }

    private static boolean inDungeon(ServerLevel level) {
        return level.dimension().location().toString().equals(DungeonsConfig.dimension());
    }

    /**
     * The floor this fight is happening on, which the mitigation curve needs.
     *
     * <p>Read off whichever party to the fight is in a run — usually the defender, but an enemy being
     * hit knows nothing about the run it belongs to, so the attacker answers for it.</p>
     */
    private static int depthOf(Entity defender, Entity attacker) {
        int depth = stageOf(defender);
        return depth > 0 ? depth : Math.max(1, stageOf(attacker));
    }

    private static int stageOf(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return 0;
        }
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        return run == null ? 0 : run.stage();
    }
}
