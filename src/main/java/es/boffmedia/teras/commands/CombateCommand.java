package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.pixelmon.battle.NPCTerasBattle;
import es.boffmedia.teras.util.objects.pixelmon.BattleConfig;
import es.boffmedia.teras.pixelmon.battle.NPCTerasMultiBattle;
import es.boffmedia.teras.util.file.Reader;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;

public class CombateCommand {
    public CombateCommand(CommandDispatcher<CommandSource> dispatcher){
        dispatcher.register(Commands.literal("combate")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("url", StringArgumentType.string())
                                .executes((command) -> {
                                    return iniciarCombate(command);

                                })
                        )));

        dispatcher.register(Commands.literal("encuentro")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("url", StringArgumentType.string())
                                .executes((command) -> {
                                    return iniciarEncuentro(command);
                                })
                        )));

        dispatcher.register(Commands.literal("combatemulti")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("companero", StringArgumentType.string())
                        .then(Commands.argument("enemigo1", StringArgumentType.string())
                        .then(Commands.argument("enemigo2", StringArgumentType.string())
                            .executes((command) -> {
                                return iniciarCombateMulti(command);
                            })
                        )))));
    }

    private int iniciarCombateMulti(CommandContext<CommandSource> command) {
        try{
            ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");
            String companero = StringArgumentType.getString(command, "companero");
            String enemigo1 = StringArgumentType.getString(command, "enemigo1");
            String enemigo2 = StringArgumentType.getString(command, "enemigo2");


            BattleConfig configCompanero = Reader.getDatosNPC(companero);
            BattleConfig configEnemigo1 = Reader.getDatosNPC(enemigo1);
            BattleConfig configEnemigo2 = Reader.getDatosNPC(enemigo2);

            NPCTerasMultiBattle combate = new NPCTerasMultiBattle(player, configCompanero, configEnemigo1, configEnemigo2);
            combate.start();


        }catch(CommandSyntaxException e){
            Teras.LOGGER.error("Error al iniciar el combate");
        }

        return 1;
    }

    private int iniciarEncuentro(CommandContext<CommandSource> command) {
        try{
            ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");
            String url = StringArgumentType.getString(command, "url");
            BattleConfig configSalvaje = Reader.getDatosEncuentro(url);
            NPCTerasBattle combate = new NPCTerasBattle(player, configSalvaje);
            combate.start();
        } catch (CommandSyntaxException e){
            Teras.LOGGER.error("Error al iniciar el combate");
        }
        return 1;
    }

    public int iniciarCombate(CommandContext<CommandSource> command) {
        try{
            ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");
            String url = StringArgumentType.getString(command, "url");
            BattleConfig configCombateEntrenador = Reader.getDatosNPC(url);
            NPCTerasBattle combate = new NPCTerasBattle(player, configCombateEntrenador);
            combate.start();
        }catch(CommandSyntaxException e){
            Teras.LOGGER.error("Error al iniciar el combate");
        }

        return 1;
    }



}
