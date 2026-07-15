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
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;

import java.util.function.Supplier;

public class CombateCommand {

    /** Op level required to start a combat for an arbitrary target player (triggers remote config fetches). */
    private static final int PERMISSION_LEVEL = 2;

    public CombateCommand(CommandDispatcher<CommandSource> dispatcher){
        dispatcher.register(Commands.literal("combate")
                .requires(src -> src.hasPermission(PERMISSION_LEVEL))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("npc", StringArgumentType.string())
                                .executes(this::iniciarCombate)
                        )));

        dispatcher.register(Commands.literal("encuentro")
                .requires(src -> src.hasPermission(PERMISSION_LEVEL))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("evento", StringArgumentType.string())
                                .executes(this::iniciarEncuentro)
                        )));

        dispatcher.register(Commands.literal("combatemulti")
                .requires(src -> src.hasPermission(PERMISSION_LEVEL))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("companero", StringArgumentType.string())
                        .then(Commands.argument("enemigo1", StringArgumentType.string())
                        .then(Commands.argument("enemigo2", StringArgumentType.string())
                            .executes(this::iniciarCombateMulti)
                        )))));
    }

    private int iniciarCombateMulti(CommandContext<CommandSource> command) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");
        CommandSource source = command.getSource();
        String companero = StringArgumentType.getString(command, "companero");
        String enemigo1 = StringArgumentType.getString(command, "enemigo1");
        String enemigo2 = StringArgumentType.getString(command, "enemigo2");

        if (!validarIdentificadores(source, companero, enemigo1, enemigo2)) {
            return 0;
        }

        String label = companero + " + " + enemigo1 + " + " + enemigo2;
        return fetchAndStart(source, player, label, () -> {
            BattleConfig configCompanero = Reader.getDatosNPC(companero);
            BattleConfig configEnemigo1 = Reader.getDatosNPC(enemigo1);
            BattleConfig configEnemigo2 = Reader.getDatosNPC(enemigo2);
            if (configCompanero == null || configEnemigo1 == null || configEnemigo2 == null) {
                return null;
            }
            return () -> new NPCTerasMultiBattle(player, configCompanero, configEnemigo1, configEnemigo2).start();
        });
    }

    private int iniciarEncuentro(CommandContext<CommandSource> command) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");
        CommandSource source = command.getSource();
        String evento = StringArgumentType.getString(command, "evento");

        if (!validarIdentificadores(source, evento)) {
            return 0;
        }

        return fetchAndStart(source, player, evento, () -> {
            BattleConfig configSalvaje = Reader.getDatosEncuentro(evento);
            if (configSalvaje == null) {
                return null;
            }
            return () -> new NPCTerasBattle(player, configSalvaje).start();
        });
    }

    private int iniciarCombate(CommandContext<CommandSource> command) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");
        CommandSource source = command.getSource();
        String npc = StringArgumentType.getString(command, "npc");

        if (!validarIdentificadores(source, npc)) {
            return 0;
        }

        return fetchAndStart(source, player, npc, () -> {
            BattleConfig configCombateEntrenador = Reader.getDatosNPC(npc);
            if (configCombateEntrenador == null) {
                return null;
            }
            return () -> new NPCTerasBattle(player, configCombateEntrenador).start();
        });
    }

    /** Rejects (with feedback) any identifier that isn't safe to interpolate into the remote URL. */
    private boolean validarIdentificadores(CommandSource source, String... ids) {
        for (String id : ids) {
            if (!Reader.isValidIdentifier(id)) {
                source.sendFailure(new StringTextComponent("Identificador de combate inválido: '" + id + "'"));
                return false;
            }
        }
        return true;
    }

    /**
     * Runs the (blocking) config fetch off the server thread, then resumes battle creation on the server
     * thread. {@code fetch} performs the HTTP work and returns the start action to run on the main thread,
     * or {@code null} if the config couldn't be loaded. All outcomes report back to the command source.
     */
    private int fetchAndStart(CommandSource source, ServerPlayerEntity player, String label,
                              Supplier<Runnable> fetch) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            source.sendFailure(new StringTextComponent("No se pudo resolver el servidor del jugador."));
            return 0;
        }

        source.sendSuccess(new StringTextComponent("Cargando combate '" + label + "'…"), false);

        Teras.EXECUTOR.submit(() -> {
            Runnable startAction;
            try {
                startAction = fetch.get();
            } catch (Exception e) {
                Teras.getLogger().error("Error cargando la configuración del combate '" + label + "'", e);
                server.execute(() -> source.sendFailure(
                        new StringTextComponent("Error cargando el combate '" + label + "'.")));
                return;
            }

            if (startAction == null) {
                server.execute(() -> source.sendFailure(new StringTextComponent(
                        "No se pudo cargar la configuración del combate '" + label + "'.")));
                return;
            }

            server.execute(() -> {
                try {
                    startAction.run();
                    source.sendSuccess(new StringTextComponent("Combate '" + label + "' iniciado."), false);
                } catch (Exception e) {
                    Teras.getLogger().error("Error iniciando el combate '" + label + "'", e);
                    source.sendFailure(new StringTextComponent("Error al iniciar el combate '" + label + "'."));
                }
            });
        });

        return 1;
    }

}
