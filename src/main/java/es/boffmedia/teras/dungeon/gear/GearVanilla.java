package es.boffmedia.teras.dungeon.gear;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Binds the plain-Java catalog to vanilla's attributes, slots and armour types. */
public final class GearVanilla {
    private GearVanilla() {}

    public static Holder<Attribute> attribute(GearStat stat) {
        return switch (stat) {
            case ATTACK_DAMAGE -> Attributes.ATTACK_DAMAGE;
            case ATTACK_SPEED -> Attributes.ATTACK_SPEED;
            case ARMOR -> Attributes.ARMOR;
            case ARMOR_TOUGHNESS -> Attributes.ARMOR_TOUGHNESS;
            case MOVEMENT_SPEED -> Attributes.MOVEMENT_SPEED;
            case MAX_HEALTH -> Attributes.MAX_HEALTH;
        };
    }

    public static AttributeModifier.Operation operation(GearOp op) {
        return op == GearOp.FRACTION_OF_BASE
                ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                : AttributeModifier.Operation.ADD_VALUE;
    }

    /** Where a piece's stats apply from — the slot it has to be in to count. */
    public static EquipmentSlotGroup slot(GearKind kind) {
        return switch (kind) {
            case SWORD -> EquipmentSlotGroup.MAINHAND;
            case HELMET -> EquipmentSlotGroup.HEAD;
            case CHESTPLATE -> EquipmentSlotGroup.CHEST;
            case LEGGINGS -> EquipmentSlotGroup.LEGS;
            case BOOTS -> EquipmentSlotGroup.FEET;
            case CHARM -> EquipmentSlotGroup.OFFHAND;
        };
    }

    public static net.minecraft.world.item.Rarity rarity(GearDef.Rarity rarity) {
        return switch (rarity) {
            case COMUN -> net.minecraft.world.item.Rarity.UNCOMMON;
            case RARO -> net.minecraft.world.item.Rarity.RARE;
            case EPICO -> net.minecraft.world.item.Rarity.EPIC;
        };
    }

    /** The vanilla base a fresh copy of {@code def} is made of; air when the id is unknown. */
    public static net.minecraft.world.item.Item itemFor(GearDef def) {
        if (!def.hasBaseItem()) {
            return net.minecraft.world.item.Items.AIR;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(def.baseItem()));
    }
}
