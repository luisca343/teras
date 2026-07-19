package es.boffmedia.teras.dungeon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.build.DungeonMaterializer;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.build.RoomTemplates;
import es.boffmedia.teras.dungeon.gen.DungeonGenerationException;
import es.boffmedia.teras.dungeon.gen.DungeonGenerator;
import es.boffmedia.teras.dungeon.gen.GenConfig;
import es.boffmedia.teras.dungeon.gen.LayoutAscii;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * The {@code /teras dungeon} admin tree — generation and materialization for testing floors and
 * authoring content. Replaces the legacy {@code /generardungeon}, which was registered without any
 * permission gate, pasted at the caller's feet with no record, and had no way to undo.
 *
 * <p>The player-facing way into a dungeon run is deliberately not a command; it arrives with the
 * run stage (DUNGEONS.md §10).</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonCommand {
    private DungeonCommand() {}

    private static final int PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("teras")
                .then(Commands.literal("dungeon")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.literal("generar")
                                .then(stageTree(Mode.BUILD_HERE)))
                        .then(Commands.literal("preview")
                                .then(stageTree(Mode.PREVIEW)))
                        .then(Commands.literal("iniciar")
                                .then(stageTree(Mode.INSTANCE)))
                        .then(Commands.literal("terminar")
                                .then(Commands.argument("run", IntegerArgumentType.integer(1))
                                        .executes(DungeonCommand::endRun)))
                        .then(Commands.literal("lista")
                                .executes(DungeonCommand::list))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("run", IntegerArgumentType.integer(1))
                                        .executes(DungeonCommand::debug)))
                        .then(Commands.literal("descartar")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(DungeonCommand::discard)))
                        .then(Commands.literal("reload")
                                .executes(DungeonCommand::reload))));
    }

    private enum Mode { PREVIEW, BUILD_HERE, INSTANCE }

    /** {@code <stage> [labyrinth] [lost] [seed]} — the legacy argument shape, kept. */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> stageTree(Mode mode) {
        return Commands.argument("stage", IntegerArgumentType.integer(1, 12))
                .executes(ctx -> run(ctx, mode, false, false, null))
                .then(Commands.argument("labyrinth", BoolArgumentType.bool())
                        .then(Commands.argument("lost", BoolArgumentType.bool())
                                .executes(ctx -> run(ctx, mode,
                                        BoolArgumentType.getBool(ctx, "labyrinth"),
                                        BoolArgumentType.getBool(ctx, "lost"), null))
                                .then(Commands.argument("seed", StringArgumentType.string())
                                        .executes(ctx -> run(ctx, mode,
                                                BoolArgumentType.getBool(ctx, "labyrinth"),
                                                BoolArgumentType.getBool(ctx, "lost"),
                                                StringArgumentType.getString(ctx, "seed"))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Mode mode,
                           boolean labyrinth, boolean lost, String seed) throws CommandSyntaxException {
        int stage = IntegerArgumentType.getInteger(ctx, "stage");
        Set<Curse> curses = EnumSet.noneOf(Curse.class);
        if (labyrinth) {
            curses.add(Curse.LABYRINTH);
        }
        if (lost) {
            curses.add(Curse.LOST);
        }

        if (mode == Mode.INSTANCE) {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            DungeonRunManager.StartOutcome outcome = DungeonRunManager.start(player, stage, curses, seed);
            if (outcome.error() != null) {
                ctx.getSource().sendFailure(Component.literal(outcome.error()));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Preparando instancia " + outcome.run().id() + " (etapa " + stage + ")…"), false);
            return 1;
        }

        DungeonLayout layout;
        try {
            layout = DungeonGenerator.generate(GenConfig.defaults(), stage, curses, seed);
        } catch (DungeonGenerationException e) {
            ctx.getSource().sendFailure(Component.literal("Generación fallida: " + e.getMessage()));
            return 0;
        }

        for (String warning : layout.warnings()) {
            ctx.getSource().sendSystemMessage(Component.literal("§eAviso: " + warning));
        }
        if (mode == Mode.PREVIEW) {
            ctx.getSource().sendSuccess(() -> Component.literal(LayoutAscii.render(layout)), false);
            return 1;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int id = DungeonMaterializer.enqueueBuild(level, layout, origin, built -> {
            BlockPos start = built.anchorCenter(built.layout().start());
            player.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal(
                    "§aMazmorra " + built.id() + " construida: " + built.layout().rooms().size()
                            + " salas, semilla " + built.layout().seedString()));
            player.sendSystemMessage(Component.literal(LayoutAscii.render(built.layout())));
        });
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Construyendo mazmorra " + id + " (" + layout.rooms().size() + " salas)…"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var built = DungeonMaterializer.built();
        var runs = DungeonRunManager.runs();
        if (built.isEmpty() && runs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No hay mazmorras construidas."), false);
            return 0;
        }
        for (BuiltDungeon dungeon : built) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "#" + dungeon.id() + " — etapa " + dungeon.layout().stage()
                            + ", semilla " + dungeon.layout().seedString()
                            + ", " + dungeon.dimension().location()
                            + " @ " + dungeon.origin().toShortString()), false);
        }
        for (DungeonRun run : runs) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Instancia " + run.id() + " — etapa " + run.stage()
                            + ", " + run.state() + ", slot " + run.slot()
                            + ", " + run.party().size() + " jugador(es)"), false);
        }
        return built.size() + runs.size();
    }

    private static int debug(CommandContext<CommandSourceStack> ctx) {
        int runId = IntegerArgumentType.getInteger(ctx, "run");
        var lines = es.boffmedia.teras.dungeon.run.RunEngine.describe(runId);
        if (lines.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "La instancia " + runId + " no tiene piso activo."));
            return 0;
        }
        for (String line : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int endRun(CommandContext<CommandSourceStack> ctx) {
        int runId = IntegerArgumentType.getInteger(ctx, "run");
        if (!DungeonRunManager.end(ctx.getSource().getServer(), runId)) {
            ctx.getSource().sendFailure(Component.literal(
                    "No existe la instancia " + runId + " (o aún se está construyendo)."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Instancia " + runId + " terminada; retirando el piso…"), false);
        return 1;
    }

    private static int discard(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        BuiltDungeon built = DungeonMaterializer.get(id);
        if (built == null) {
            ctx.getSource().sendFailure(Component.literal("No existe la mazmorra " + id + "."));
            return 0;
        }
        ServerLevel level = ctx.getSource().getServer().getLevel(built.dimension());
        if (level == null || !DungeonMaterializer.enqueueDiscard(id, level, () ->
                ctx.getSource().sendSuccess(() ->
                        Component.literal("§aMazmorra " + id + " retirada."), false))) {
            ctx.getSource().sendFailure(Component.literal("No se pudo retirar la mazmorra " + id + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Retirando mazmorra " + id + "…"), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        DungeonsConfig.load();
        RoomTemplates.load();
        es.boffmedia.teras.dungeon.encounter.SpawnTables.load();
        ctx.getSource().sendSuccess(() ->
                Component.literal("Configuración de mazmorras recargada."), false);
        return 1;
    }
}
