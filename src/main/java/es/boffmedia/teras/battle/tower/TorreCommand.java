package es.boffmedia.teras.battle.tower;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /frentebatalla iniciar|continuar|pausar} — Battle Tower commands (player self-service). */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class TorreCommand {
    private TorreCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("frentebatalla")
                .then(Commands.literal("iniciar")
                        .then(Commands.argument("modalidad", StringArgumentType.string())
                                .executes(TorreCommand::iniciar)))
                .then(Commands.literal("continuar").executes(TorreCommand::continuar))
                .then(Commands.literal("pausar").executes(TorreCommand::pausar)));
    }

    private static int iniciar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BattleTower.iniciar(player, StringArgumentType.getString(ctx, "modalidad"));
        return 1;
    }

    private static int continuar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BattleTower.continuar(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int pausar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BattleTower.pausar(ctx.getSource().getPlayerOrException());
        return 1;
    }
}
