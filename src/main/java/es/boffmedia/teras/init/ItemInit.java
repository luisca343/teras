package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.item.DungeonPotionItem;
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

    /**
     * The dungeon coin. It is only ever an item entity on a dungeon floor: enemies drop it, the run
     * loop's magnet sweep credits the party wallet and discards it, and nothing puts one in an
     * inventory — see {@link es.boffmedia.teras.dungeon.run.CoinDrops}.
     */
    public static final DeferredItem<Item> MONEDA_MAZMORRA = ITEMS.registerItem(
            "moneda_mazmorra",
            Item::new,
            new Item.Properties().stacksTo(64)
    );

    /** Wall-breaker charge, same never-in-inventory contract as the coin: it credits shared stock. */
    public static final DeferredItem<Item> CARGA_ROMPEMUROS = ITEMS.registerItem(
            "carga_rompemuros",
            Item::new,
            new Item.Properties().stacksTo(16)
    );

    /** Dungeon healing, the only healing a run allows; see {@link DungeonPotionItem}. */
    public static final DeferredItem<DungeonPotionItem> POCION_VITAL = ITEMS.registerItem(
            "pocion_vital",
            props -> new DungeonPotionItem(props, 8.0f),
            new Item.Properties().stacksTo(8)
    );

    public static final DeferredItem<DungeonPotionItem> POCION_VITAL_MAYOR = ITEMS.registerItem(
            "pocion_vital_mayor",
            props -> new DungeonPotionItem(props, Float.MAX_VALUE),
            new Item.Properties().stacksTo(4)
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
