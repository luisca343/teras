package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.blocks.Funko;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry.
 *
 * <p>1.16.5 {@code BlockInit} auto-registered a plain {@code BlockItem} for every block it declared.
 * That helper is deliberately not reproduced yet: the only block ported so far needs a bespoke item
 * ({@link ItemInit#FUNKO}), and 1.21 {@code Material} is gone — block properties now spell out
 * {@code mapColor}/{@code sound} individually, so there is no one-size default to hide in a helper.
 * Add one when a block that wants a vanilla item shows up.</p>
 */
public final class BlockInit {
    private BlockInit() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Teras.MOD_ID);

    /** Block only — the funko item is a custom {@link es.boffmedia.teras.items.FunkoItem}, see {@link ItemInit#FUNKO}. */
    public static final DeferredBlock<Funko> FUNKO = BLOCKS.register("funko",
            () -> new Funko(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .sound(SoundType.WOOL)
                    .strength(0.8F)
                    .noOcclusion()));
}
