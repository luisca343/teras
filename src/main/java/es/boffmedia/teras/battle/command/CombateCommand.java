package es.boffmedia.teras.battle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.api.BattleProvider;
import es.boffmedia.teras.battle.api.BattleProviders;
import es.boffmedia.teras.battle.config.BattleConfig;
import es.boffmedia.teras.battle.config.BattleConfigLoader;
import es.boffmedia.teras.util.net.HttpText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.function.Supplier;

/**
 * Combat commands over the {@link BattleProvider} abstraction:
 * <ul>
 *   <li>{@code /combate <player> <npc>} — trainer battle</li>
 *   <li>{@code /encuentro <player> <evento>} — wild encounter</li>
 *   <li>{@code /combatemulti <player> <companero> <enemigo1> <enemigo2>} — 2v2 multi</li>
 * </ul>
 * The config/team load runs on {@link Teras#EXECUTOR}; the battle start is bounced back to the server thread.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class CombateCommand {
    private CombateCommand() {}

    /** Op level required to start a combat (triggers remote config fetches for an arbitrary target). */
    private static final int PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("combate")
                .requires(src -> src.hasPermission(PERMISSION_LEVEL))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("npc", StringArgumentType.string())
                                .executes(CombateCommand::iniciarCombate))));

        d.register(Commands.literal("encuentro")
                .requires(src -> src.hasPermission(PERMISSION_LEVEL))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("evento", StringArgumentType.string())
                                .executes(CombateCommand::iniciarEncuentro))));

        d.register(Commands.literal("combatemulti")
                .requires(src -> src.hasPermission(PERMISSION_LEVEL))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("companero", StringArgumentType.string())
                                .then(Commands.argument("enemigo1", StringArgumentType.string())
                                        .then(Commands.argument("enemigo2", StringArgumentType.string())
                                                .executes(CombateCommand::iniciarCombateMulti))))));
    }

    private static int iniciarCombate(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(command, "player");
        CommandSourceStack source = command.getSource();
        String npc = StringArgumentType.getString(command, "npc");
        if (!validarIdentificadores(source, npc)) {
            return 0;
        }
        return fetchAndStart(source, player, npc, provider -> {
            BattleConfig config = BattleConfigLoader.loadTrainer(npc);
            if (config == null) return null;
            return () -> provider.startConfigBattle(player, config);
        });
    }

    private static int iniciarEncuentro(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(command, "player");
        CommandSourceStack source = command.getSource();
        String evento = StringArgumentType.getString(command, "evento");
        if (!validarIdentificadores(source, evento)) {
            return 0;
        }
        return fetchAndStart(source, player, evento, provider -> {
            BattleConfig config = BattleConfigLoader.loadEvent(evento);
            if (config == null) return null;
            return () -> provider.startConfigBattle(player, config);
        });
    }

    private static int iniciarCombateMulti(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(command, "player");
        CommandSourceStack source = command.getSource();
        String companero = StringArgumentType.getString(command, "companero");
        String enemigo1 = StringArgumentType.getString(command, "enemigo1");
        String enemigo2 = StringArgumentType.getString(command, "enemigo2");
        if (!validarIdentificadores(source, companero, enemigo1, enemigo2)) {
            return 0;
        }
        String label = companero + " + " + enemigo1 + " + " + enemigo2;
        return fetchAndStart(source, player, label, provider -> {
            BattleConfig cCompanero = BattleConfigLoader.loadTrainer(companero);
            BattleConfig cEnemigo1 = BattleConfigLoader.loadTrainer(enemigo1);
            BattleConfig cEnemigo2 = BattleConfigLoader.loadTrainer(enemigo2);
            if (cCompanero == null || cEnemigo1 == null || cEnemigo2 == null) return null;
            return () -> provider.startMultiBattle(player, cCompanero, cEnemigo1, cEnemigo2);
        });
    }

    /** Rejects (with feedback) any identifier that isn't safe to interpolate into the remote URL. */
    private static boolean validarIdentificadores(CommandSourceStack source, String... ids) {
        for (String id : ids) {
            if (!HttpText.isValidIdentifier(id)) {
                source.sendFailure(Component.literal("Identificador de combate inválido: '" + id + "'"));
                return false;
            }
        }
        return true;
    }

    /** Resolves a start action from a loaded config; {@code null} means the config couldn't be loaded. */
    @FunctionalInterface
    private interface StartResolver {
        Runnable resolve(BattleProvider provider);
    }

    /**
     * Runs the (blocking) config fetch off the server thread, then resumes the battle start on the
     * server thread. {@code resolver} performs the load and returns the start action, or {@code null}
     * if the config couldn't be loaded.
     */
    private static int fetchAndStart(CommandSourceStack source, ServerPlayer player, String label,
                                     StartResolver resolver) {
        BattleProvider provider = BattleProviders.get();
        if (provider == null) {
            source.sendFailure(Component.literal(
                    "No hay un motor de combate (Pixelmon/Cobblemon) instalado en el servidor."));
            return 0;
        }
        MinecraftServer server = source.getServer();

        source.sendSuccess(() -> Component.literal("Cargando combate '" + label + "'…"), false);

        Teras.EXECUTOR.submit(() -> {
            Runnable startAction;
            try {
                startAction = resolver.resolve(provider);
            } catch (Exception e) {
                Teras.LOGGER.error("Error cargando la configuración del combate '{}'", label, e);
                server.execute(() -> source.sendFailure(
                        Component.literal("Error cargando el combate '" + label + "'.")));
                return;
            }

            if (startAction == null) {
                server.execute(() -> source.sendFailure(Component.literal(
                        "No se pudo cargar la configuración del combate '" + label + "'.")));
                return;
            }

            server.execute(() -> {
                try {
                    startAction.run();
                    source.sendSuccess(() -> Component.literal("Combate '" + label + "' iniciado."), false);
                } catch (Exception e) {
                    Teras.LOGGER.error("Error iniciando el combate '{}'", label, e);
                    source.sendFailure(Component.literal("Error al iniciar el combate '" + label + "'."));
                }
            });
        });

        return 1;
    }
}
