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

    // --- dungeon gear -------------------------------------------------------------------------
    //
    // Eight items, one per GearKind — not one per piece. Which piece a stack IS stays the
    // `teras:gear_id` component, so the catalog remains config-defined: a new piece is an entry in
    // gear.json, not a new registered item plus assets plus a code change.
    //
    // They replace riding on vanilla bases. A piece used to be a real diamond chestplate, which
    // meant it was a diamond chestplate to anvils, enchanting, recipes and every other mod on the
    // server — and since gear leaves the dungeon and is kept forever, that leaked into the whole
    // item economy. Behaviourally this changes nothing: GearStamp replaces each stack's attribute
    // modifiers outright, so the numbers were always gear.json's and never the base item's.

    /** The sword. Grey until an Armourer's Workshop skin is configured for the piece. */
    public static final DeferredItem<Item> ARMA_ESPADA = ITEMS.registerItem(
            "gear_espada",
            props -> new net.minecraft.world.item.SwordItem(GearMaterialInit.TIER, props),
            new Item.Properties().stacksTo(1)
    );

    /** The axe: the slower, harder-hitting melee profile. */
    public static final DeferredItem<Item> ARMA_HACHA = ITEMS.registerItem(
            "gear_hacha",
            props -> new net.minecraft.world.item.AxeItem(GearMaterialInit.TIER, props),
            new Item.Properties().stacksTo(1)
    );

    /**
     * The shield. Possible only because charms move to a Curios slot and stop occupying the
     * offhand — one decision paid for the other.
     */
    public static final DeferredItem<Item> ARMA_ESCUDO = ITEMS.registerItem(
            "gear_escudo",
            net.minecraft.world.item.ShieldItem::new,
            new Item.Properties().stacksTo(1).durability(336)
    );

    public static final DeferredItem<Item> GEAR_YELMO = ITEMS.registerItem(
            "gear_yelmo",
            props -> new es.boffmedia.teras.dungeon.gear.GearArmorItem(GearMaterialInit.dungeon(),
                    net.minecraft.world.item.ArmorItem.Type.HELMET, props),
            new Item.Properties().stacksTo(1).durability(
                    net.minecraft.world.item.ArmorItem.Type.HELMET.getDurability(GearMaterialInit.ARMOR_DURABILITY))
    );

    public static final DeferredItem<Item> GEAR_CORAZA = ITEMS.registerItem(
            "gear_coraza",
            props -> new es.boffmedia.teras.dungeon.gear.GearArmorItem(GearMaterialInit.dungeon(),
                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, props),
            new Item.Properties().stacksTo(1).durability(
                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE.getDurability(GearMaterialInit.ARMOR_DURABILITY))
    );

    public static final DeferredItem<Item> GEAR_GREBAS = ITEMS.registerItem(
            "gear_grebas",
            props -> new es.boffmedia.teras.dungeon.gear.GearArmorItem(GearMaterialInit.dungeon(),
                    net.minecraft.world.item.ArmorItem.Type.LEGGINGS, props),
            new Item.Properties().stacksTo(1).durability(
                    net.minecraft.world.item.ArmorItem.Type.LEGGINGS.getDurability(GearMaterialInit.ARMOR_DURABILITY))
    );

    public static final DeferredItem<Item> GEAR_BOTAS = ITEMS.registerItem(
            "gear_botas",
            props -> new es.boffmedia.teras.dungeon.gear.GearArmorItem(GearMaterialInit.dungeon(),
                    net.minecraft.world.item.ArmorItem.Type.BOOTS, props),
            new Item.Properties().stacksTo(1).durability(
                    net.minecraft.world.item.ArmorItem.Type.BOOTS.getDurability(GearMaterialInit.ARMOR_DURABILITY))
    );

    /**
     * The charm. Worn in a Curios slot where Curios is installed, and in the offhand where it is
     * not — Curios is a soft dependency for the same reason AW is: a server that wants Teras
     * without dungeons should not have to install it.
     */
    public static final DeferredItem<Item> GEAR_AMULETO = ITEMS.registerItem(
            "gear_amuleto",
            Item::new,
            new Item.Properties().stacksTo(1)
    );

    /**
     * Every gadget, on one item — the flare, the charge, the flask, the hook. Which one a stack is
     * stays the gear id component, exactly as it does for swords and helmets; what is different is
     * that this one has a use.
     */
    public static final DeferredItem<Item> GEAR_ARTILUGIO = ITEMS.registerItem(
            "gear_artilugio",
            es.boffmedia.teras.dungeon.gear.GadgetItem::new,
            new Item.Properties().stacksTo(1)
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
