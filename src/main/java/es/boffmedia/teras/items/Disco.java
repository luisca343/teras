package es.boffmedia.teras.items;

import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A record. Carries the name of a track in the server's library; the
 * {@link es.boffmedia.teras.blocks.Tocadiscos} plays it.
 *
 * <p>Blank until {@code /disco crear} stamps it. That was true in 1.16.5 too, but for a worse
 * reason: the command's two NBT-writing lines were commented out, so <b>nothing</b> could stamp
 * one and the tooltip below was the only code that ever read the key.</p>
 */
public class Disco extends Item {

    public Disco(Properties properties) {
        super(properties);
    }

    /** The track on {@code stack}, or {@code null} for a blank disc. */
    public static String trackOf(ItemStack stack) {
        return stack.get(ComponentInit.DISCO_TRACK.get());
    }

    public static void setTrack(ItemStack stack, String track) {
        stack.set(ComponentInit.DISCO_TRACK.get(), track);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String track = trackOf(stack);
        tooltip.add(track == null
                ? Component.translatable("item.teras.disco.blank").withStyle(ChatFormatting.GRAY)
                : Component.literal(track).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
