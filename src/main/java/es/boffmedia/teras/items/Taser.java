package es.boffmedia.teras.items;

import es.boffmedia.teras.init.SoundInit;
import es.boffmedia.teras.util.game.TerasDamageTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The taser. Fires down the player's aim, deals a token point of damage and slows what it hits.
 *
 * <p>A crowd-control roleplay tool, not a weapon: the damage exists so the target flinches and knows
 * it happened, and the slowness is the actual effect. The 1.16.5 version is preserved as-is apart
 * from its damage source, which is now a datapack {@link TerasDamageTypes#TASER} entry.</p>
 */
public class Taser extends Item {

    private static final double RANGE = 7.0;
    private static final float DAMAGE = 1.0F;
    private static final int SLOWNESS_TICKS = 100;
    private static final int SLOWNESS_AMPLIFIER = 2;
    private static final int COOLDOWN_TICKS = 100;

    public Taser(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundInit.TASER.get(), player.getSoundSource(), 1.0F, 1.0F);

        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }

        Entity hit = ItemRayTrace.entity(player, RANGE, entity -> entity instanceof LivingEntity);
        if (hit instanceof LivingEntity target) {
            target.hurt(TerasDamageTypes.source(level, TerasDamageTypes.TASER, player), DAMAGE);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    SLOWNESS_TICKS, SLOWNESS_AMPLIFIER));
        }

        // Charged on use, not on hit: a taser that costs nothing when it misses is a free scanner.
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(held);
    }
}
