package es.boffmedia.teras.items;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.SoundInit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The whip. A melee weapon that also yanks what you are aiming at towards you.
 *
 * <p>Like {@link Porra}, the 1.16.5 original built a modifier multimap it never applied, so the whip
 * had no stats either; the numbers here are that intent, applied properly. Its commented-out reach
 * bonus is deliberately <b>not</b> revived — reach is what makes a weapon dominate PvP, and this is
 * a roleplay item.</p>
 */
public class LatigoNumeril extends Item {

    private static final double ATTACK_DAMAGE = 1.0;

    /** See {@link Porra} — 1.16.5's raw 1.6 was a delta it never applied. */
    private static final double ATTACK_SPEED = -2.4;

    /** Blocks ahead the pull looks for a target. Shorter than the taser: this one moves people. */
    private static final double PULL_RANGE = 5.0;

    /** Ticks between pulls. Long enough that the whip cannot chain-drag a fleeing player. */
    private static final int COOLDOWN_TICKS = 100;

    /** Speed of the yank, in blocks/tick, applied along the target→player line. */
    private static final double PULL_SPEED = 1.0;

    public LatigoNumeril(Properties properties) {
        super(properties.attributes(modifiers()));
    }

    private static ItemAttributeModifiers modifiers() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "latigo/damage"),
                                ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "latigo/speed"),
                                ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        crack(player);
        return super.onLeftClickEntity(stack, player, entity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        crack(player);

        // Server-only past this point: the pull writes velocity, and a client that guessed at it
        // would be corrected a tick later by the server's own value.
        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }

        Entity target = ItemRayTrace.entity(player, PULL_RANGE, LatigoNumeril::pullable);
        if (target == null) {
            return InteractionResultHolder.pass(held);
        }

        pull(player, target);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(held);
    }

    /** Living things and loose items. Anything else (a boat, a frame) is left where it is. */
    private static boolean pullable(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof ItemEntity;
    }

    /** Throws {@code target} at the whip's holder, aimed at their chest rather than their feet. */
    private static void pull(Player player, Entity target) {
        Vec3 chest = player.position().add(0, player.getEyeHeight() / 2.0, 0);
        Vec3 impulse = chest.subtract(target.position()).normalize().scale(PULL_SPEED);
        target.setDeltaMovement(impulse);
        // Without this the server keeps the new velocity to itself and a player target never moves;
        // see the dungeon hook gadget, which learned the same lesson.
        target.hurtMarked = true;
        target.resetFallDistance();
        if (target instanceof ServerPlayer pulled) {
            pulled.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(pulled));
        }
    }

    private static void crack(Player player) {
        player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundInit.LATIGO_NUMERIL.get(), player.getSoundSource(), 1.0F, 1.0F);
    }
}
