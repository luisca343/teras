package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.blocks.BloqueAguasTermales;
import es.boffmedia.teras.blocks.BloqueTeras;
import es.boffmedia.teras.blocks.Funko;
import es.boffmedia.teras.blocks.PictureFrame;
import es.boffmedia.teras.items.ObjetoColocable;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class BlockInit {
    private BlockInit() {}

    /** 1.16.5 passed 100 <em>ticks</em>; the 1.21 {@link FlowerBlock} constructor takes seconds. */
    private static final float LAVENDER_STEW_SECONDS = 5.0F;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Teras.MOD_ID);

    /** Block only — the funko item is a custom {@link es.boffmedia.teras.items.FunkoItem}, see {@link ItemInit#FUNKO}. */
    public static final DeferredBlock<Funko> FUNKO = BLOCKS.register("funko",
            () -> new Funko(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .sound(SoundType.WOOL)
                    .strength(0.8F)
                    .noOcclusion()));

    /** Empty model — {@code FrameRenderer} draws the media. See {@link es.boffmedia.teras.blockentity.FrameBlockEntity}. */
    public static final DeferredBlock<PictureFrame> PICTURE_FRAME = registerWithItem("picture_frame",
            () -> new PictureFrame(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .sound(SoundType.METAL)
                    .strength(0.8F)
                    .noOcclusion()));

    public static final DeferredBlock<CarpetBlock> TWISTER_AMARILLO = twister("twister_amarillo", MapColor.COLOR_YELLOW);
    public static final DeferredBlock<CarpetBlock> TWISTER_AZUL = twister("twister_azul", MapColor.COLOR_BLUE);
    public static final DeferredBlock<CarpetBlock> TWISTER_ROJO = twister("twister_rojo", MapColor.COLOR_RED);
    public static final DeferredBlock<CarpetBlock> TWISTER_VERDE = twister("twister_verde", MapColor.COLOR_GREEN);

    public static final DeferredBlock<FlowerBlock> LAVENDER = registerWithItem("lavender",
            () -> new FlowerBlock(MobEffects.NIGHT_VISION, LAVENDER_STEW_SECONDS,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY)));

    public static final DeferredBlock<BloqueAguasTermales> AGUAS_TERMALES = BLOCKS.register("aguas_termales",
            () -> new BloqueAguasTermales(FluidInit.AGUAS_TERMALES_SOURCE.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).randomTicks()));

    /** One block plus one {@link ObjetoColocable} per {@link ComidasTeras#COMIDAS} entry. */
    public static final List<DeferredBlock<BloqueTeras>> COMIDAS = registerComidas();

    private static DeferredBlock<CarpetBlock> twister(String name, MapColor color) {
        return registerWithItem(name, () -> new CarpetBlock(BlockBehaviour.Properties.of()
                .mapColor(color)
                .sound(SoundType.WOOL)
                .strength(0.1F)
                .pushReaction(PushReaction.DESTROY)
                .ignitedByLava()));
    }

    private static List<DeferredBlock<BloqueTeras>> registerComidas() {
        List<DeferredBlock<BloqueTeras>> blocks = new ArrayList<>();
        for (ComidasTeras.Comida comida : ComidasTeras.COMIDAS) {
            // Not ofFullCopy(STONE): that drags in requiresCorrectToolForDrops + strength 1.5, so a
            // food broken by hand would drop nothing. 1.16.5's Material.STONE was instant-break.
            DeferredBlock<BloqueTeras> block = BLOCKS.register(comida.nombre(),
                    () -> new BloqueTeras(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .sound(SoundType.STONE)
                            .instabreak()
                            .noOcclusion(),
                            comida.hitbox()));
            ItemInit.ITEMS.registerItem(comida.nombre(),
                    props -> new ObjetoColocable(block.get(), comida.animacion(), props),
                    new Item.Properties().food(comida.food()));
            blocks.add(block);
        }
        return blocks;
    }

    private static <B extends Block> DeferredBlock<B> registerWithItem(String name, Supplier<B> block) {
        DeferredBlock<B> registered = BLOCKS.register(name, block);
        ItemInit.ITEMS.registerSimpleBlockItem(registered);
        return registered;
    }
}
