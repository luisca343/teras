package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.run.CoinDrops;
import es.boffmedia.teras.dungeon.run.DungeonHealth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where gear abilities actually fire. Stat lines need nothing here — they ride the item's attribute
 * modifiers ({@link GearStamp}) exactly as vanilla equipment does; only the hooks live in events.
 *
 * <p>Abilities work wherever the gear is carried, not only inside a dungeon. Gear is kept after a
 * run, and a sword that stops biting the moment you walk out is a reward players stop chasing. The
 * two exceptions are structural rather than chosen: {@link GearAbility#BOTIN} pays into a party
 * purse and {@link GearAbility#FENIX_MENOR} writes run state, so both simply have nothing to do
 * outside a run.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class GearEvents {
    private GearEvents() {}

    /** Cheap enough to run often, but no reason to: the charm is a once-per-floor grant. */
    private static final int PHOENIX_CHECK_TICKS = 40;

    /** Last floor each player was granted the charm on, keyed {@code runId:floorsCleared}. */
    private static final Map<UUID, String> PHOENIX_GRANTED = new HashMap<>();

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            onHit(attacker, victim, event.getAmount());
        }
        if (victim instanceof ServerPlayer defender) {
            onHurt(defender, event.getSource(), event.getAmount());
            onFall(defender, event);
        }
    }

    /**
     * Softens a landing for whoever is wearing something that says it does.
     *
     * <p>Reduces rather than cancels, so a piece can be worth half a fall instead of all of it, and
     * so the default of 1.0 is a statement in the catalog rather than a special case in here.</p>
     */
    private static void onFall(ServerPlayer defender, LivingIncomingDamageEvent event) {
        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return;
        }
        for (ItemStack stack : bearing(defender, defender.getArmorSlots())) {
            GearDef def = GearHolder.defOf(stack);
            AbilityDef soft = def == null ? null : def.ability(GearAbility.CAIDA_SUAVE);
            if (soft == null) {
                continue;
            }
            double share = Math.clamp(
                    soft.magnitude(GearAbility.CAIDA_SUAVE.defaultMagnitude()), 0.0, 1.0);
            event.setAmount((float) (event.getAmount() * (1 - share)));
            if (event.getAmount() <= 0) {
                event.setCanceled(true);
            }
            return;
        }
    }

    /**
     * Main-hand, offhand and curio slots. Charms used to be held, which is why the hands were
     * enough; once they moved to a Curios slot the offhand stops holding them, and scanning only
     * the hands would leave every charm equipping, tooltipping and doing nothing.
     */
    private static void onHit(ServerPlayer attacker, LivingEntity victim, float amount) {
        for (ItemStack stack : bearing(attacker, attacker.getMainHandItem(),
                attacker.getOffhandItem())) {
            GearDef def = GearHolder.defOf(stack);
            if (def == null) {
                continue;
            }
            // Every ability the piece carries, not just its first: a sword that both burns and
            // steals life is expressible now, and was not before.
            for (AbilityDef a : def.abilities()) {
                switch (a.ability()) {
                    case VAMPIRISMO -> heal(attacker,
                            (float) (amount * a.magnitude(GearAbility.VAMPIRISMO.defaultMagnitude())));
                    case QUEMAZON -> victim.igniteForSeconds(
                            (float) a.magnitude(GearAbility.QUEMAZON.defaultMagnitude()));
                    case DESGARRO -> victim.addEffect(new MobEffectInstance(MobEffects.WITHER,
                            (int) (a.magnitude(GearAbility.DESGARRO.defaultMagnitude()) * 20),
                            // A level, which used to be the hard-coded 1 — the second number the
                            // old one-magnitude shape had nowhere to put.
                            Math.max(0, a.intParam(GearAbility.P_LEVEL, 1) - 1)));
                    case VISCOSO -> victim.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            (int) (a.magnitude(GearAbility.VISCOSO.defaultMagnitude()) * 20),
                            Math.max(0, a.intParam(GearAbility.P_LEVEL, 1) - 1)));
                    // Away from the attacker, worked out from the horizontal offset rather than
                    // from look direction: a shove should go where the two of you actually stand,
                    // not where the camera happens to point.
                    case EMPUJE -> victim.knockback(
                            a.magnitude(GearAbility.EMPUJE.defaultMagnitude()),
                            attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ());
                    default -> { }
                }
            }
        }
    }

    private static void onHurt(ServerPlayer defender, DamageSource source, float amount) {
        // Melee only: on a projectile the direct entity is the arrow, and thorns should not
        // reach back down the arc to whoever loosed it.
        if (!(source.getEntity() instanceof LivingEntity attacker)
                || source.getDirectEntity() != attacker) {
            return;
        }
        for (ItemStack stack : bearing(defender, defender.getArmorSlots())) {
            GearDef def = GearHolder.defOf(stack);
            AbilityDef thorns = def == null ? null : def.ability(GearAbility.ESPINAS);
            if (thorns != null) {
                attacker.hurt(defender.damageSources().thorns(defender),
                        (float) (amount * thorns.magnitude(GearAbility.ESPINAS.defaultMagnitude())));
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)
                || event.getEntity() instanceof ServerPlayer) {
            return;
        }
        LivingEntity victim = event.getEntity();
        int bonus = 0;
        boolean shockwave = false;
        double shockwaveMagnitude = 0;
        double shockwaveRadius = DEFAULT_SHOCKWAVE_RADIUS;

        for (ItemStack stack : bearing(killer, held(killer))) {
            GearDef def = GearHolder.defOf(stack);
            if (def == null) {
                continue;
            }
            bonus += loot(def);
            AbilityDef wave = def.ability(GearAbility.ONDA);
            if (wave != null) {
                shockwave = true;
                shockwaveMagnitude = Math.max(shockwaveMagnitude,
                        wave.magnitude(GearAbility.ONDA.defaultMagnitude()));
                shockwaveRadius = Math.max(shockwaveRadius,
                        wave.doubleParam(GearAbility.P_RADIUS, DEFAULT_SHOCKWAVE_RADIUS));
            }
        }
        for (ItemStack stack : killer.getArmorSlots()) {
            bonus += loot(GearHolder.defOf(stack));
        }

        if (shockwave) {
            shockwave(killer, victim, shockwaveMagnitude, shockwaveRadius);
        }
        // Spawned as coin entities rather than credited straight to the purse: the magnet sweep is
        // what turns coins into money, and it is also the feedback that says the ability fired.
        if (bonus > 0 && killer.level() instanceof ServerLevel level
                && DungeonRunManager.runOf(killer.getUUID()) != null) {
            CoinDrops.spawnCoins(level, victim.position(), bonus);
        }
    }

    /** Coins this piece adds to a kill, or zero. Summed across every slot that carries BOTIN. */
    private static int loot(GearDef def) {
        AbilityDef botin = def == null ? null : def.ability(GearAbility.BOTIN);
        return botin == null ? 0 : (int) botin.magnitude(GearAbility.BOTIN.defaultMagnitude());
    }

    /** Where the shockwave's reach lived before {@code radio} could be written down. */
    private static final double DEFAULT_SHOCKWAVE_RADIUS = 2.5;

    /** The hammer's reward for the kill: everything nearby takes a share and gets pushed off. */
    private static void shockwave(ServerPlayer killer, LivingEntity victim, double fraction,
                                  double radius) {
        float damage = (float) (victim.getMaxHealth() * fraction);
        for (LivingEntity nearby : victim.level().getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(radius))) {
            if (nearby == victim || nearby == killer || nearby instanceof ServerPlayer) {
                continue;
            }
            // indirectMagic, not playerAttack: the shockwave is a share of the victim's health that
            // this ability authored, and playerAttack is a type CombatEngine reads as a swing — which
            // would replace the number AND consume whatever heavy the killer had wound up.
            nearby.hurt(killer.damageSources().indirectMagic(killer, killer), damage);
            Vec3 push = nearby.position().subtract(victim.position()).normalize().scale(0.5);
            nearby.push(push.x, 0.3, push.z);
        }
    }

    /**
     * The lesser phoenix re-arms once per floor rather than once per run: it is an epic chestplate
     * whose whole identity is the save, and a single charm across four stages would be strictly
     * worse than the 40-coin shop version it is named after.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % PHOENIX_CHECK_TICKS != 0) {
            return;
        }
        // The whole inventory, not just what our item classes tick: vanilla-based gear (a diamond
        // sword with a teras:gear_id component) has no inventoryTick of ours, worn armour is never
        // ticked by vanilla at all, and both must pick up a `/teras dungeon reload` while held.
        // A generation compare per stack every two seconds is as cheap as scans get.
        // Migration rides the same scan. A piece pulled out of a chest mid-session is the case
        // login and reload both miss, and this is the cheapest place to catch it: the test is a
        // component lookup and an item compare, on a scan that already happens.
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemStack migrated = es.boffmedia.teras.dungeon.gear.GearItems.migrate(stack);
            if (migrated != stack) {
                inventory.setItem(slot, migrated);
            } else {
                GearStamp.refresh(stack);
            }
        }

        lantern(player);

        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        if (run == null || !DungeonHealth.isInRun(player)) {
            PHOENIX_GRANTED.remove(player.getUUID());
            return;
        }
        if (!wears(player, GearAbility.FENIX_MENOR)) {
            return;
        }
        String floorKey = run.id() + ":" + run.stagesCleared();
        if (floorKey.equals(PHOENIX_GRANTED.get(player.getUUID()))) {
            return;
        }
        PHOENIX_GRANTED.put(player.getUUID(), floorKey);
        if (!run.stateOf(player.getUUID()).hasPhoenix()) {
            run.stateOf(player.getUUID()).grantPhoenix();
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§6Las alas de fénix se despliegan."), true);
        }
    }

    /**
     * Outlines what is moving nearby, for whoever is carrying a light.
     *
     * <p>Rides the two-second scan that is already happening rather than its own tick, and the
     * outline lasts a little longer than the gap between scans so it never blinks. Enemies only:
     * outlining another player would be a wallhack rather than a lamp, and outlining an item would
     * make the charm a treasure detector, which is a different item and a different decision.</p>
     */
    private static void lantern(ServerPlayer player) {
        AbilityDef lamp = null;
        for (ItemStack stack : bearing(player, player.getMainHandItem(), player.getOffhandItem())) {
            GearDef def = GearHolder.defOf(stack);
            if (def != null && def.ability(GearAbility.LINTERNA) != null) {
                lamp = def.ability(GearAbility.LINTERNA);
                break;
            }
        }
        if (lamp == null) {
            for (ItemStack stack : bearing(player, player.getArmorSlots())) {
                GearDef def = GearHolder.defOf(stack);
                if (def != null && def.ability(GearAbility.LINTERNA) != null) {
                    lamp = def.ability(GearAbility.LINTERNA);
                    break;
                }
            }
        }
        if (lamp == null) {
            return;
        }
        double radius = lamp.doubleParam(GearAbility.P_RADIUS, 12.0);
        // Long enough to outlive the gap between scans, or the outline blinks once every two
        // seconds and reads as a bug in the lamp rather than as a lamp.
        int ticks = Math.max(PHOENIX_CHECK_TICKS + 20,
                (int) (lamp.magnitude(GearAbility.LINTERNA.defaultMagnitude()) * 20));
        for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e instanceof net.minecraft.world.entity.monster.Monster && e.isAlive())) {
            nearby.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, false));
        }
    }

    /**
     * The ability line. Gear has no item classes to hang {@code appendHoverText} on — a piece is a
     * vanilla item plus a component — so the tooltip rides this event. Fires client-side only.
     */
    @SubscribeEvent
    public static void onItemTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        GearDef def = GearHolder.defOf(event.getItemStack());
        if (def != null) {
            GearTooltip.append(def.id(), event.getItemStack(), event.getToolTip());
        }
    }

    private static boolean wears(ServerPlayer player, GearAbility ability) {
        for (ItemStack stack : bearing(player, player.getArmorSlots())) {
            GearDef def = GearHolder.defOf(stack);
            if (def != null && def.has(ability)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The given stacks plus whatever the player is wearing in Curios slots.
     *
     * <p>Every hook that looks for an ability goes through here. The slot rules each hook already
     * had are unchanged — hands for on-hit, armour for on-hurt — and curios are added to all of
     * them, because a charm is worn no matter which hook is asking and there is no slot it
     * "belongs" to any more.</p>
     *
     * <p>Returns the stacks unchanged when Curios is absent, which is the offhand fallback working
     * by itself: the charm is simply in the offhand and the hand scans find it.</p>
     */
    private static Iterable<ItemStack> bearing(ServerPlayer player, ItemStack... base) {
        return bearing(player, java.util.Arrays.asList(base));
    }

    private static Iterable<ItemStack> bearing(ServerPlayer player, Iterable<ItemStack> base) {
        if (!es.boffmedia.teras.dungeon.gear.GearCurios.available()) {
            return base;
        }
        java.util.List<ItemStack> all = new java.util.ArrayList<>();
        base.forEach(all::add);
        all.addAll(es.boffmedia.teras.dungeon.gear.GearCurios.wornCurios(player));
        return all;
    }

    private static ItemStack[] held(ServerPlayer player) {
        return new ItemStack[]{
                player.getItemBySlot(EquipmentSlot.MAINHAND),
                player.getItemBySlot(EquipmentSlot.OFFHAND)};
    }

    /**
     * Healing goes through the dungeon's own path while in a run — the health lockdown cancels the
     * vanilla heal event, so a vampiric sword would silently do nothing inside a dungeon, which is
     * the one place it matters most.
     */
    private static void heal(ServerPlayer player, float amount) {
        if (amount <= 0) {
            return;
        }
        if (DungeonHealth.isInRun(player)) {
            DungeonHealth.heal(player, amount);
        } else {
            player.heal(amount);
        }
    }
}
