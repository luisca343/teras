package es.boffmedia.teras.commands;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import es.boffmedia.teras.util.objects._old.karts.Circuito;
import es.boffmedia.teras.util.data.Scoreboard;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

import java.util.UUID;

public class ObjectiveCommand {
    private Circuito circuito;

    public ObjectiveCommand(CommandDispatcher<CommandSource> dispatcher){
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("objetivo")
                .then(sumar())
                .then(restar())
                .then(set())
                .then(read());


        dispatcher.register(literalBuilder);

    }

    private ArgumentBuilder<CommandSource,?> sumar() {
        return Commands.literal("sumar")
                .then(Commands.argument("jugador", EntityArgument.player())
                .then(Commands.argument("objetivo", StringArgumentType.string())
                .then(Commands.argument("valor", IntegerArgumentType.integer())
                .executes((command) -> {
                     Entity entity = command.getSource().getEntity();
                     if (entity instanceof ServerPlayerEntity) {
                     ServerPlayerEntity player = (ServerPlayerEntity) entity;
                     ServerPlayerEntity target = EntityArgument.getPlayer(command, "jugador");
                     String objective = StringArgumentType.getString(command, "objetivo");
                     int value = IntegerArgumentType.getInteger(command, "valor");
                     player.sendMessage(new StringTextComponent("Sumando..."), UUID.randomUUID());
                     Scoreboard.add(target, objective, value);
                     return 1;
                }
                    return 0;
        }))));
    }

    private ArgumentBuilder<CommandSource,?> restar() {
        return Commands.literal("restar")
                .then(Commands.argument("jugador", EntityArgument.player())
                .then(Commands.argument("objetivo", StringArgumentType.string())
                .then(Commands.argument("valor", IntegerArgumentType.integer())
                .executes((command) -> {
                     Entity entity = command.getSource().getEntity();
                     if (entity instanceof ServerPlayerEntity) {
                     ServerPlayerEntity player = (ServerPlayerEntity) entity;
                     ServerPlayerEntity target = EntityArgument.getPlayer(command, "jugador");
                     String objective = StringArgumentType.getString(command, "objetivo");
                     int value = IntegerArgumentType.getInteger(command, "valor");
                     player.sendMessage(new StringTextComponent("Restando..."), UUID.randomUUID());
                     Scoreboard.remove(target, objective, value);
                     return 1;
                }
                    return 0;
        }))));
    }

    private ArgumentBuilder<CommandSource,?> set() {
        return Commands.literal("set")
                .then(Commands.argument("jugador", EntityArgument.player())
                .then(Commands.argument("objetivo", StringArgumentType.string())
                .then(Commands.argument("valor", IntegerArgumentType.integer())
                .executes((command) -> {
                     Entity entity = command.getSource().getEntity();
                     if (entity instanceof ServerPlayerEntity) {
                     ServerPlayerEntity player = (ServerPlayerEntity) entity;
                     ServerPlayerEntity target = EntityArgument.getPlayer(command, "jugador");
                     String objective = StringArgumentType.getString(command, "objetivo");
                     int value = IntegerArgumentType.getInteger(command, "valor");
                     player.sendMessage(new StringTextComponent("Estableciendo..."), UUID.randomUUID());
                     Scoreboard.set(target, objective, value);
                     return 1;
                }
                    return 0;
        }))));
    }

    private ArgumentBuilder<CommandSource,?> read() {
        return Commands.literal("leer")
                .then(Commands.argument("jugador", EntityArgument.player())
                .then(Commands.argument("objetivo", StringArgumentType.string())
                .executes((command) -> {
                     Entity entity = command.getSource().getEntity();
                     if (entity instanceof ServerPlayerEntity) {
                     ServerPlayerEntity player = (ServerPlayerEntity) entity;
                     ServerPlayerEntity target = EntityArgument.getPlayer(command, "jugador");
                     String objective = StringArgumentType.getString(command, "objetivo");
                     player.sendMessage(new StringTextComponent("El valor de " + objective + " es " + Scoreboard.get(target, objective)), UUID.randomUUID());
                     return 1;
                }
                    return 0;
        })));
    }

}

