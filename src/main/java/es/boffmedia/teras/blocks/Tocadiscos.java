package es.boffmedia.teras.blocks;

import com.mojang.serialization.MapCodec;
import es.boffmedia.teras.blockentity.TocadiscosBlockEntity;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The record player.
 *
 * <p><b>A new build, not a port.</b> 1.16.5's {@code BloqueTocadiscos}/{@code TocadiscosTE} were
 * commented out at class level, along with both registrations, and would not have compiled if
 * uncommented — they imported a {@code util.music} package that never existed. The blockstate, both
 * models and both textures survived and are reused; the behaviour below is authored, because there
 * was none to match.</p>
 *
 * <p>Interaction: right-click with a stamped disc to load and play, right-click empty-handed to
 * eject, sneak-right-click to toggle repeat. Redstone <b>edges</b> control playback — a rising edge
 * starts, a falling edge stops — so a lever reads as an on/off switch while a jukebox with no
 * redstone attached simply plays when a disc goes in.</p>
 */
public class Tocadiscos extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<Tocadiscos> CODEC = simpleCodec(Tocadiscos::new);

    public Tocadiscos(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TocadiscosBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TocadiscosBlockEntity tocadiscos)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(ItemInit.DISCO.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        String track = es.boffmedia.teras.items.Disco.trackOf(stack);
        if (track == null) {
            player.displayClientMessage(Component.translatable("teras.tocadiscos.blank"), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (tocadiscos.hasDisc()) {
            eject(level, pos, tocadiscos);
        }

        tocadiscos.setDisc(stack);
        stack.shrink(1);
        if (tocadiscos.play()) {
            player.displayClientMessage(Component.translatable("teras.tocadiscos.playing", track), true);
        } else {
            // The disc is in regardless: a voice server that is merely not up yet should not eat it.
            player.displayClientMessage(Component.translatable("teras.tocadiscos.no_voice"), true);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TocadiscosBlockEntity tocadiscos)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            tocadiscos.setLooping(!tocadiscos.isLooping());
            player.displayClientMessage(Component.translatable(tocadiscos.isLooping()
                    ? "teras.tocadiscos.loop_on" : "teras.tocadiscos.loop_off"), true);
            return InteractionResult.CONSUME;
        }

        if (!tocadiscos.hasDisc()) {
            return InteractionResult.PASS;
        }
        eject(level, pos, tocadiscos);
        return InteractionResult.CONSUME;
    }

    /**
     * Redstone control, on edges rather than level.
     *
     * <p>Level would mean an unpowered jukebox — the normal case, with no redstone anywhere near it
     * — could never play. On edges, a lever becomes a switch and a jukebox nobody has wired stays
     * exactly as the last person left it.</p>
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour,
                                   BlockPos neighbourPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbour, neighbourPos, movedByPiston);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof TocadiscosBlockEntity tocadiscos)) {
            return;
        }
        // The last level seen lives on the block entity so it survives a reload; otherwise a
        // restart under a live lever reads as a rising edge and every wired jukebox starts at once.
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == tocadiscos.wasPowered()) {
            return;
        }
        tocadiscos.setWasPowered(powered);
        if (powered) {
            tocadiscos.play();
        } else {
            tocadiscos.stop();
        }
    }

    private static void eject(Level level, BlockPos pos, TocadiscosBlockEntity tocadiscos) {
        ItemStack removed = tocadiscos.removeDisc();
        if (!removed.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY() + 1.0, pos.getZ(), removed);
        }
    }

    /** Drops the disc when the player breaks the block, and stops the music with it. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TocadiscosBlockEntity tocadiscos) {
            eject(level, pos, tocadiscos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
