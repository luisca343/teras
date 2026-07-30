package es.boffmedia.teras.items;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.SoundInit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The baton. A roleplay weapon: it hits barely harder than a fist and exists for the sound it makes.
 *
 * <p>The 1.16.5 original built a {@code defaultModifiers} multimap in its constructor and never
 * overrode {@code getAttributeModifiers}, so the field was written and read nowhere — the porra had
 * <b>no stats at all</b>. The numbers below are that dead field's intent, applied for the first time
 * through the 1.21 {@link DataComponents#ATTRIBUTE_MODIFIERS} component.</p>
 */
public class Porra extends Item {

    /** Added to the 1.0 base, so a hit lands for 2 — a nudge above an empty hand, not a weapon. */
    private static final double ATTACK_DAMAGE = 1.0;

    /**
     * Attack speed is expressed as a delta from the 4.0 base, so 1.16.5's raw {@code 1.6} would have
     * meant 5.6 attacks/second. It read as "fast" there only because it never applied; -2.4 is the
     * vanilla sword cadence and is what the item was reaching for.
     */
    private static final double ATTACK_SPEED = -2.4;

    public Porra(Properties properties) {
        super(properties.attributes(modifiers()));
    }

    private static ItemAttributeModifiers modifiers() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "porra/damage"),
                                ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "porra/speed"),
                                ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        // Positioned on the player, not the target: the bonk is the swing, and it must be heard even
        // when the hit is later cancelled (a protected region, a dungeon gate). The player is the
        // excluded listener, not the source — this runs on both sides, so the server broadcasts to
        // everyone else and the swinger's own client plays it locally.
        player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundInit.BONK.get(), player.getSoundSource(), 1.0F, 1.0F);
        return super.onLeftClickEntity(stack, player, entity);
    }
}
