package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.blocks.*;
import es.boffmedia.teras.items.TerasItemGroup;
import es.boffmedia.teras.model.world.ObjColocable;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.function.Supplier;

public class BlockInit {
    public static final DeferredRegister<Block> BLOCKS
            = DeferredRegister.create(ForgeRegistries.BLOCKS, Teras.MOD_ID);

    public static RegistryObject TWISTER_AMARILLO = registerBlock("twister_amarillo", () -> new Alfombra(Material.WOOL));
    public static RegistryObject TWISTER_AZUL = registerBlock("twister_azul", () -> new Alfombra(Material.WOOL));
    public static RegistryObject TWISTER_ROJO = registerBlock("twister_rojo", () -> new Alfombra(Material.WOOL));
    public static RegistryObject TWISTER_VERDE = registerBlock("twister_verde", () -> new Alfombra(Material.WOOL));
    // Block only: the funko item is a custom FunkoItem (with an inventory renderer) registered in ItemInit.
    public static final RegistryObject<Block> FUNKO = BLOCKS.register("funko",
            () -> new Funko(AbstractBlock.Properties.of(Material.WOOL).strength(0.8F).noOcclusion()));

    /*
    public static final RegistryObject<Block> TOCADISCOS = registerBlock("tocadiscos", () -> new BloqueTocadiscos(AbstractBlock.Properties.of(Material.STONE)));
    */
    public static final RegistryObject<Block> PANTALLA = registerBlock("pantalla", () -> new BloquePantalla(AbstractBlock.Properties.of(Material.STONE)));
    public static final RegistryObject<Block> TVBLOCK = registerBlock("frame", () -> new TVBlock(AbstractBlock.Properties.of(Material.STONE)));

    public static final RegistryObject<Block> BLUE_NETHER_BRICK_STAIRS = registerBlock("blue_nether_brick_stairs", () -> new StairsBlock(Blocks.OAK_PLANKS.defaultBlockState(),AbstractBlock.Properties.of(Material.STONE)));

    public static final RegistryObject<Block> BLUE_NETHER_BRICK_SLAB = registerBlock("blue_nether_brick_slab", () -> new SlabBlock(AbstractBlock.Properties.of(Material.STONE).requiresCorrectToolForDrops().harvestTool(ToolType.PICKAXE)));

    public static final RegistryObject<Block> LAVENDER = registerBlock("lavender", () -> new LavenderFlower());

    public static void inicializarComidas(){
        ArrayList<ObjColocable> objetos = ComidasTeras.getComidas();
        for (ObjColocable objeto : objetos) {
            registrarBloqueTeras(objeto, () -> new BloqueTeras(objeto.getHitbox()));
        }
    }

    private static <T extends Block>RegistryObject<T> registrarBloqueTeras(ObjColocable objeto, Supplier<T> block){
        RegistryObject<T> toReturn = BLOCKS.register(objeto.getNombre(), block);
        ItemInit.ITEMS.register(objeto.getNombre(), () -> new ObjetoColocable(toReturn.get(), objeto));
        return toReturn;
    }

    private static <T extends Block>RegistryObject<T> registerBlock(String name, Supplier<T> block){
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block){
        ItemInit.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(TerasItemGroup.LIZARDON_GROUP)));
    }


    public static void register(IEventBus eventBus) {
        inicializarComidas();
        BLOCKS.register(eventBus);
    }
}

