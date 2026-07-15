package es.boffmedia.teras.items;

import es.boffmedia.teras.client.TerasClient;
import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * SmartRotom — opens the in-game MCEF browser. Each individual item carries its own
 * {@code smartrotom_id} ({@link ComponentInit#SMARTROTOM_ID}) and therefore its own browser instance,
 * restoring the 1.16.5 per-item pad behaviour (the old fragile client-side {@code PadID} counter is
 * replaced by a persistent, server-assigned {@link UUID}).
 *
 * <p>Right-click opens the SmartRotom screen for <em>this</em> item's browser. The 1.16.5 Pixelmon
 * dex-scan path ({@code openDex(...)} via raytrace) and the shift-click screenshot path are deferred
 * until the Pixelmon 1.21.1 dependency and the screenshot handler are wired in.</p>
 */
public class SmartRotom extends Item {
    public SmartRotom(Properties properties) {
        super(properties);
    }

    /** The per-item browser id, or {@code null} if one hasn't been assigned yet. */
    public static UUID getId(ItemStack stack) {
        return stack.get(ComponentInit.SMARTROTOM_ID.get());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // Assign a stable per-item id once, server-side, so it persists in the save and syncs to the
        // client authoritatively (a client can't spoof it). Runs before the item is ever used, so the
        // id is present by the time the renderer / open path need it.
        if (!level.isClientSide && stack.get(ComponentInit.SMARTROTOM_ID.get()) == null) {
            stack.set(ComponentInit.SMARTROTOM_ID.get(), UUID.randomUUID());
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            TerasClient.openSmartRotom(stack);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
