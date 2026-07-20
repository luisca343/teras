package es.boffmedia.teras.dungeon.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.party.DungeonEntrance;
import es.boffmedia.teras.dungeon.party.DungeonParty;
import es.boffmedia.teras.dungeon.party.PartyManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The player-facing {@code /teras mazmorra} tree — no permission gate, because none of it opens a
 * door on its own: {@code entrar} only works standing next to a marked entrance NPC
 * ({@link DungeonEntrance}), and the rest is party small talk. The admin surface stays under
 * {@code /teras dungeon}.
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class MazmorraCommand {
    private MazmorraCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("teras")
                .then(Commands.literal("mazmorra")
                        .then(Commands.literal("invitar")
                                .then(Commands.argument("jugador", EntityArgument.player())
                                        .executes(MazmorraCommand::invite)))
                        .then(Commands.literal("aceptar")
                                .executes(MazmorraCommand::accept))
                        .then(Commands.literal("grupo")
                                .executes(MazmorraCommand::info))
                        .then(Commands.literal("salir")
                                .executes(MazmorraCommand::leave))
                        .then(Commands.literal("entrar")
                                .then(Commands.argument("etapa", IntegerArgumentType.integer(1, 12))
                                        .executes(ctx -> enter(ctx, false, false))
                                        .then(Commands.argument("laberinto", BoolArgumentType.bool())
                                                .then(Commands.argument("perdido", BoolArgumentType.bool())
                                                        .executes(ctx -> enter(ctx,
                                                                BoolArgumentType.getBool(ctx, "laberinto"),
                                                                BoolArgumentType.getBool(ctx, "perdido")))))))));
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
        String error = PartyManager.invite(leader, target);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§aInvitación enviada a " + target.getName().getString() + "."), false);
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String error = PartyManager.accept(player);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var run = DungeonRunManager.runOf(player.getUUID());
        if (run != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7En mazmorra — etapa " + run.stage() + ", "
                            + run.party().size() + " jugador(es)."), false);
            return 1;
        }
        DungeonParty party = PartyManager.partyOf(player.getUUID());
        if (party == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7No estás en ningún grupo. Invita con /teras mazmorra invitar <jugador>."), false);
            return 0;
        }
        StringBuilder names = new StringBuilder();
        for (UUID member : party.members()) {
            ServerPlayer online = player.getServer().getPlayerList().getPlayer(member);
            if (!names.isEmpty()) {
                names.append("§7, ");
            }
            names.append(member.equals(party.leader()) ? "§6" : "§f")
                    .append(online == null ? "(desconectado)" : online.getName().getString());
        }
        String line = "§7Grupo (" + party.size() + "): " + names;
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** Context-aware: inside a run it walks out of the run; outside, it leaves the lobby. */
    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (DungeonRunManager.leaveRun(player)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7Has salido de la mazmorra."), false);
            return 1;
        }
        String error = PartyManager.leave(player);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§7Has salido del grupo."), false);
        return 1;
    }

    private static int enter(CommandContext<CommandSourceStack> ctx, boolean labyrinth, boolean lost)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Set<Curse> curses = EnumSet.noneOf(Curse.class);
        if (labyrinth) {
            curses.add(Curse.LABYRINTH);
        }
        if (lost) {
            curses.add(Curse.LOST);
        }
        String error = DungeonEntrance.enter(player,
                IntegerArgumentType.getInteger(ctx, "etapa"), curses);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }
}
