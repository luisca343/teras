package es.boffmedia.teras.region;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.TerasRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * WorldGuard-style protection, enforced from the region flags and plot ownership — new in the
 * port: on 1.16.5 the server ran WorldGuard for this, the mod never did it.
 *
 * <p>Two rules, resolved together in {@link RegionResolver#decide}. Among the regions containing
 * the position, only those at the highest priority get a say; within that tier, a plot the player
 * does not hold denies implicitly, and a flag set to {@code denegar} denies explicitly. Ops
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

    /**
     * Item frames, armour stands and the like. Judged at the <em>entity's</em> position, not the
     * player's: without this a plot is protected against block edits while its decorations stay
     * open to anyone standing outside the border.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (blockedAt(event.getEntity(), target.getX(), target.getY(), target.getZ(),
                RegionFlag.INTERACT)) {
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
            notifyBlocked(attacker, null);
        }
    }

    /**
     * Not cancellable — instead the protected positions are pulled out of the affected list. Uses
     * the flag-only check: an explosion has no player to be a plot member, so gating it on
     * ownership would block every explosion inside every plot. Admins who want that set
     * {@code explosions} to {@code denegar} on the plot.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (RegionStore.all().isEmpty()) return;
        String dimension = dimensionOf(event.getLevel());
        event.getAffectedBlocks().removeIf(pos -> RegionIndex.denies(
                dimension, pos.getX(), pos.getY(), pos.getZ(), RegionFlag.EXPLOSIONS));
    }

    private static boolean blocked(Player player, BlockPos pos, RegionFlag flag) {
        return blockedAt(player, pos.getX(), pos.getY(), pos.getZ(), flag);
    }

    private static boolean blockedAt(Player player, double x, double y, double z, RegionFlag flag) {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (sp.hasPermissions(BYPASS_PERMISSION_LEVEL)) return false;
        RegionResolver.Decision decision = RegionIndex.decide(
                dimensionOf(sp.level()), x, y, z, flag, sp.getUUID());
        if (!decision.denied()) return false;
        notifyBlocked(sp, decision);
        return true;
    }

    /**
     * Action bar rather than chat: border spam while a player tests the edge must not scroll chat.
     * A plot says who holds it — "someone owns this" is a different problem for the player than
     * "this is protected", and only one of them has a person to go ask.
     */
    private static void notifyBlocked(ServerPlayer player, RegionResolver.Decision decision) {
        Component message;
        if (decision != null && decision.outcome() == RegionResolver.Outcome.DENIED_BY_PLOT) {
            String plot = TerasRegion.titleCase(decision.regionName());
            String owner = ownerName(player.getServer(), decision.owner());
            message = owner == null
                    ? Component.translatable("message.teras.plot_for_sale", plot)
                    : Component.translatable("message.teras.plot_owned_by", plot, owner);
        } else {
            message = Component.translatable("message.teras.region_protected");
        }
        player.displayClientMessage(message, true);
    }

    /** The owner's name, or {@code null} when the plot is unowned or the uuid is unknown. */
    private static String ownerName(MinecraftServer server, UUID owner) {
        if (owner == null || server == null || server.getProfileCache() == null) return null;
        Optional<com.mojang.authlib.GameProfile> profile = server.getProfileCache().get(owner);
        return profile.map(com.mojang.authlib.GameProfile::getName).orElse(null);
    }

    private static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }
}
