package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Health is a resource inside a dungeon, not a timer. Vanilla regeneration undoes every cost the
 * design leans on — a sacrifice plate, a curse toll, a devil deal and the price of a bad fight all
 * become "wait and eat a steak" — so for the duration of a run <b>all</b> vanilla healing is
 * cancelled and hunger is pinned full (a frozen bar cannot regenerate, and it cannot starve
 * either: the lockdown must not become a slow death sentence).
 *
 * <p>Healing therefore happens in exactly one place — {@link #heal}, which writes health directly
 * and so is not subject to the cancel. Dungeon potions, the phoenix charm and any future reward
 * route through it. Isaac's economy of hearts, in a game whose players carry food.</p>
 *
 * <p>Scope is strictly run membership: a player outside a dungeon is untouched, and every exit
 * path restores what was changed (see {@code RunEngine.clearRunEffects}).</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DungeonHealth {
    private DungeonHealth() {}

    /** Devil deals sell maximum health; one fixed id so the modifier is idempotent and removable. */
    public static final ResourceLocation DEVIL_DEAL_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "devil_deal");

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInRun(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * True while the player is actually inside a dungeon — the only condition anything here
     * applies on.
     *
     * <p>Both halves matter. A run exists from the moment it is started, but its party is still
     * standing in town for the second or two the floor takes to build, and a run whose build job
     * failed never leaves that state at all; gating on the run alone would lock healing for
     * players who are nowhere near a dungeon. Checking the dimension as well means the lockdown
     * begins when the party arrives and cannot outlive their being there.</p>
     */
    public static boolean isInRun(ServerPlayer player) {
        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        return run != null && player.serverLevel().dimension().location().toString()
                .equals(DungeonsConfig.dimension());
    }

    /**
     * The sanctioned way to give health back. Writes it directly rather than calling {@code heal},
     * which {@link #onHeal} cancels; clamped to the player's current maximum, so hearts sold to a
     * devil deal stay sold.
     */
    public static void heal(ServerPlayer player, float amount) {
        if (amount <= 0) {
            return;
        }
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
    }

    /** Restores the player to full within their current maximum. */
    public static void healFully(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
    }

    /** Called from the run tick: keeps the bar full so nothing regenerates and nothing starves. */
    public static void holdHunger(ServerPlayer player) {
        if (player.getFoodData().getFoodLevel() < 20) {
            player.getFoodData().setFoodLevel(20);
        }
        player.getFoodData().setSaturation(5.0f);
    }

    /**
     * Applies the run's accumulated max-health debt. Re-applied after every respawn: a fresh player
     * entity comes back with vanilla attributes, and a deal paid in hearts that a death refunded
     * would make dying the cheapest way to settle it.
     */
    public static void applyHpDebt(ServerPlayer player, int halfHearts) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(DEVIL_DEAL_MODIFIER);
        if (halfHearts <= 0) {
            return;
        }
        attribute.addPermanentModifier(new AttributeModifier(DEVIL_DEAL_MODIFIER, -halfHearts,
                AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Gives the hearts back: the debt is a run-long cost, not a permanent one. */
    public static void clearHpDebt(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(DEVIL_DEAL_MODIFIER);
        }
    }
}
