package es.boffmedia.teras.dungeon.mecanica;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.init.BlockInit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import es.boffmedia.teras.Teras;

/**
 * Places and clears combat webbing.
 *
 * <p>Clearing goes through <b>left-click</b> rather than block breaking: the party is in adventure
 * mode and cannot break anything, so a web that had to be mined would be a permanent trap.</p>
 *
 * <h2>Why the interaction event is not enough</h2>
 *
 * <p>{@link PlayerInteractEvent.LeftClickBlock} was the whole implementation, and in adventure mode
 * it <b>never fires</b> — which is why webbing read as unbreakable. Verified against the 1.21.1
 * sources: {@code MultiPlayerGameMode.startDestroyBlock} calls {@code Player.blockActionRestricted}
 * <i>first</i> and returns early, before NeoForge's {@code onLeftClickBlock} hook and before any
 * packet is sent. With an empty hand or a tool carrying no matching {@code can_break} component,
 * that check is always true, so the server is never told the click happened.</p>
 *
 * <p>What does reach the server is the swing: {@code Minecraft.startAttack} calls
 * {@code player.swing(MAIN_HAND)} regardless of what {@code startDestroyBlock} returned, so a
 * {@code ServerboundSwingPacket} arrives either way. {@link #onPlayerTick} watches for the start of
 * a swing and clears whatever web the player is aiming at. The interaction handler stays for the
 * modes where it does fire — creative, survival, or an op holding a {@code can_break} tool — and
 * the two are idempotent, so a click handled by both simply clears the same block twice.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class WebPlacer {
    private WebPlacer() {}

    /** Thin webbing at {@code pos} if there is room for it. */
    public static boolean placeThin(ServerLevel level, BlockPos pos, Entity source) {
        return place(level, pos, false, false);
    }

    /** The queen's walls: full collision, and a much shorter life. */
    public static boolean placeDense(ServerLevel level, BlockPos pos, boolean poisonous) {
        return place(level, pos, true, poisonous);
    }

    public static boolean place(ServerLevel level, BlockPos pos, boolean dense, boolean poisonous) {
        BlockState existing = level.getBlockState(pos);
        // Only into air. Webbing that replaced floor or walls could open a hole in a room, and a
        // dense web dropped into a doorway would seal a fight nobody could leave.
        if (!existing.isAir() && !existing.is(BlockInit.TELARANA.get())) {
            return false;
        }
        BlockState web = BlockInit.TELARANA.get().defaultBlockState()
                .setValue(TelaranaBlock.DENSA, dense)
                .setValue(TelaranaBlock.VENENOSA, poisonous);
        level.setBlock(pos, web, 3);
        level.scheduleTick(pos, BlockInit.TELARANA.get(),
                dense ? TelaranaBlock.DENSE_DECAY : TelaranaBlock.THIN_DECAY);
        return true;
    }

    /**
     * A left-click on webbing clears it — in the game modes where this event fires at all. See the
     * class note: adventure mode is not one of them, which is what {@link #onPlayerTick} covers.
     */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (clear(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * The adventure-mode path: a swing that starts while the player is aiming at webbing clears it.
     *
     * <p>{@code swing()} sets {@code swingTime} to -1 and {@code Player.serverAiStep} advances it,
     * so {@code swinging} with a non-positive timer is the first tick of a swing and fires once per
     * swing — including the repeats a held mouse button produces, since each re-trigger resets the
     * timer.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.swinging || player.swingTime > 0) {
            return;
        }
        // Cheap gate: only floors have webbing, and this runs for every player every tick.
        if (!player.level().dimension().location().toString().equals(DungeonsConfig.dimension())) {
            return;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 0f, false);
        if (hit instanceof BlockHitResult block && clear(player.level(), block.getBlockPos())) {
            return;
        }
        // Being stuck in the webbing is the case that matters most and the one a raycast cannot
        // answer: a ray starting inside a block does not hit it, so a player webbed at the feet and
        // swinging at it would clear nothing. The two blocks they occupy are cleared instead.
        BlockPos feet = player.blockPosition();
        if (!clear(player.level(), feet)) {
            clear(player.level(), feet.above());
        }
    }

    /** Clears webbing at {@code pos}; false when there is none, so callers can ignore the click. */
    private static boolean clear(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockInit.TELARANA.get())) {
            return false;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOL_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.4f);
        return true;
    }
}
