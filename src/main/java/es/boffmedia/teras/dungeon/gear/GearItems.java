package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.init.ItemInit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which registered item carries each {@link GearKind}, and the conversion of anything that predates
 * them.
 *
 * <h2>One item per kind, not per piece</h2>
 *
 * <p>Which piece a stack <i>is</i> stays the {@code teras:gear_id} component. That is what keeps the
 * catalog config-defined: a new piece is an entry in {@code gear.json}, not a registered item plus
 * assets plus a code change. The cost is that a config-only piece is grey until somebody authors its
 * Armourer's Workshop skin — mechanically complete, visually unfinished.</p>
 *
 * <h2>Why gear left vanilla items</h2>
 *
 * <p>A piece used to be a real diamond chestplate, and therefore a diamond chestplate to anvils,
 * enchanting, recipes and every other mod on the server. Gear leaves the dungeon and is kept
 * forever, so that leaked into the whole item economy.</p>
 *
 * <p>The change is <b>stat-neutral</b>, which is not obvious and is worth writing down:
 * {@code GearStamp} writes an {@code ItemAttributeModifiers} component, and that component
 * <b>replaces</b> the item's defaults rather than adding to them. A {@code coraza_abisal} on a
 * diamond chestplate always gave its own 6 armour and never diamond's 8. Moving to a zero-stat
 * material changes no number anyone has ever seen.</p>
 */
public final class GearItems {
    private GearItems() {}

    /** The item a piece of this kind is built on. */
    public static Item of(GearKind kind) {
        return switch (kind) {
            case SWORD -> ItemInit.ARMA_ESPADA.get();
            case AXE -> ItemInit.ARMA_HACHA.get();
            case SHIELD -> ItemInit.ARMA_ESCUDO.get();
            case HELMET -> ItemInit.GEAR_YELMO.get();
            case CHESTPLATE -> ItemInit.GEAR_CORAZA.get();
            case LEGGINGS -> ItemInit.GEAR_GREBAS.get();
            case BOOTS -> ItemInit.GEAR_BOTAS.get();
            case CHARM -> ItemInit.GEAR_AMULETO.get();
            case GADGET -> ItemInit.GEAR_ARTILUGIO.get();
        };
    }

    /** Whether this stack is already on the right first-party item for what it claims to be. */
    public static boolean isCurrent(ItemStack stack, GearDef def) {
        return def != null && stack.is(of(def.kind()));
    }

    /**
     * A fresh stack of {@code def} on its first-party item, carrying the id and freshly stamped.
     * The one place a piece of gear is created, so loot, commands and migration cannot disagree
     * about what a new piece looks like.
     */
    public static ItemStack create(GearDef def) {
        ItemStack stack = new ItemStack(of(def.kind()));
        stack.set(es.boffmedia.teras.init.ComponentInit.GEAR_ID.get(), def.id());
        return GearStamp.decorate(stack);
    }

    /**
     * Rebuilds a legacy stack — gear riding on a vanilla base — onto its first-party item,
     * preserving nothing but the identity, because everything else is derived from the catalog
     * anyway.
     *
     * <p>Returns the same stack when there is nothing to do, so callers can run it over a whole
     * inventory without checking first.</p>
     *
     * <p>Migration is not optional. Gear persists across runs and lives in players' inventories
     * forever; without this, every piece already handed out becomes junk vanilla armour the moment
     * the update lands, and gear is the long-term reason to run dungeons at all.</p>
     */
    public static ItemStack migrate(ItemStack stack) {
        GearDef def = GearHolder.defOf(stack);
        if (def == null || isCurrent(stack, def)) {
            return stack;
        }
        ItemStack migrated = create(def);
        migrated.setCount(Math.max(1, stack.getCount()));
        return migrated;
    }
}
