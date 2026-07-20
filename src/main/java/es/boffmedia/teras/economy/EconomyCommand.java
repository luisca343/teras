package es.boffmedia.teras.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * {@code /teras economia} — admin tools for the starbank cache.
 *
 * <ul>
 *   <li>{@code recargar [players]} — re-read balances from starbank. The escape hatch when a login
 *       fetch failed: those players are left unknown and cannot spend until it succeeds
 *       (see {@link EconomyStore}), and without this their only recourse is to reconnect.</li>
 *   <li>{@code ver [players]} — show what the cache currently holds, including whether it is loaded
 *       at all, which is the distinction the balance alone can't show.</li>
 * </ul>
 *
 * <p>Admin-only (permission level 2, matching {@code /npcs}). Pixelmon-free: it operates on the cache,
 * which exists whether or not Pixelmon is installed.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class EconomyCommand {
    private EconomyCommand() {}

    private static final int PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("teras")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("economia")
                        .then(Commands.literal("recargar")
                                .executes(ctx -> reload(ctx, self(ctx)))
                                .then(Commands.argument("jugadores", EntityArgument.players())
                                        .executes(ctx -> reload(ctx,
                                                EntityArgument.getPlayers(ctx, "jugadores")))))
                        .then(Commands.literal("ver")
                                .executes(ctx -> show(ctx, self(ctx)))
                                .then(Commands.argument("jugadores", EntityArgument.players())
                                        .executes(ctx -> show(ctx,
                                                EntityArgument.getPlayers(ctx, "jugadores")))))));
    }

    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return java.util.List.of(ctx.getSource().getPlayerOrException());
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            EconomyStore.load(player.getUUID());
        }
        int count = players.size();
        // The fetch is async, so this reports that it was started, not that it worked — the result
        // lands in the log, and `ver` shows whether the balance actually arrived.
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Recarga de saldo solicitada para " + count + " jugador(es); "
                        + "usa /teras economia ver para comprobar el resultado"), true);
        return count;
    }

    private static int show(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            String name = player.getGameProfile().getName();
            String line = EconomyStore.isLoaded(player.getUUID())
                    ? name + ": " + EconomyStore.get(player.getUUID())
                    : name + ": sin cargar (no puede gastar; starbank no respondió)";
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return players.size();
    }
}
