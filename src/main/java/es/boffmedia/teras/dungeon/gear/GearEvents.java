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
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
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
        }
    }

    /** Main-hand and offhand both count: the charms are held, and that is how they are worn. */
    private static void onHit(ServerPlayer attacker, LivingEntity victim, float amount) {
        for (ItemStack stack : new ItemStack[]{attacker.getMainHandItem(), attacker.getOffhandItem()}) {
            GearDef def = GearHolder.defOf(stack);
            if (def == null) {
                continue;
            }
            switch (def.ability()) {
                case VAMPIRISMO -> heal(attacker, (float) (amount * def.magnitude()));
                case QUEMAZON -> victim.igniteForSeconds((float) def.magnitude());
                case DESGARRO -> victim.addEffect(new MobEffectInstance(
                        MobEffects.WITHER, (int) (def.magnitude() * 20), 1));
                default -> { }
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
        for (ItemStack stack : defender.getArmorSlots()) {
            GearDef def = GearHolder.defOf(stack);
            if (def != null && def.ability() == GearAbility.ESPINAS) {
                attacker.hurt(defender.damageSources().thorns(defender),
                        (float) (amount * def.magnitude()));
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

        for (ItemStack stack : held(killer)) {
            GearDef def = GearHolder.defOf(stack);
            if (def == null) {
                continue;
            }
            if (def.ability() == GearAbility.BOTIN) {
                bonus += (int) def.magnitude();
            } else if (def.ability() == GearAbility.ONDA) {
                shockwave = true;
                shockwaveMagnitude = Math.max(shockwaveMagnitude, def.magnitude());
            }
        }
        for (ItemStack stack : killer.getArmorSlots()) {
            GearDef def = GearHolder.defOf(stack);
            if (def != null && def.ability() == GearAbility.BOTIN) {
                bonus += (int) def.magnitude();
            }
        }

        if (shockwave) {
            shockwave(killer, victim, shockwaveMagnitude);
        }
        // Spawned as coin entities rather than credited straight to the purse: the magnet sweep is
        // what turns coins into money, and it is also the feedback that says the ability fired.
        if (bonus > 0 && killer.level() instanceof ServerLevel level
                && DungeonRunManager.runOf(killer.getUUID()) != null) {
            CoinDrops.spawnCoins(level, victim.position(), bonus);
        }
    }

    /** The hammer's reward for the kill: everything nearby takes a share and gets pushed off. */
    private static void shockwave(ServerPlayer killer, LivingEntity victim, double fraction) {
        double radius = 2.5;
        float damage = (float) (victim.getMaxHealth() * fraction);
        for (LivingEntity nearby : victim.level().getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(radius))) {
            if (nearby == victim || nearby == killer || nearby instanceof ServerPlayer) {
                continue;
            }
            nearby.hurt(killer.damageSources().playerAttack(killer), damage);
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
        for (ItemStack stack : player.getInventory().items) {
            GearStamp.refresh(stack);
        }
        for (ItemStack worn : player.getArmorSlots()) {
            GearStamp.refresh(worn);
        }
        GearStamp.refresh(player.getOffhandItem());

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
        for (ItemStack stack : player.getArmorSlots()) {
            GearDef def = GearHolder.defOf(stack);
            if (def != null && def.ability() == ability) {
                return true;
            }
        }
        return false;
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
