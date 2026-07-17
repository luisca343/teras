package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.items.FunkoItem;
import es.boffmedia.teras.items.SmartRotom;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ItemInit {
    private ItemInit() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Teras.MOD_ID);

    public static final DeferredItem<Item> SMARTROTOM = ITEMS.registerItem(
            "smartrotom",
            SmartRotom::new,
            new Item.Properties().stacksTo(1)
    );

    /** The funko's block item. Bespoke rather than a plain {@code BlockItem}: it renders its skin and tooltips it. */
    public static final DeferredItem<FunkoItem> FUNKO = ITEMS.registerItem(
            "funko",
            props -> new FunkoItem(BlockInit.FUNKO.get(), props),
            new Item.Properties().stacksTo(16)
    );

    public static final DeferredItem<BucketItem> CUBO_AGUAS_TERMALES = ITEMS.registerItem(
            "cubo_aguas_termales",
            props -> new BucketItem(FluidInit.AGUAS_TERMALES_SOURCE.get(), props),
            new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)
    );

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Teras.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TERAS_TAB =
            CREATIVE_TABS.register("teras", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.teras"))
                    .icon(() -> SMARTROTOM.get().getDefaultInstance())
                    // Every Teras item, in registration order — the 1.21 equivalent of 1.16.5 tagging
                    // each one with .tab(LIZARDON_GROUP), and it needs no upkeep as items are added.
                    .displayItems((params, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());
}
