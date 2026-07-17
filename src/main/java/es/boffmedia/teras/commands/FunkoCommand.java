package es.boffmedia.teras.commands;

import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.ComponentInit;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

/**
 * {@code /funko player <name>} and {@code /funko skin <file>} hand the player a funko whose skin is
 * stored in data components &mdash; the same mechanism vanilla now uses for
 * {@code player_head[profile=...]}. Placing it transfers the skin to the block.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class FunkoCommand {
    private FunkoCommand() {}

    /**
     * {@code ResolvableProfile}'s codecs cap the name at 16 characters ({@code ExtraCodecs.PLAYER_NAME}
     * on disk, {@code stringUtf8(16)} on the wire). Rejecting here keeps a bad name from becoming an
     * {@code EncoderException} when the stack syncs.
     */
    private static final SimpleCommandExceptionType INVALID_NAME =
            new SimpleCommandExceptionType(Component.literal("Nombre de jugador inválido (máx. 16 caracteres)"));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("funko")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("player")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FunkoCommand::givePlayerFunko)))
                .then(Commands.literal("skin")
                        .then(Commands.argument("file", StringArgumentType.greedyString())
                                .executes(FunkoCommand::giveFileFunko))));
    }

    /**
     * Resolves before handing the item over: the client cannot resolve, and
     * {@code FunkoItem#verifyComponentsAfterLoad} would not fire until the item was reloaded, so
     * without this the funko renders as Steve until then.
     */
    private static int givePlayerFunko(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (!StringUtil.isValidPlayerName(name)) {
            throw INVALID_NAME.create();
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResolvableProfile profile = new ResolvableProfile(Optional.of(name), Optional.empty(), new PropertyMap());
        profile.resolve().thenAcceptAsync(resolved -> {
            ItemStack stack = new ItemStack(ItemInit.FUNKO.get());
            stack.set(DataComponents.PROFILE, resolved);
            give(context, player, stack, "Funko de §f" + name);
        }, SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR);
        return 1;
    }

    private static int giveFileFunko(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String file = StringArgumentType.getString(context, "file").trim();
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = new ItemStack(ItemInit.FUNKO.get());
        stack.set(ComponentInit.FUNKO_SKIN_FILE.get(), file);
        return give(context, player, stack, "Funko con skin §f" + file);
    }

    private static int give(CommandContext<CommandSourceStack> context, ServerPlayer player, ItemStack stack,
                            String description) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        context.getSource().sendSuccess(
                () -> Component.literal(description).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
