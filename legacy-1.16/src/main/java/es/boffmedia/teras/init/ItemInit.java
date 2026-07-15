package es.boffmedia.teras.init;

import com.pixelmonmod.pixelmon.items.BadgeItem;
import es.boffmedia.teras.client.funko.FunkoItemRenderer;
import es.boffmedia.teras.items.*;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ItemInit {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "teras");

    public static final RegistryObject<Item> SMARTROTOM = ITEMS.register("smartrotom",
            () -> new SmartRotom(new Item.Properties().stacksTo(1).tab(TerasItemGroup.LIZARDON_GROUP)));

    // Funko statue. The ISTER (client-only) renders the actual skin in inventory/hand;
    // the supplier is only invoked on the client, so referencing the client class here is dist-safe.
    public static final RegistryObject<Item> FUNKO = ITEMS.register("funko",
            () -> new FunkoItem(BlockInit.FUNKO.get(), new Item.Properties().stacksTo(16)
                    .tab(TerasItemGroup.LIZARDON_GROUP)
                    .setISTER(() -> FunkoItemRenderer::new)));

    /*public static final RegistryObject<Item> MEDALLA = ITEMS.register("medalla_helada",
            () -> new BadgeItem());

    public static final RegistryObject<Item> DISCO = ITEMS.register("disco",
            () -> new Disco());

    public static final RegistryObject<Item> TEST = ITEMS.register("test",
            () -> new Item(new Item.Properties().stacksTo(6).tab(TerasItemGroup.LIZARDON_GROUP)));

    public static final RegistryObject<Item> CHAPA_OXIDADA = ITEMS.register("chapa_oxidada",
            () -> new ChapaOxidada());

   */
    
    public static final RegistryObject<Item> LATIGO_NUMERIL = ITEMS.register("latigo_numeril",
            () -> new LatigoNumeril(new Item.Properties().stacksTo(1).tab(TerasItemGroup.LIZARDON_GROUP)));
    public static final RegistryObject<Item> PORRA = ITEMS.register("porra",
            () -> new Porra(new Item.Properties().stacksTo(1).tab(TerasItemGroup.LIZARDON_GROUP)));

    public static final RegistryObject<Item> TASER = ITEMS.register("taser",
            () -> new Taser(new Item.Properties().stacksTo(1).tab(TerasItemGroup.LIZARDON_GROUP)));

    public static final RegistryObject<Item> CUBO_AGUAS_TERMALES = ITEMS.register("cubo_aguas_termales",
            () -> new BucketItem(() -> FluidInit.AGUAS_TERMALES_SOURCE.get(), new Item.Properties().stacksTo(1).tab(TerasItemGroup.LIZARDON_GROUP)));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
