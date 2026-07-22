package es.boffmedia.teras.init;

import es.boffmedia.teras.Teras;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * The armour material and tool tier every piece of dungeon gear is built on. <b>Diamond, mirrored.</b>
 *
 * <h2>Why these are not zeros</h2>
 *
 * <p>An earlier version made every number zero, reasoning that {@code GearStamp} replaces a stack's
 * {@code ItemAttributeModifiers} anyway — which is true of <i>armour and damage</i>, and true of
 * nothing else. A tier and a material carry four things the component does not touch:</p>
 *
 * <ul>
 *   <li><b>durability</b>, which is the item's, not the component's;</li>
 *   <li><b>enchantment value</b>, which decides what an enchanting table offers;</li>
 *   <li><b>the repair ingredient</b>, which decides whether an anvil will mend it;</li>
 *   <li><b>the correct-tool tag</b>, which decides what the weapon can mine for drops.</li>
 * </ul>
 *
 * <p>Zeroing them made dungeon gear un-enchantable, un-repairable and paper-thin — behaviour no
 * vanilla equivalent has. Diamond is the reference because it is what most pieces already rode on
 * (the exceptions were chainmail and gold, either side of it), and because one item per kind means
 * one profile has to serve every piece of that kind.</p>
 *
 * <p>The defense and damage numbers here still only ever apply to an <b>unstamped</b> stack — one
 * conjured in creative without a {@code gear_id}. A real piece's numbers are {@code gear.json}'s,
 * which is why moving off vanilla bases remains stat-neutral.</p>
 */
public final class GearMaterialInit {
    private GearMaterialInit() {}

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Teras.MOD_ID);

    /**
     * Diamond's, so the enchanting table offers what it would on any diamond piece.
     *
     * <p>Set this to 0 to make dungeon gear un-enchantable without changing anything else — the
     * item tags decide <i>which</i> enchantments may apply, this decides whether a table offers any.
     * It is a balance lever, deliberately in one place.</p>
     */
    public static final int ENCHANTMENT_VALUE = 10;

    /** Diamond's durability multiplier, which {@code ArmorItem.Type.getDurability} scales per slot. */
    public static final int ARMOR_DURABILITY = 33;


    /**
     * One grey layer, shared by all four armour pieces.
     *
     * <p>The layer texture is the honest appearance of a piece with no Armourer's Workshop skin
     * configured. AW is a soft dependency on purpose — a server that installs Teras for regions or
     * starbank and does not want dungeons should not have to install a skinning mod because a
     * system it never uses has armour in it — so "grey" has to be a real state the mod supports,
     * not a placeholder.</p>
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DUNGEON =
            ARMOR_MATERIALS.register("dungeon", () -> new ArmorMaterial(
                    // Diamond's protection, for an unstamped piece and for nothing else.
                    Map.of(ArmorItem.Type.HELMET, 3,
                            ArmorItem.Type.CHESTPLATE, 8,
                            ArmorItem.Type.LEGGINGS, 6,
                            ArmorItem.Type.BOOTS, 3,
                            ArmorItem.Type.BODY, 11),
                    ENCHANTMENT_VALUE,
                    SoundEvents.ARMOR_EQUIP_DIAMOND,
                    () -> Ingredient.of(net.minecraft.world.item.Items.DIAMOND),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon"))),
                    2.0f,
                    0.0f));

    public static Holder<ArmorMaterial> dungeon() {
        return DUNGEON;
    }

    /**
     * The tier the weapons carry — diamond's, so a dungeon sword mines, breaks, enchants and mends
     * like the diamond sword it used to be.
     *
     * <p>{@code TieredItem} reads durability, enchantment value and the repair ingredient straight
     * off this, and none of the three is something {@code GearStamp} can supply.</p>
     */
    public static final Tier TIER = new Tier() {
        @Override
        public int getUses() {
            return 1561;
        }

        @Override
        public float getSpeed() {
            return 8.0f;
        }

        @Override
        public float getAttackDamageBonus() {
            return 3.0f;
        }

        @Override
        public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
            return net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }

        @Override
        public int getEnchantmentValue() {
            return ENCHANTMENT_VALUE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(net.minecraft.world.item.Items.DIAMOND);
        }
    };
}
