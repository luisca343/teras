package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.init.ItemInit;
import es.boffmedia.teras.tileentity.FunkoTE;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /funko player <name>} and {@code /funko skin <file>} hand the player a funko whose
 * skin is stored in {@code BlockEntityTag} NBT &mdash; the same mechanism vanilla uses for
 * {@code player_head{SkullOwner:...}}. Placing it transfers the skin to the block.
 */
public class FunkoCommand {

    public FunkoCommand(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("funko")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("player")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::givePlayerFunko)))
                .then(Commands.literal("skin")
                        .then(Commands.argument("file", StringArgumentType.greedyString())
                                .executes(this::giveFileFunko))));
    }

    private int givePlayerFunko(CommandContext<CommandSource> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        ItemStack stack = createFunko(FunkoTE.TAG_OWNER, name);
        return give(context, stack, "Funko de §f" + name);
    }

    private int giveFileFunko(CommandContext<CommandSource> context) throws CommandSyntaxException {
        String file = StringArgumentType.getString(context, "file").trim();
        ItemStack stack = createFunko(FunkoTE.TAG_FILE, file);
        return give(context, stack, "Funko con skin §f" + file);
    }

    private static ItemStack createFunko(String key, String value) {
        ItemStack stack = new ItemStack(ItemInit.FUNKO.get());
        CompoundNBT blockEntityTag = stack.getOrCreateTagElement("BlockEntityTag");
        blockEntityTag.putString(key, value);
        return stack;
    }

    private int give(CommandContext<CommandSource> context, ItemStack stack, String description) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();
        if (!player.inventory.add(stack)) {
            player.drop(stack, false);
        }
        context.getSource().sendSuccess(
                new StringTextComponent(description).withStyle(TextFormatting.GREEN), false);
        return 1;
    }
}
