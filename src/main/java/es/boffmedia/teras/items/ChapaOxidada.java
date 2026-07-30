package es.boffmedia.teras.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The rusty cap: a bottle cap that runs backwards, dropping a chosen IV to 0 instead of raising it
 * to 31. A gag item, and a griefing one — hence "your own Pokémon only", enforced twice.
 *
 * <p>Deliberately <b>not</b> a Pixelmon {@code BottlecapItem} despite behaving like one. Extending it
 * buys nothing: {@code SelectStatPacket} gates on
 * {@code stack.getItem() == ItemRegistration.SILVER_BOTTLE_CAP.value()} — an identity check against
 * Pixelmon's own item, not an {@code instanceof} — so a subclass is rejected there regardless. The
 * 1.16.5 version extended it <i>and</i> declared its own {@code onSilverSelection}, which the packet
 * calls by {@code invokestatic} on {@code BottlecapItem} and therefore never reached: had its side
 * check been right, the cap would have set the IV to 31 and acted as a free silver cap.</p>
 *
 * <p>All Pixelmon-facing behaviour lives in {@code pixelmon.chapa} so this class stays loadable on a
 * server without Pixelmon, where it is an inert curiosity.</p>
 */
public class ChapaOxidada extends Item {

    public ChapaOxidada(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.teras.chapa_oxidada.desc").withStyle(ChatFormatting.DARK_RED));
    }
}
