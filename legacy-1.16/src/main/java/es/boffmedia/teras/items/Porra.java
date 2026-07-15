package es.boffmedia.teras.items;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import es.boffmedia.teras.util.media.TerasSoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class Porra extends Item {
    private final Multimap<Attribute, AttributeModifier> defaultModifiers;

    public Porra(Properties properties) {

        super(properties);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", 1.6D, AttributeModifier.Operation.ADDITION));

        this.defaultModifiers = builder.build();
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, PlayerEntity player, Entity entity) {
        player.level.playSound(player, player.getX(), player.getY(), player.getZ(), TerasSoundEvents.BONK.get(), player.getSoundSource(), 1.0F, 1.0F);

        return super.onLeftClickEntity(stack, player, entity);
    }
}
