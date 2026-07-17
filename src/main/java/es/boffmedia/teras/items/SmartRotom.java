package es.boffmedia.teras.items;

import es.boffmedia.teras.client.TerasClient;
import es.boffmedia.teras.dex.api.DexProvider;
import es.boffmedia.teras.dex.api.DexProviders;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.init.ComponentInit;
import es.boffmedia.teras.net.TerasNet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * SmartRotom — opens the in-game MCEF browser. Each individual item carries its own
 * {@code smartrotom_id} ({@link ComponentInit#SMARTROTOM_ID}) and therefore its own browser instance,
 * restoring the 1.16.5 per-item pad behaviour (the old fragile client-side {@code PadID} counter is
 * replaced by a persistent, server-assigned {@link UUID}).
 *
 * <p>Right-click does one of three things, as in 1.16.5: shift-clicking with the camera page open fires
 * the shutter; aiming at a Pokémon scans it — the item's browser jumps to that dex entry in-hand and
 * the server registers it as seen; anything else opens the SmartRotom screen.</p>
 */
public class SmartRotom extends Item {

    /** How far the dex scan reaches, in blocks. {@code TerasNet} re-checks this server-side. */
    public static final double SCAN_RANGE = 25.0;

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
        // Client-driven: the scan follows the player's camera and targets their own browser. The server
        // re-reads the species off the entity rather than trusting this scan (see DexRegisterPayload).
        if (!level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }

        // Shift-click is the camera shutter, but only while this item's browser is on the camera page;
        // otherwise it falls through to the scan.
        if (player.isShiftKeyDown() && TerasClient.tryCameraShutter(stack)) {
            return InteractionResultHolder.success(stack);
        }

        Entity target = rayTracedEntity(player);
        DexScan scan = scanOf(target);
        if (scan != null) {
            // Shown from the client's own read so the page doesn't wait on a round-trip.
            TerasClient.openDex(stack, scan);
            TerasNet.registerDex(target.getId());
            return InteractionResultHolder.success(stack);
        }

        TerasClient.openSmartRotom(stack);
        return InteractionResultHolder.success(stack);
    }

    /** The scan for {@code target}, or {@code null} if it isn't a Pokémon (or no engine is installed). */
    private static DexScan scanOf(Entity target) {
        if (target == null) {
            return null;
        }
        DexProvider provider = DexProviders.get();
        return provider == null ? null : provider.scan(target);
    }

    /**
     * The entity under the player's crosshair within {@link #SCAN_RANGE}, or {@code null}. Filters on
     * {@link Entity#isPickable()} rather than on being a Pokémon: this item loads on every client, so
     * it must name no engine class — the provider answers that.
     */
    private static Entity rayTracedEntity(Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).scale(SCAN_RANGE);
        Vec3 end = eye.add(look);
        AABB box = player.getBoundingBox().expandTowards(look).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, end, box, Entity::isPickable, SCAN_RANGE * SCAN_RANGE);
        return hit == null ? null : hit.getEntity();
    }
}
