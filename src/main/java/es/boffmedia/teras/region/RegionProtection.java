package es.boffmedia.teras.region;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.region.model.RegionFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * WorldGuard-style protection, enforced from the region flags — new in the port: on 1.16.5 the
 * server ran WorldGuard for this, the mod never did it.
 *
 * <p>Rule: a flag explicitly set to {@code denegar} in ANY region containing the position blocks
 * the action (most-restrictive-wins across overlaps; a region with no opinion allows). Ops
 * (permission level 2) bypass everything, matching the admin gate on the region commands.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RegionProtection {
    private RegionProtection() {}

    private static final int BYPASS_PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (blocked(event.getPlayer(), event.getPos(), RegionFlag.BREAK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player
                && blocked(player, event.getPos(), RegionFlag.BUILD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity(), event.getPos(), RegionFlag.INTERACT)) {
            event.setCanceled(true);
        }
    }

    /** PvP: judged at the victim's position, so a denied town protects whoever stands in it. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (attacker == victim || attacker.hasPermissions(BYPASS_PERMISSION_LEVEL)) return;
        if (RegionIndex.denies(dimensionOf(victim.level()),
                victim.getX(), victim.getY(), victim.getZ(), RegionFlag.PVP)) {
            event.setCanceled(true);
            notifyBlocked(attacker);
        }
    }

    /** Not cancellable — instead the protected positions are pulled out of the affected list. */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (RegionStore.all().isEmpty()) return;
        String dimension = dimensionOf(event.getLevel());
        event.getAffectedBlocks().removeIf(pos -> RegionIndex.denies(
                dimension, pos.getX(), pos.getY(), pos.getZ(), RegionFlag.EXPLOSIONS));
    }

    private static boolean blocked(Player player, BlockPos pos, RegionFlag flag) {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (sp.hasPermissions(BYPASS_PERMISSION_LEVEL)) return false;
        if (!RegionIndex.denies(dimensionOf(sp.level()),
                pos.getX(), pos.getY(), pos.getZ(), flag)) {
            return false;
        }
        notifyBlocked(sp);
        return true;
    }

    /** Action bar rather than chat: border spam while a player tests the edge must not scroll chat. */
    private static void notifyBlocked(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.teras.region_protected"), true);
    }

    private static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }
}
