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

    /**
     * Where a piece's stats apply from — the slot it has to be in to count.
     *
     * <p>{@code CHARM} answers {@code OFFHAND} here, which is right only when Curios is absent. A
     * charm in a Curios slot is not in any {@link EquipmentSlotGroup} at all, and its modifiers come
     * from {@code ICurio#getAttributeModifiers} instead — see {@code GearCurios}.</p>
     */
    public static EquipmentSlotGroup slot(GearKind kind) {
        return switch (kind) {
            case SWORD, AXE -> EquipmentSlotGroup.MAINHAND;
            case SHIELD -> EquipmentSlotGroup.OFFHAND;
            case HELMET -> EquipmentSlotGroup.HEAD;
            case CHESTPLATE -> EquipmentSlotGroup.CHEST;
            case LEGGINGS -> EquipmentSlotGroup.LEGS;
            case BOOTS -> EquipmentSlotGroup.FEET;
            case CHARM -> EquipmentSlotGroup.OFFHAND;
            // A gadget is used from whichever hand holds it, and carries no stat line to apply from
            // a slot in the first place. MAINHAND is the honest answer to "where does it count":
            // nowhere, and the hand is where it is.
            case GADGET -> EquipmentSlotGroup.MAINHAND;
        };
    }

    public static net.minecraft.world.item.Rarity rarity(GearDef.Rarity rarity) {
        return switch (rarity) {
            case COMUN -> net.minecraft.world.item.Rarity.UNCOMMON;
            case RARO -> net.minecraft.world.item.Rarity.RARE;
            case EPICO -> net.minecraft.world.item.Rarity.EPIC;
        };
    }

}
