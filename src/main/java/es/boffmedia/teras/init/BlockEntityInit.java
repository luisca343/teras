package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.blockentity.FrameBlockEntity;
import es.boffmedia.teras.blockentity.FunkoBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity types — the 1.21 port of 1.16.5 {@code TileEntityInit}. */
public final class BlockEntityInit {
    private BlockEntityInit() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Teras.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FunkoBlockEntity>> FUNKO =
            BLOCK_ENTITIES.register("funko", () -> BlockEntityType.Builder
                    .of(FunkoBlockEntity::new, BlockInit.FUNKO.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<es.boffmedia.teras.blockentity.TocadiscosBlockEntity>> TOCADISCOS =
            BLOCK_ENTITIES.register("tocadiscos", () -> BlockEntityType.Builder
                    .of(es.boffmedia.teras.blockentity.TocadiscosBlockEntity::new, BlockInit.TOCADISCOS.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FrameBlockEntity>> FRAME =
            BLOCK_ENTITIES.register("picture_frame", () -> BlockEntityType.Builder
                    .of(FrameBlockEntity::new, BlockInit.PICTURE_FRAME.get())
                    .build(null));
}
