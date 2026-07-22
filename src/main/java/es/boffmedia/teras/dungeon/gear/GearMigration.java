package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.Teras;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Moves gear that predates the first-party items onto them.
 *
 * <p>This is the step that can destroy player property, so it is deliberately dull: it changes the
 * item a stack sits on and nothing else. Identity is the {@code teras:gear_id} component and every
 * other property — stats, ability, rarity, skin — is derived from the catalog, so nothing is carried
 * across because nothing needs to be.</p>
 *
 * <p>Gear persists across runs and lives in inventories forever. Without this, every piece ever
 * handed out becomes an ordinary diamond chestplate the moment the update lands, and gear is the
 * long-term reason to run dungeons at all.</p>
 *
 * <p>Runs on login, so it catches players who were offline for the update; on
 * {@code /teras dungeon reload}, so it can be re-run without waiting for a relog; and on the gear
 * tick for worn pieces, which catches one pulled out of a chest mid-session. What it does not reach
 * is a piece sitting in an unopened ender chest or shulker box — that converts the first time it is
 * taken out and worn.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class GearMigration {
    private GearMigration() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int converted = sweep(player);
            if (converted > 0) {
                Teras.LOGGER.info("Gear: migrated {} legacy piece(s) for {}",
                        converted, player.getGameProfile().getName());
            }
        }
    }

    /** Every online player's inventory. Called by {@code /teras dungeon reload}. */
    public static int sweepAll(MinecraftServer server) {
        int converted = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            converted += sweep(player);
        }
        return converted;
    }

    /**
     * One player's whole inventory — main, armour and offhand, since {@code Inventory}'s container
     * view covers all three.
     *
     * @return how many stacks were rebuilt
     */
    public static int sweep(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int converted = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemStack migrated = GearItems.migrate(stack);
            if (migrated != stack) {
                inventory.setItem(slot, migrated);
                converted++;
            }
        }
        return converted;
    }
}
