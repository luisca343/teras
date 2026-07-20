package es.boffmedia.teras.dungeon.mecanica;

import es.boffmedia.teras.init.BlockInit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import es.boffmedia.teras.Teras;

/**
 * Places and clears combat webbing.
 *
 * <p>Clearing goes through <b>left-click</b> rather than block breaking, and that is the point: the
 * party is in adventure mode and cannot break anything, so a web that had to be mined would be a
 * permanent trap. Treating the click as an <i>interaction</i> sidesteps the game mode entirely — no
 * {@code can_break} grant on any tool, no exception carved into the dungeon's no-mining rule, and
 * webbing stays the only thing in a floor a player may remove.</p>
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
     * A left-click on webbing clears it. Handled on the interaction event because in adventure mode
     * the break never reaches {@code BlockEvent.BreakEvent} at all.
     */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(BlockInit.TELARANA.get())) {
            return;
        }
        event.getLevel().setBlock(event.getPos(), Blocks.AIR.defaultBlockState(), 3);
        event.getLevel().playSound(null, event.getPos(),
                net.minecraft.sounds.SoundEvents.WOOL_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.4f);
        event.setCanceled(true);
    }
}
