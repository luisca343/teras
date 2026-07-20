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
import es.boffmedia.teras.dungeon.editor.RoomEditor;
import es.boffmedia.teras.dungeon.encounter.CnpcBridge;
import es.boffmedia.teras.dungeon.encounter.DungeonEnemyPacks;
import es.boffmedia.teras.dungeon.encounter.EnemyPreset;
import es.boffmedia.teras.dungeon.encounter.SpawnTables;
import es.boffmedia.teras.dungeon.entity.DungeonGeoEnemy;
import es.boffmedia.teras.dungeon.entity.GeoEnemyVariant;
import es.boffmedia.teras.init.EntityInit;
import es.boffmedia.teras.dungeon.gen.DungeonGenerationException;
import es.boffmedia.teras.dungeon.gen.DungeonGenerator;
import es.boffmedia.teras.dungeon.gen.GenConfig;
import es.boffmedia.teras.dungeon.gen.LayoutAscii;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.party.DungeonEntrance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> GEO_VARIANTS =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    GeoEnemyVariant.all().stream().map(GeoEnemyVariant::id), builder);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> ROOM_TYPES =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    RoomTemplates.knownPoolKeys(), builder);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> GEAR_IDS =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    es.boffmedia.teras.dungeon.gear.GearDefs.all().keySet(), builder);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> MARKER_KINDS =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    java.util.List.of("spawn:default", "loot:default", "boss", "trapdoor",
                            "shopslot:1", "door:n", "challenge", "sacrifice", "arcade", "deal"),
                    builder);

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
                        .then(Commands.literal("enemigos")
                                .then(Commands.literal("instalar")
                                        .executes(ctx -> installEnemies(ctx, false))
                                        .then(Commands.literal("sobrescribir")
                                                .executes(ctx -> installEnemies(ctx, true))))
                                .then(Commands.literal("listar")
                                        .executes(DungeonCommand::listEnemies))
                                .then(Commands.literal("invocar")
                                        .then(Commands.argument("variante", StringArgumentType.word())
                                                .suggests(GEO_VARIANTS)
                                                .executes(DungeonCommand::summonGeo))))
                        .then(Commands.literal("entrada")
                                .then(Commands.literal("marcar")
                                        .executes(ctx -> markEntrance(ctx, true)))
                                .then(Commands.literal("quitar")
                                        .executes(ctx -> markEntrance(ctx, false)))
                                .then(Commands.literal("iniciar")
                                        .then(Commands.argument("jugador", EntityArgument.player())
                                                .then(Commands.argument("etapa", IntegerArgumentType.integer(1, 12))
                                                        .executes(ctx -> entranceStart(ctx, false, false))
                                                        .then(Commands.argument("laberinto", BoolArgumentType.bool())
                                                                .then(Commands.argument("perdido", BoolArgumentType.bool())
                                                                        .executes(ctx -> entranceStart(ctx,
                                                                                BoolArgumentType.getBool(ctx, "laberinto"),
                                                                                BoolArgumentType.getBool(ctx, "perdido")))))))))
                        .then(Commands.literal("sala")
                                .then(Commands.literal("editar")
                                        .then(Commands.argument("tipo", StringArgumentType.word())
                                                .suggests(ROOM_TYPES)
                                                .executes(ctx -> editRoom(ctx, 0))
                                                .then(Commands.argument("variante", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> editRoom(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "variante"))))))
                                .then(Commands.literal("marcar")
                                        // greedyString: a bare string() would refuse the ':' in
                                        // "spawn:default" unless the caller remembered to quote it.
                                        .then(Commands.argument("marca", StringArgumentType.greedyString())
                                                .suggests(MARKER_KINDS)
                                                .executes(DungeonCommand::markRoom)))
                                .then(Commands.literal("guardar")
                                        .executes(ctx -> saveRoom(ctx, null))
                                        .then(Commands.argument("nombre", StringArgumentType.word())
                                                .executes(ctx -> saveRoom(ctx,
                                                        StringArgumentType.getString(ctx, "nombre")))))
                                .then(Commands.literal("salir")
                                        .executes(DungeonCommand::exitRoom)))
                        .then(Commands.literal("descartar")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(DungeonCommand::discard)))
                        .then(Commands.literal("gear")
                                .executes(DungeonCommand::gearInfo)
                                .then(Commands.literal("dar")
                                        .then(Commands.argument("pieza", StringArgumentType.word())
                                                .suggests(GEAR_IDS)
                                                .executes(DungeonCommand::gearGive))))
                        .then(Commands.literal("reload")
                                .executes(DungeonCommand::reload))));
    }

    /**
     * Hands over a fully stamped piece by catalog id. For vanilla-based gear this is the only
     * sane give path — the {@code /give} spelling is
     * {@code minecraft:diamond_sword[teras:gear_id="espada_abisal"]}, which nobody types twice.
     */
    private static int gearGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "pieza");
        es.boffmedia.teras.dungeon.gear.GearDef def = es.boffmedia.teras.dungeon.gear.GearDefs.get(id);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("No existe el equipo '" + id + "'."));
            return 0;
        }
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(es.boffmedia.teras.dungeon.gear.GearVanilla.itemFor(def));
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "El item base '" + def.baseItem() + "' no existe."));
            return 0;
        }
        stack.set(es.boffmedia.teras.init.ComponentInit.GEAR_ID.get(), def.id());
        es.boffmedia.teras.dungeon.gear.GearStamp.decorate(stack);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§aEntregado: §f" + id), false);
        return 1;
    }

    /**
     * What the held piece is actually stamped with, read back off the stack rather than the
     * catalog. The skin authoring loop runs against a remote server whose only other signal is a
     * render that silently does not happen; this splits "the component is wrong" from "the
     * component is right and AW could not load the skin".
     */
    private static int gearInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        es.boffmedia.teras.dungeon.gear.GearDef def =
                es.boffmedia.teras.dungeon.gear.GearHolder.defOf(held);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Sostén una pieza de equipo de mazmorra en la mano principal."));
            return 0;
        }
        Integer stamped = held.get(es.boffmedia.teras.init.ComponentInit.GEAR_GENERATION.get());
        String skinOnStack = es.boffmedia.teras.dungeon.gear.GearSkins.describe(held);
        ctx.getSource().sendSuccess(() -> Component.literal(String.join("\n",
                "§6Equipo: §f" + def.id(),
                "§7Generación: §f" + stamped + " §7(catálogo: "
                        + es.boffmedia.teras.dungeon.gear.GearDefs.generation() + ")",
                "§7Skin en config: §f" + (def.hasSkin()
                        ? def.skinId() + " §7(tipo " + def.effectiveSkinType() + ")" : "(sin skin)"),
                "§7Skin en el item: §f" + (skinOnStack != null ? skinOnStack : "(ninguna)"),
                "§7Armourer's Workshop: §f"
                        + (es.boffmedia.teras.dungeon.gear.GearSkins.available()
                                ? "cargado" : "NO cargado"))), false);
        return 1;
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
            DungeonRunManager.StartOutcome outcome =
                    DungeonRunManager.start(player, java.util.List.of(player), stage, curses, seed);
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
            BlockPos start = built.roomCenter(built.layout().start());
            player.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            es.boffmedia.teras.dungeon.run.RunEngine.land(player);
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

    /**
     * Writes the built-in bestiary into CustomNPCs as clones and points {@code enemies.json} at
     * it. Safe to re-run: existing clones are left alone unless {@code sobrescribir} is used, so
     * an admin's edits in the NPC editor survive.
     */
    private static int installEnemies(CommandContext<CommandSourceStack> ctx, boolean overwrite) {
        if (!CnpcBridge.available()) {
            ctx.getSource().sendFailure(Component.literal(
                    "CustomNPCs no está instalado: los enemigos con guion necesitan ese mod."));
            return 0;
        }
        int installed = CnpcBridge.install(ctx.getSource().getLevel(), DungeonEnemyPacks.TAB,
                DungeonEnemyPacks.all(), overwrite);
        SpawnTables.writeCnpcBestiary(DungeonEnemyPacks.TAB);
        SpawnTables.load();
        int total = DungeonEnemyPacks.all().size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§aBestiario instalado: " + installed + " de " + total + " enemigos escritos en la "
                        + "pestaña de clones " + DungeonEnemyPacks.TAB
                        + (installed < total && !overwrite
                                ? " (el resto ya existía — usa 'instalar sobrescribir' para rehacerlos)"
                                : "")
                        + ". enemies.json actualizado."), true);
        return installed;
    }

    /** Drops one animated enemy in front of the caller — the way to look at a model without a run. */
    private static int summonGeo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String variantId = StringArgumentType.getString(ctx, "variante");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DungeonGeoEnemy enemy = EntityInit.DUNGEON_ENEMY.get().create(player.serverLevel());
        if (enemy == null) {
            ctx.getSource().sendFailure(Component.literal("No se pudo crear la entidad."));
            return 0;
        }
        var spot = player.position().add(player.getLookAngle().scale(3).multiply(1, 0, 1));
        enemy.moveTo(spot.x, player.getY(), spot.z, player.getYRot() + 180f, 0);
        enemy.applyVariant(variantId);
        player.serverLevel().addFreshEntity(enemy);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Invocado " + enemy.variant().id() + "."), false);
        return 1;
    }

    private static int listEnemies(CommandContext<CommandSourceStack> ctx) {
        if (!CnpcBridge.available()) {
            ctx.getSource().sendFailure(Component.literal("CustomNPCs no está instalado."));
            return 0;
        }
        var installed = CnpcBridge.clonesIn(DungeonEnemyPacks.TAB);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Clones en la pestaña " + DungeonEnemyPacks.TAB + ": " + installed.size()), false);
        for (EnemyPreset preset : DungeonEnemyPacks.all()) {
            boolean present = installed.contains(preset.id());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    (present ? "§a✔ " : "§7✘ ") + preset.id() + " — " + preset.displayName()), false);
        }
        return installed.size();
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

    /**
     * Marks (or unmarks) the nearest CustomNPCs NPC as a dungeon entrance. The tag lives on the
     * entity's own NBT, so it survives restarts with the NPC.
     */
    private static int markEntrance(CommandContext<CommandSourceStack> ctx, boolean mark)
            throws CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayerOrException();
        var npc = DungeonEntrance.nearestNpc(admin, 6);
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No hay ningún NPC de CustomNPCs a menos de 6 bloques."));
            return 0;
        }
        String name = npc.getName().getString();
        if (mark) {
            if (!npc.addTag(DungeonEntrance.ENTRANCE_TAG)) {
                ctx.getSource().sendFailure(Component.literal(name + " ya es una entrada."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a" + name + " marcado como entrada de mazmorras. En su diálogo, añade una "
                            + "opción de tipo comando: /teras dungeon entrada iniciar @dp <etapa> "
                            + "(requiere bloques de comandos activados)."), false);
        } else {
            if (!npc.removeTag(DungeonEntrance.ENTRANCE_TAG)) {
                ctx.getSource().sendFailure(Component.literal(name + " no era una entrada."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7" + name + " ya no es una entrada."), false);
        }
        return 1;
    }

    /**
     * The entrance NPC's way in: its dialog runs {@code entrada iniciar @dp <etapa>} — CustomNPCs
     * executes dialog commands at permission level 2 and substitutes {@code @dp} with the talking
     * player — and the run starts for that player's whole party. Errors go to the player, not
     * (only) the console: the source here is the NPC, and the player is the one who needs to hear
     * "ya estás en una mazmorra".
     */
    private static int entranceStart(CommandContext<CommandSourceStack> ctx,
                                     boolean labyrinth, boolean lost) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
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
            player.sendSystemMessage(Component.literal("§c" + error));
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    // --- the room editor: every handler is a thin shell over RoomEditor's error-or-null API ------

    private static int editRoom(CommandContext<CommandSourceStack> ctx, int variant)
            throws CommandSyntaxException {
        return editorCall(ctx, RoomEditor.start(ctx.getSource().getPlayerOrException(),
                StringArgumentType.getString(ctx, "tipo"), variant));
    }

    private static int markRoom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return editorCall(ctx, RoomEditor.placeMarker(ctx.getSource().getPlayerOrException(),
                StringArgumentType.getString(ctx, "marca")));
    }

    private static int saveRoom(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        return editorCall(ctx, RoomEditor.save(ctx.getSource().getPlayerOrException(), name));
    }

    private static int exitRoom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return editorCall(ctx, RoomEditor.exit(ctx.getSource().getPlayerOrException()));
    }

    private static int editorCall(CommandContext<CommandSourceStack> ctx, String error) {
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        DungeonsConfig.load();
        RoomTemplates.load();
        es.boffmedia.teras.dungeon.encounter.SpawnTables.load();
        es.boffmedia.teras.dungeon.gear.GearConfig.load();
        ctx.getSource().sendSuccess(() ->
                Component.literal("Configuración de mazmorras recargada."), false);
        return 1;
    }
}
