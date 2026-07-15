package es.boffmedia.teras.items;

import es.boffmedia.teras.client.TerasClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * SmartRotom — opens the in-game MCEF browser.
 *
 * <p>First-slice behaviour: right-click opens the SmartRotom screen. The 1.16.5 Pixelmon
 * dex-scan path ({@code openDex(...)} via raytrace) and the shift-click screenshot path are
 * deferred until the Pixelmon 1.21.1 dependency and the screenshot handler are wired in
 * ({@code TerasMCEF.runJS(...)} is already available for the Java->JS calls they need).</p>
 */
public class SmartRotom extends Item {
    public SmartRotom(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            TerasClient.openSmartRotom();
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
