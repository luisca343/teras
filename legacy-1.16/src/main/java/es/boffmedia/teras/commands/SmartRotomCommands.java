package es.boffmedia.teras.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.Commands;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.items.SmartRotom;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Teras.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SmartRotomCommands {

    private static final Map<String, Integer> VARIANT_MAP = new HashMap<>();

    static {
        // Map variant names to CustomModelData integers. Add more variants here.
        VARIANT_MAP.put("default", 0);
        VARIANT_MAP.put("sprigatito", 1);
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("smartrotom")
                        .then(Commands.literal("setvariant")
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .executes(ctx -> setVariant(ctx.getSource(), StringArgumentType.getString(ctx, "variant")))))
        );
    }

    private static int setVariant(CommandSource source, String variantName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command must be run by a player."));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        ItemStack stack = player.getMainHandItem();

        if (stack.isEmpty() || !(stack.getItem() instanceof SmartRotom)) {
            source.sendFailure(new StringTextComponent("Hold a SmartRotom in your main hand."));
            return 0;
        }

        Integer id = VARIANT_MAP.get(variantName.toLowerCase());
        if (id == null) {
            source.sendFailure(new StringTextComponent("Unknown variant '" + variantName + "'."));
            return 0;
        }

        CompoundNBT tag = stack.getOrCreateTag();
        if (id == 0) {
            // Remove custom model data for default
            tag.remove("CustomModelData");
        } else {
            tag.putInt("CustomModelData", id);
        }
        stack.setTag(tag);

        source.sendSuccess(new StringTextComponent("Set SmartRotom variant to '" + variantName + "' (id=" + id + ")"), true);
        return 1;
    }
}
