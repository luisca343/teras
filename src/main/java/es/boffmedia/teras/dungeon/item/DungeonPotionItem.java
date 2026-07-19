package es.boffmedia.teras.dungeon.item;

import es.boffmedia.teras.dungeon.run.DungeonHealth;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The dungeon's healing, and — under the health lockdown ({@link DungeonHealth}) — the only healing
 * there is inside a run. It heals through {@link DungeonHealth#heal}, which writes health directly,
 * because the lockdown cancels the vanilla heal event that a food or potion item would go through.
 *
 * <p>Drinking outside a run is refused rather than silently working: these are bought with dungeon
 * coins and stripped on the way out, so one in a player's inventory in the overworld is a leak, and
 * it should look like one.</p>
 */
public class DungeonPotionItem extends Item {

    /** Half-hearts restored; {@link Float#MAX_VALUE} means "to full". */
    private final float healAmount;

    public DungeonPotionItem(Properties properties, float healAmount) {
        super(properties);
        this.healAmount = healAmount;
    }

    public boolean healsFully() {
        return healAmount == Float.MAX_VALUE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!DungeonHealth.isInRun(serverPlayer)) {
            serverPlayer.displayClientMessage(
                    Component.literal("§7Solo funciona dentro de una mazmorra."), true);
            return InteractionResultHolder.fail(stack);
        }
        if (serverPlayer.getHealth() >= serverPlayer.getMaxHealth()) {
            serverPlayer.displayClientMessage(Component.literal("§7Ya estás al máximo."), true);
            return InteractionResultHolder.fail(stack);
        }
        if (healsFully()) {
            DungeonHealth.healFully(serverPlayer);
        } else {
            DungeonHealth.heal(serverPlayer, healAmount);
        }
        stack.consume(1, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1.2f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal(healsFully()
                ? "§aCura toda la vida" : "§aCura " + (int) (healAmount / 2) + " corazones"));
        tooltip.add(Component.literal("§8Solo dentro de una mazmorra"));
    }
}
