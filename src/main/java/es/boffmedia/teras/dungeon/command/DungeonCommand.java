package es.boffmedia.teras.dungeon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DungeonCommand {

    /** Fixed-width font for the floor printout — see {@link #mapa}. */
    private static final net.minecraft.resources.ResourceLocation MAP_FONT =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    es.boffmedia.teras.Teras.MOD_ID, "mapa");
    private DungeonCommand() {}

    private static final int PERMISSION_LEVEL = 2;

    /**
     * Everything {@code invocar} can drop: the first-party animated variants and the installed CNPC
     * clones. Both are offered because a clone (the slime, the swarm) is exactly the thing you cannot
     * see without a run otherwise, and {@code listar} only says whether it is installed.
     */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SUMMONABLE =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    java.util.stream.Stream.concat(
                            GeoEnemyVariant.all().stream().map(GeoEnemyVariant::id),
                            DungeonEnemyPacks.all().stream().map(EnemyPreset::id)), builder);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> ROOM_TYPES =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    RoomTemplates.knownPoolKeys(), builder);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> PISOS =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    es.boffmedia.teras.dungeon.piso.PisoCatalog.declared().keySet(), builder);

    /**
     * The variant names of the room key and piso already typed on the line — what makes editing,
     * reweighting or deleting a specific room a matter of tabbing rather than running {@code sala
     * listar} first and counting. It reads {@code tipo} and {@code piso} back out of the partial
     * parse, which is why every command that uses it puts the piso <b>before</b> the variant: a
     * variant only means anything once its piso is known, and the folder that answers "which
     * variants exist" is per piso.
     */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> VARIANTS =
            (ctx, builder) -> {
                String tipo = argOrNull(ctx, "tipo");
                if (tipo == null) {
                    return builder.buildFuture();
                }
                String pisoId = argOrNull(ctx, "piso");
                if (pisoId == null || pisoId.isBlank()) {
                    pisoId = es.boffmedia.teras.dungeon.piso.PisoCatalog.defaultPisoId();
                }
                es.boffmedia.teras.dungeon.piso.FloorDef piso = pisoId == null ? null
                        : es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(pisoId);
                if (piso == null) {
                    return builder.buildFuture();
                }
                // declared, not the enabled pool: a variant switched off with peso 0 is exactly the
                // one you need to name to turn back on, or to edit before re-enabling.
                return net.minecraft.commands.SharedSuggestionProvider.suggest(
                        RoomTemplates.pool(piso, tipo).stream()
                                .map(RoomTemplates.TemplateEntry::name), builder);
            };

    /** A parsed string argument, or null when the node is not on the line yet. */
    private static String argOrNull(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> GEAR_IDS =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    es.boffmedia.teras.dungeon.gear.GearDefs.all().keySet(), builder);

    /**
     * Completions for {@code sala marcar}: the contract's vocabulary, plus the qualified forms of
     * the kinds that take one. Derived rather than listed, so a new marker cannot exist in the
     * editor and be missing from the suggestions.
     */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> MARKER_KINDS =
            (ctx, builder) -> {
                java.util.List<String> kinds = new java.util.ArrayList<>(
                        es.boffmedia.teras.dungeon.piso.MarkerContract.vocabulary());
                java.util.Collections.sort(kinds);
                kinds.addAll(java.util.List.of("spawn:ranged", "shopslot:1", "door:n",
                        "decoracion:techo", "decoracion:suelo", "decoracion:pared"));
                return net.minecraft.commands.SharedSuggestionProvider.suggest(kinds, builder);
            };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("teras")
                // Player-facing and ungated on purpose: it is the target of the clickable lines in
                // El Acreedor's offer, so an ordinary party member must be able to run it. It is
                // safe to expose because DungeonNpcs only honours it while that player has a live
                // offer open and is still standing at him — typing it otherwise does nothing.
                // Takes the player explicitly because CustomNPCs runs a dialogue option's command
                // from its OWN sender, not from the player — so `@dp` (which CNPC expands to the
                // player's name) is how the choice carries who made it. A player running it for
                // someone else is refused below; CNPC's own sender is not a player and is allowed.
                .then(Commands.literal("trato")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("opcion", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer target =
                                                    EntityArgument.getPlayer(ctx, "jugador");
                                            ServerPlayer caller = ctx.getSource().getPlayer();
                                            if (caller != null && caller != target) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "No puedes cerrar el trato de otro jugador."));
                                                return 0;
                                            }
                                            return es.boffmedia.teras.dungeon.run.DungeonNpcs.choose(
                                                    target, StringArgumentType.getString(ctx, "opcion"))
                                                    ? 1 : 0;
                                        }))))
                // La Orden's half of the fork, on the same terms as the deal above: ungated so a
                // chat line or an authored dialogue option can reach it, and authorised by standing
                // at her rather than by a permission level.
                .then(Commands.literal("gracia")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("don", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer target =
                                                    EntityArgument.getPlayer(ctx, "jugador");
                                            ServerPlayer caller = ctx.getSource().getPlayer();
                                            if (caller != null && caller != target) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "No puedes aceptar la gracia de otro jugador."));
                                                return 0;
                                            }
                                            return es.boffmedia.teras.dungeon.run.DungeonNpcs
                                                    .chooseGracia(target,
                                                            StringArgumentType.getString(ctx, "don"))
                                                    ? 1 : 0;
                                        }))))
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
                        .then(Commands.literal("personajes")
                                .then(Commands.literal("instalar")
                                        .executes(ctx -> installCharacters(ctx, false))
                                        .then(Commands.literal("sobrescribir")
                                                .executes(ctx -> installCharacters(ctx, true)))))
                        .then(Commands.literal("enemigos")
                                .then(Commands.literal("instalar")
                                        .executes(ctx -> installEnemies(ctx, false))
                                        .then(Commands.literal("sobrescribir")
                                                .executes(ctx -> installEnemies(ctx, true))))
                                .then(Commands.literal("listar")
                                        .executes(DungeonCommand::listEnemies))
                                .then(Commands.literal("auditar")
                                        .executes(DungeonCommand::auditBestiary))
                                .then(Commands.literal("invocar")
                                        .then(Commands.argument("variante", StringArgumentType.word())
                                                .suggests(SUMMONABLE)
                                                .executes(DungeonCommand::summonGeo))))
                        .then(Commands.literal("entrada")
                                .then(Commands.literal("listar")
                                        .then(Commands.argument("tipo", StringArgumentType.word())
                                                .suggests(ROOM_TYPES)
                                                .executes(ctx -> listRooms(ctx, null))
                                                .then(Commands.argument("piso", StringArgumentType.word())
                                                        .suggests(PISOS)
                                                        .executes(ctx -> listRooms(ctx,
                                                                StringArgumentType.getString(ctx, "piso"))))))
                                .then(Commands.literal("borrar")
                                        .then(Commands.argument("tipo", StringArgumentType.word())
                                                .suggests(ROOM_TYPES)
                                                .then(Commands.argument("variante", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> deleteRoom(ctx, null))
                                                        .then(Commands.argument("piso", StringArgumentType.word())
                                                                .suggests(PISOS)
                                                                .executes(ctx -> deleteRoom(ctx,
                                                                        StringArgumentType.getString(ctx, "piso")))))))
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
                        .then(Commands.literal("piso")
                                .then(Commands.literal("listar")
                                        .executes(DungeonCommand::listPisos))
                                .then(Commands.literal("info")
                                        .then(Commands.argument("piso", StringArgumentType.word())
                                                .suggests(PISOS)
                                                .executes(DungeonCommand::pisoInfo)))
                                .then(Commands.literal("auditar")
                                        .then(Commands.argument("piso", StringArgumentType.word())
                                                .suggests(PISOS)
                                                .executes(DungeonCommand::auditPiso)))
                                .then(Commands.literal("limpiar")
                                        .then(Commands.argument("piso", StringArgumentType.word())
                                                .suggests(PISOS)
                                                .executes(DungeonCommand::purgePiso)))
                                .then(Commands.literal("migrar")
                                        .then(Commands.argument("piso", StringArgumentType.word())
                                                .suggests(PISOS)
                                                .executes(DungeonCommand::migratePiso)))
                                .then(Commands.literal("resync")
                                        .then(Commands.argument("piso", StringArgumentType.word())
                                                .suggests(PISOS)
                                                .executes(DungeonCommand::resyncPiso)))
                                .then(Commands.literal("crear")
                                        .then(Commands.argument("nuevo", StringArgumentType.word())
                                                .then(Commands.literal("desde")
                                                        .then(Commands.argument("origen", StringArgumentType.word())
                                                                .suggests(PISOS)
                                                                .executes(ctx -> createPiso(ctx, false))
                                                                .then(Commands.literal("rehacer")
                                                                        .executes(ctx -> createPiso(ctx, true))))))))
                        .then(Commands.literal("sala")
                                // Order is <tipo> [piso] <variante> so the variant autocompletes: a
                                // variant only means something once its piso is known. Naming a
                                // specific variant therefore requires naming its piso, which is the
                                // safety 'always name the piso' the guide already asks for anyway.
                                .then(Commands.literal("editar")
                                        .then(Commands.argument("tipo", StringArgumentType.word())
                                                .suggests(ROOM_TYPES)
                                                .executes(ctx -> editRoom(ctx, null, null))
                                                .then(Commands.argument("piso", StringArgumentType.word())
                                                        .suggests(PISOS)
                                                        .executes(ctx -> editRoom(ctx, null,
                                                                StringArgumentType.getString(ctx, "piso")))
                                                        .then(Commands.argument("variante", StringArgumentType.word())
                                                                .suggests(VARIANTS)
                                                                .executes(ctx -> editRoom(ctx,
                                                                        StringArgumentType.getString(ctx, "variante"),
                                                                        StringArgumentType.getString(ctx, "piso")))))))
                                .then(Commands.literal("listar")
                                        .then(Commands.argument("tipo", StringArgumentType.word())
                                                .suggests(ROOM_TYPES)
                                                .executes(ctx -> listRooms(ctx, null))
                                                .then(Commands.argument("piso", StringArgumentType.word())
                                                        .suggests(PISOS)
                                                        .executes(ctx -> listRooms(ctx,
                                                                StringArgumentType.getString(ctx, "piso"))))))
                                .then(Commands.literal("borrar")
                                        .then(Commands.argument("tipo", StringArgumentType.word())
                                                .suggests(ROOM_TYPES)
                                                .then(Commands.argument("piso", StringArgumentType.word())
                                                        .suggests(PISOS)
                                                        .then(Commands.argument("variante", StringArgumentType.word())
                                                                .suggests(VARIANTS)
                                                                .executes(ctx -> deleteRoom(ctx,
                                                                        StringArgumentType.getString(ctx, "piso")))))))
                                .then(Commands.literal("peso")
                                        .then(Commands.argument("tipo", StringArgumentType.word())
                                                .suggests(ROOM_TYPES)
                                                .then(Commands.argument("piso", StringArgumentType.word())
                                                        .suggests(PISOS)
                                                        .then(Commands.argument("variante", StringArgumentType.word())
                                                                .suggests(VARIANTS)
                                                                .then(Commands.argument("peso", DoubleArgumentType.doubleArg(0))
                                                                        .executes(ctx -> weighRoom(ctx,
                                                                                StringArgumentType.getString(ctx, "piso"))))))))
                                .then(Commands.literal("marcar")
                                        // greedyString: a bare string() would refuse the ':' in
                                        // "spawn:default" unless the caller remembered to quote it.
                                        .then(Commands.argument("marca", StringArgumentType.greedyString())
                                                .suggests(MARKER_KINDS)
                                                .executes(DungeonCommand::markRoom)))
                                .then(Commands.literal("guardar")
                                        .executes(ctx -> saveRoom(ctx, null, null))
                                        .then(Commands.argument("nombre", StringArgumentType.word())
                                                .executes(ctx -> saveRoom(ctx,
                                                        StringArgumentType.getString(ctx, "nombre"), null))
                                                .then(Commands.argument("piso", StringArgumentType.word())
                                                        .suggests(PISOS)
                                                        .executes(ctx -> saveRoom(ctx,
                                                                StringArgumentType.getString(ctx, "nombre"),
                                                                StringArgumentType.getString(ctx, "piso"))))))
                                .then(Commands.literal("salir")
                                        .executes(DungeonCommand::exitRoom)))
                        .then(Commands.literal("descartar")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(DungeonCommand::discard)))
                        .then(Commands.literal("purgar")
                                .then(Commands.literal("todos")
                                        .executes(ctx -> purge(ctx, -1)))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                        .executes(ctx -> purge(ctx,
                                                IntegerArgumentType.getInteger(ctx, "slot")))))
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
        // GearItems.create, not the vanilla base: this is the one place an admin makes a piece by
        // hand, and it handing out a legacy stack that migration then rebuilds would be a command
        // that quietly disagrees with every other way gear enters the world.
        net.minecraft.world.item.ItemStack stack =
                es.boffmedia.teras.dungeon.gear.GearItems.create(def);
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
                    DungeonRunManager.start(player, java.util.List.of(player),
                            es.boffmedia.teras.dungeon.piso.PisoCatalog.defaultDungeonId(),
                            stage, curses, seed);
            if (outcome.error() != null) {
                ctx.getSource().sendFailure(Component.literal(outcome.error()));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Preparando instancia " + outcome.run().id() + " (etapa " + stage + ")…"), false);
            return 1;
        }

        es.boffmedia.teras.dungeon.piso.DungeonDef dungeon =
                es.boffmedia.teras.dungeon.piso.PisoCatalog.dungeon(
                        es.boffmedia.teras.dungeon.piso.PisoCatalog.defaultDungeonId());
        if (dungeon == null || !dungeon.isValidStage(stage)) {
            ctx.getSource().sendFailure(Component.literal(
                    "No hay una mazmorra con un piso " + stage + " — revisa mazmorras.json."));
            return 0;
        }
        String genSeed = (seed == null || seed.isBlank())
                ? Long.toUnsignedString(java.util.concurrent.ThreadLocalRandom.current().nextLong(), 36)
                : seed;
        es.boffmedia.teras.dungeon.piso.FloorPlan plan =
                es.boffmedia.teras.dungeon.piso.FloorSelector.select(dungeon,
                        es.boffmedia.teras.dungeon.piso.PisoCatalog.pisos(), stage, genSeed,
                        DungeonsConfig.curseChances());
        if (plan == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Ningún piso utilizable para el piso " + stage + " — revisa el log."));
            return 0;
        }
        java.util.Set<Curse> floorCurses = java.util.EnumSet.noneOf(Curse.class);
        floorCurses.addAll(plan.curses());
        floorCurses.addAll(curses);

        DungeonLayout layout;
        try {
            layout = DungeonGenerator.generate(es.boffmedia.teras.dungeon.build.DungeonsConfig.genConfig()
                            .withShapeWeights(plan.piso().pesoFormas())
                            .withExitRoom(es.boffmedia.teras.dungeon.build.RoomTemplates
                                    .hasExitRoom(plan.piso()))
                            .withForceBossQuad(plan.piso().shapes()
                                    .contains(es.boffmedia.teras.dungeon.model.RoomShape.QUAD)),
                    es.boffmedia.teras.dungeon.gen.FloorDepth.ofStage(
                            es.boffmedia.teras.dungeon.build.DungeonsConfig.genConfig(), dungeon.primerPiso(), stage),
                    floorCurses, plan.piso().shapes(), genSeed);
        } catch (DungeonGenerationException e) {
            ctx.getSource().sendFailure(Component.literal("Generación fallida: " + e.getMessage()));
            return 0;
        }
        ctx.getSource().sendSystemMessage(Component.literal(
                "§7Piso: §f" + plan.title() + " §7(" + plan.piso().id() + ")"));
        // 'generar' materialises a floor without starting a run, so nothing registers it with the
        // run engine: no doors seal, no waves spawn, and nests never hatch. Worth saying, because
        // "I built a floor and the mechanic did nothing" is otherwise a mystery.
        ctx.getSource().sendSystemMessage(Component.literal(
                "§8Esto solo construye el piso. Sin run no hay combate, ni puertas, ni nidos — "
                        + "usa 'iniciar' para jugarlo."));

        for (String warning : layout.warnings()) {
            ctx.getSource().sendSystemMessage(Component.literal("§eAviso: " + warning));
        }
        if (mode == Mode.PREVIEW) {
            ctx.getSource().sendSuccess(() -> mapa(LayoutAscii.render(layout)), false);
            return 1;
        }

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int id = DungeonMaterializer.enqueueBuild(level, layout, plan, origin, built -> {
            BlockPos start = built.roomCenter(built.layout().start());
            player.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            es.boffmedia.teras.dungeon.run.RunEngine.land(player);
            player.sendSystemMessage(Component.literal(
                    "§aMazmorra " + built.id() + " construida: " + built.layout().rooms().size()
                            + " salas, semilla " + built.layout().seedString()));
            player.sendSystemMessage(mapa(LayoutAscii.render(built.layout())));
        });
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Construyendo mazmorra " + id + " (" + layout.rooms().size() + " salas)…"), false);
        return 1;
    }

    /**
     * The floor printout, in a font whose columns are columns.
     *
     * <p>No stock font is fixed-width: Minecraft derives a glyph's advance from its <i>ink</i>
     * ({@code UnihexProvider.Glyph.getAdvance()} is {@code width / 2 + 1} over the columns that
     * actually have pixels), so against {@code minecraft:uniform} every glyph this map uses
     * advances 4px except {@code █}, which reaches column 0 and advances 5, and {@code !}, which is
     * one column wide and advances 1. A pixel per cell is enough to walk a room out from under its
     * own column by the far side of the grid, which is how a connected boss comes to read as
     * sealed. {@code assets/teras/font/mapa.json} pins every advance with {@code size_overrides}
     * instead of trusting the ink.</p>
     */
    private static Component mapa(String printout) {
        return Component.literal(printout).withStyle(style -> style.withFont(MAP_FONT));
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
                    "#" + dungeon.id() + " — etapa " + dungeon.layout().floor()
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
     * Writes the dungeon's <i>characters</i> — El Acreedor and, later, la Orden — into CustomNPCs
     * as clones. Same contract as the bestiary: re-runnable, and an admin's edits survive unless
     * {@code sobrescribir} is used. Without this the rooms still work off their pedestals; what is
     * missing is the person standing at them.
     */
    private static int installCharacters(CommandContext<CommandSourceStack> ctx, boolean overwrite) {
        if (!CnpcBridge.available()) {
            ctx.getSource().sendFailure(Component.literal(
                    "CustomNPCs no está instalado: los personajes de la mazmorra necesitan ese mod."));
            return 0;
        }
        int installed = CnpcBridge.installCharacters(ctx.getSource().getLevel(),
                es.boffmedia.teras.dungeon.run.DungeonNpcs.TAB,
                es.boffmedia.teras.dungeon.run.DungeonNpcs.SHIPPED, overwrite);
        int total = es.boffmedia.teras.dungeon.run.DungeonNpcs.SHIPPED.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§aPersonajes instalados: " + installed + " de " + total + " en la pestaña de clones "
                        + es.boffmedia.teras.dungeon.run.DungeonNpcs.TAB
                        + (installed < total && !overwrite
                                ? " (el resto ya existía — usa 'instalar sobrescribir' para rehacerlos)"
                                : "")), true);
        return installed;
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

    /**
     * Drops one enemy in front of the caller — the way to look at a model without a run. Handles
     * both kinds: a first-party {@link GeoEnemyVariant} spawns as the animated entity, and anything
     * else is looked up as an installed CNPC clone in the bestiary tab. That second path is the
     * point of the change — a slime or a swarm clone is exactly what you cannot see otherwise, and
     * {@code listar} only tells you whether it is installed, not what it looks like.
     */
    private static int summonGeo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String variantId = StringArgumentType.getString(ctx, "variante");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var spot = player.position().add(player.getLookAngle().scale(3).multiply(1, 0, 1));

        if (!GeoEnemyVariant.exists(variantId)) {
            // Not one of ours — try it as a CNPC clone so `invocar limo_cueva` works.
            if (!CnpcBridge.available()) {
                ctx.getSource().sendFailure(Component.literal(
                        "'" + variantId + "' no es un enemigo propio y CustomNPCs no está instalado."));
                return 0;
            }
            var clone = CnpcBridge.spawnClone(player.serverLevel(), spot.x, player.getY(), spot.z,
                    DungeonEnemyPacks.TAB, variantId);
            if (clone == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "No hay un clon '" + variantId + "' en la pestaña " + DungeonEnemyPacks.TAB
                                + ". ¿Has ejecutado 'enemigos instalar'? Míralo con 'enemigos listar'."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Invocado el clon " + variantId + "."), false);
            return 1;
        }

        DungeonGeoEnemy enemy = EntityInit.DUNGEON_ENEMY.get().create(player.serverLevel());
        if (enemy == null) {
            ctx.getSource().sendFailure(Component.literal("No se pudo crear la entidad."));
            return 0;
        }
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

    /**
     * The bestiary held to {@code BestiaryAudit}, in game. The same rules run at build time, so a
     * clean report here is expected rather than informative — what it is for is a server whose
     * config or resources differ from the jar the tests ran against.
     */
    private static int auditBestiary(CommandContext<CommandSourceStack> ctx) {
        var findings = es.boffmedia.teras.dungeon.entity.BestiaryAudit.auditAll();
        var variants = es.boffmedia.teras.dungeon.entity.GeoEnemyVariant.all();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§eBestiario: " + variants.size() + " variantes, " + findings.size()
                        + " aviso(s)."), false);
        for (var variant : variants) {
            String behaviours = variant.behaviours().isEmpty() ? "—"
                    : variant.behaviours().stream().map(Enum::name)
                            .collect(java.util.stream.Collectors.joining(", "));
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7" + variant.id() + " §8[" + variant.movement() + "] §f" + behaviours
                            + " §8x" + variant.scale()), false);
        }
        for (var finding : findings) {
            boolean error = finding.level()
                    == es.boffmedia.teras.dungeon.entity.BestiaryAudit.Level.ERROR;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    (error ? "§c✖ " : "§6! ") + finding.variant() + ": " + finding.message()), false);
        }
        return findings.size();
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
     * Air-fills run pads outright, whether or not anything is known to be on them.
     *
     * <p>The bottom of the teardown stack. Above it a run ends and sweeps its own floor, a deserted
     * or failed one is swept by the watchdog, a clean shutdown finishes its discards, and a crash
     * leaves a journal for the boot sweep. This is for what escapes all of that: a journal that was
     * lost or hand-deleted, geometry from a version before the journal existed, an experiment left
     * on a pad. There is no record to read, so it clears the largest floor the generator can make
     * and takes whatever is there with it.</p>
     *
     * <p>Slots with a live run are refused rather than cleared — pulling the floor out from under a
     * party is not a repair. Queued a cell per tick, so purging everything is slow and harmless
     * rather than a shutdown-length freeze.</p>
     *
     * @param slot the slot to clear, or -1 for every one up to {@code maxSlots}
     */
    private static int purge(CommandContext<CommandSourceStack> ctx, int slot) {
        ServerLevel level = ctx.getSource().getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(DungeonsConfig.dimension())));
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "La dimensión " + DungeonsConfig.dimension() + " no existe."));
            return 0;
        }
        // Refused outright while anything is being played, not just for the slots in use. Jobs drain
        // one cell per tick from a single shared queue, and a full purge is tens of thousands of
        // cells: a floor queued behind it would take longer to build than the watchdog waits, and
        // healthy runs would start failing. Maintenance work waits for a quiet server.
        java.util.Set<Integer> live = new java.util.HashSet<>();
        for (var run : es.boffmedia.teras.dungeon.instance.DungeonRunManager.runs()) {
            live.add(run.slot());
        }
        if (!live.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hay " + live.size()
                    + " partida(s) en curso; purgar las bloquearía. Ciérralas primero con "
                    + "/teras dungeon terminar."));
            return 0;
        }
        int first = slot < 0 ? 0 : slot;
        int last = slot < 0 ? DungeonsConfig.maxSlots() - 1 : slot;
        int grid = DungeonsConfig.genConfig().gridSize();
        int slots = 0;
        int cells = 0;
        for (int s = first; s <= last; s++) {
            for (int pad = 0; pad < 2; pad++) {
                cells += DungeonMaterializer.enqueuePadClear(level,
                        es.boffmedia.teras.world.VoidZones.dungeonRunPad(s, pad), grid, () -> { });
            }
            slots++;
        }
        int purgedSlots = slots;
        int purgedCells = cells;
        ctx.getSource().sendSuccess(() -> Component.literal("§ePurgando " + purgedSlots
                + " slot(s), " + purgedCells + " celdas — una por tick, ~"
                + (purgedCells / 20) + "s."), true);
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

    private static int editRoom(CommandContext<CommandSourceStack> ctx, String variantName,
                                String piso) throws CommandSyntaxException {
        return editorCall(ctx, RoomEditor.start(ctx.getSource().getPlayerOrException(),
                StringArgumentType.getString(ctx, "tipo"), variantName, piso));
    }

    /** The variants of one room key, named — the handles {@code sala editar/borrar/peso} take. */
    private static int listRooms(CommandContext<CommandSourceStack> ctx, String pisoArg) {
        String type = StringArgumentType.getString(ctx, "tipo");
        String pisoId = pisoArg == null || pisoArg.isBlank()
                ? es.boffmedia.teras.dungeon.piso.PisoCatalog.defaultPisoId() : pisoArg;
        var piso = es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(pisoId);
        if (piso == null) {
            ctx.getSource().sendFailure(Component.literal("No existe el piso '" + pisoId + "'."));
            return 0;
        }
        var pool = RoomTemplates.pool(piso, type);
        double total = pool.stream().mapToDouble(RoomTemplates.TemplateEntry::weight).sum();
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + pisoId + " · §e" + type
                + " §7(" + pool.size() + ") §8dungeon/" + pisoId + "/" + type + "/"), false);
        var manager = ctx.getSource().getServer().getStructureManager();
        for (RoomTemplates.TemplateEntry entry : pool) {
            // Where it actually comes from. "mundo" is the one that matters: a copy in the world's
            // generated folder shadows the jar's, which is invisible in game and has cost days.
            String source = entry.name().indexOf('/') >= 0
                    ? "§dheredada" : (inGenerated(manager, entry.template()) ? "§bmundo" : "§8jar");
            String odds = entry.weight() <= 0 ? "§cdesactivada"
                    : "§7" + Math.round(entry.weight() / Math.max(total, 1e-9) * 100) + "%";
            // The name is the handle now, not a bracketed index — it is what the other commands take
            // and what they autocomplete.
            String line = "  §f" + entry.name() + " " + source
                    + " §7peso " + trim(entry.weight()) + " · " + odds;
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /** Whether the world's generated folder holds this template, and is therefore shadowing the jar. */
    private static boolean inGenerated(
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager,
            net.minecraft.resources.ResourceLocation id) {
        try {
            return java.nio.file.Files.exists(
                    manager.createAndValidatePathToGeneratedStructure(id, ".nbt"));
        } catch (Exception e) {
            return false;
        }
    }

    /** 1.0 rather than 1.0000000001, and 0.2 rather than 0.2000000001. */
    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * Takes a variant out of the rotation.
     *
     * <p>Two different acts behind one word, because the admin asking does not care which: a room
     * that lives in the world's {@code generated} folder is a file this server owns and is deleted,
     * while one shipped in the jar or lent by a shared set cannot be — it is weighted to 0 instead,
     * which is the same thing from the floor's point of view and is reversible with
     * {@code sala peso}.</p>
     */
    private static int deleteRoom(CommandContext<CommandSourceStack> ctx, String pisoArg) {
        String type = StringArgumentType.getString(ctx, "tipo");
        String variantName = StringArgumentType.getString(ctx, "variante");
        String pisoId = pisoArg == null || pisoArg.isBlank()
                ? es.boffmedia.teras.dungeon.piso.PisoCatalog.defaultPisoId() : pisoArg;
        var piso = es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(pisoId);
        if (piso == null) {
            ctx.getSource().sendFailure(Component.literal("No existe el piso '" + pisoId + "'."));
            return 0;
        }
        var pool = RoomTemplates.pool(piso, type);
        var entry = pool.stream().filter(e -> e.name().equals(variantName)).findFirst().orElse(null);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal("'" + variantName + "' no es una variante de '"
                    + type + "' en " + pisoId + " — 'sala listar " + type + " " + pisoId
                    + "' las lista."));
            return 0;
        }
        var manager = ctx.getSource().getServer().getStructureManager();
        boolean own = entry.name().indexOf('/') < 0;
        if (own && inGenerated(manager, entry.template())) {
            try {
                java.nio.file.Files.delete(
                        manager.createAndValidatePathToGeneratedStructure(entry.template(), ".nbt"));
                manager.remove(entry.template());
            } catch (Exception e) {
                ctx.getSource().sendFailure(Component.literal("No se pudo borrar: " + e));
                return 0;
            }
            es.boffmedia.teras.dungeon.build.RoomPools.rebuild(manager);
            es.boffmedia.teras.dungeon.piso.PisoCatalog.validateTemplates(manager);
            ctx.getSource().sendSuccess(() -> Component.literal("§a" + entry.name()
                    + " borrada de " + pisoId + "/" + type + "."), false);
            return 1;
        }
        String error = es.boffmedia.teras.dungeon.piso.PisoCatalog.setWeight(
                pisoId, type, entry.name(), 0);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        es.boffmedia.teras.dungeon.piso.PisoCatalog.validateTemplates(manager);
        ctx.getSource().sendSuccess(() -> Component.literal("§a" + entry.name()
                + " desactivada en " + pisoId + " (peso 0) §7— viene "
                + (own ? "del jar" : "de un set compartido")
                + ", así que no es un archivo de este servidor. "
                + "Para reactivarla: sala peso " + type + " " + pisoId + " " + entry.name() + " 1"), false);
        return 1;
    }

    /** Retunes one variant's odds. The only thing a piso may say about its own rooms. */
    private static int weighRoom(CommandContext<CommandSourceStack> ctx, String pisoArg) {
        String type = StringArgumentType.getString(ctx, "tipo");
        String name = StringArgumentType.getString(ctx, "variante");
        double weight = DoubleArgumentType.getDouble(ctx, "peso");
        String pisoId = pisoArg == null || pisoArg.isBlank()
                ? es.boffmedia.teras.dungeon.piso.PisoCatalog.defaultPisoId() : pisoArg;
        var piso = es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(pisoId);
        if (piso == null) {
            ctx.getSource().sendFailure(Component.literal("No existe el piso '" + pisoId + "'."));
            return 0;
        }
        boolean known = RoomTemplates.pool(piso, type).stream()
                .anyMatch(e -> e.name().equals(name));
        if (!known) {
            // A peso can never add a room, so a typo here would sit in the file doing nothing.
            ctx.getSource().sendFailure(Component.literal("'" + name + "' no está en "
                    + type + " de " + pisoId + " — 'sala listar " + type + " " + pisoId
                    + "' lista los nombres."));
            return 0;
        }
        String error = es.boffmedia.teras.dungeon.piso.PisoCatalog.setWeight(pisoId, type, name, weight);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        es.boffmedia.teras.dungeon.piso.PisoCatalog.validateTemplates(
                ctx.getSource().getServer().getStructureManager());
        ctx.getSource().sendSuccess(() -> Component.literal("§a" + type + "/" + name
                + " = " + trim(weight) + (weight == 0 ? " §7(desactivada)" : "")), false);
        return 1;
    }

    /** Moves a piso from the old flat template layout into per-key folders. Once per server. */
    private static int migratePiso(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "piso");
        String report = es.boffmedia.teras.dungeon.piso.PisoCatalog.migratePiso(
                ctx.getSource().getServer(), id);
        if (report.startsWith("No existe")) {
            ctx.getSource().sendFailure(Component.literal(report));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(id + ": " + report), false);
        return 1;
    }

    /**
     * Holds every room of a piso against the rules in DUNGEONS_PISOS.md §21.
     *
     * <p>Written for the authoring loop: an apron built shut, a spawn marker in a doorway or a room
     * with fewer spawn points than the wave it will hold all look right on the editor pad, and
     * otherwise surface mid-run as an enemy in a wall or a door that will not open. The author is
     * the last person able to see any of it cheaply.</p>
     */
    private static int auditPiso(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "piso");
        var piso = es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(id);
        if (piso == null) {
            ctx.getSource().sendFailure(Component.literal("No existe el piso '" + id + "'."));
            return 0;
        }
        var results = es.boffmedia.teras.dungeon.build.RoomAuditor.audit(piso,
                ctx.getSource().getServer().getStructureManager(),
                DungeonsConfig.roomSize(), DungeonsConfig.roomHeight(),
                DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
        if (results.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "No hay plantillas que auditar — 'piso info " + id + "'."));
            return 0;
        }
        long dirty = results.stream().filter(r -> !r.clean()).count();
        long errors = results.stream().mapToLong(
                es.boffmedia.teras.dungeon.build.RoomAuditor.Result::errors).sum();
        for (var result : results) {
            if (result.clean()) {
                continue;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§e" + result.roomKey() + " §7("
                    + result.template() + ")"), false);
            for (var finding : result.findings()) {
                boolean error = finding.level()
                        == es.boffmedia.teras.dungeon.piso.RoomAudit.Level.ERROR;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        (error ? "  §c✖ " : "  §6! ") + finding.message()), false);
            }
        }
        if (dirty == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a" + results.size()
                    + " plantillas auditadas, todas correctas."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§7" + dirty + " de "
                    + results.size() + " plantillas con avisos; §c" + errors + " error(es)§7. "
                    + "Los §c✖§7 rompen la sala en partida; los §6!§7 solo la empeoran."), false);
        }
        return 1;
    }

    /**
     * Deletes a piso's generated overrides so the jar's shipped rooms take over. The only way out of
     * "the mod updated but the rooms did not" — {@code generated} shadows the jar, invisibly.
     */
    private static int purgePiso(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "piso");
        var piso = es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(id);
        if (piso == null) {
            ctx.getSource().sendFailure(Component.literal("No existe el piso '" + id + "'."));
            return 0;
        }
        var result = es.boffmedia.teras.dungeon.build.PisoAuthoring.purgeGenerated(
                ctx.getSource().getServer(), piso);
        es.boffmedia.teras.dungeon.piso.PisoCatalog.validateTemplates(
                ctx.getSource().getServer().getStructureManager());
        ctx.getSource().sendSuccess(() -> Component.literal("§a" + id + ": " + result.copied()
                + " plantillas locales borradas; vuelven a usarse las del mod."), false);
        if (!result.ok()) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "§cNo se pudieron borrar: " + String.join(", ", result.failed())));
        }
        return 1;
    }

    /**
     * Rewrites a piso's config back to the mod's factory content, then reloads. The escape hatch for
     * "I changed shipped content but the config file on disk still has the old version" — the
     * defaults only seed an absent file, so an existing one keeps its old content until this rewrites
     * it. Authored room variants survive; everything else resets.
     */
    private static int resyncPiso(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "piso");
        String error = es.boffmedia.teras.dungeon.piso.PisoCatalog.resyncPiso(id);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        // Re-read from disk so the rewrite is live now, the same passes '/teras dungeon reload' runs.
        es.boffmedia.teras.dungeon.piso.PisoCatalog.load();
        es.boffmedia.teras.dungeon.piso.PisoCatalog.validateTemplates(
                ctx.getSource().getServer().getStructureManager());
        ctx.getSource().sendSuccess(() -> Component.literal("§a" + id + " restablecido a los valores "
                + "de fábrica (enemigos, decoración, formas, luz, maldiciones…). Las variantes de "
                + "sala se conservan."), false);
        return 1;
    }

    /** Every piso and whether it can actually build a floor. */
    private static int listPisos(CommandContext<CommandSourceStack> ctx) {
        var declared = es.boffmedia.teras.dungeon.piso.PisoCatalog.declared();
        if (declared.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No hay pisos definidos."));
            return 0;
        }
        for (var piso : declared.values()) {
            boolean usable = es.boffmedia.teras.dungeon.piso.PisoCatalog.isUsable(piso.id());
            int owed = piso.requiredRooms().size();
            int missing = es.boffmedia.teras.dungeon.piso.PisoCatalog.missingRooms(piso.id()).size();
            String line = (usable ? "§a✔ " : "§c✘ ") + piso.id() + " §7— " + piso.nombre()
                    + " · " + piso.formas().size() + " formas · " + (owed - missing) + "/" + owed
                    + " salas" + (usable ? "" : " §c(no se puede seleccionar)");
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§8'piso info <id>' lista las salas que faltan."), false);
        return 1;
    }

    /** What one piso is and, when it cannot build, exactly which rooms it still owes. */
    private static int pisoInfo(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "piso");
        var piso = es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(id);
        if (piso == null) {
            ctx.getSource().sendFailure(Component.literal("No existe el piso '" + id + "'."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§e" + piso.id() + " §7— " + piso.nombre()
                + (piso.subtitulo().isBlank() ? "" : " · §o" + piso.subtitulo())), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7formas: §f"
                + piso.formas().stream().map(Enum::name).sorted().toList()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7maldiciones: §f"
                + piso.maldiciones().stream().map(Enum::name).sorted().toList()
                + " §7· luz §f" + piso.luz()
                + (piso.mecanica().isNone() ? "" : " §7· mecánica §f" + piso.mecanica())), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7hereda: §f"
                + (piso.hereda().isEmpty() ? "(nada)" : String.join(", ", piso.hereda()))), false);
        java.util.List<String> wrongSize =
                es.boffmedia.teras.dungeon.piso.PisoCatalog.mismatchedRooms(id);
        if (!wrongSize.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c" + wrongSize.size()
                    + " plantilla(s) con tamaño equivocado — config.yml desfasado o copias locales "
                    + "viejas. 'piso limpiar " + id + "' borra las locales:"), false);
            for (String line : wrongSize) {
                ctx.getSource().sendSuccess(() -> Component.literal("  §7" + line), false);
            }
        }
        java.util.List<String> missing =
                es.boffmedia.teras.dungeon.piso.PisoCatalog.missingRooms(id);
        if (missing.isEmpty()) {
            int variants = piso.requiredRooms().stream()
                    .mapToInt(k -> RoomTemplates.pool(piso, k).size()).sum();
            long onlyOne = piso.requiredRooms().stream()
                    .filter(k -> RoomTemplates.pool(piso, k).size() == 1).count();
            ctx.getSource().sendSuccess(() -> Component.literal("§aTiene sus "
                    + piso.requiredRooms().size() + " salas §7(" + variants + " plantillas en total"
                    + (onlyOne == 0 ? "" : ", " + onlyOne + " con una sola variante") + ")."), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§8'sala listar <tipo> " + id + "' desglosa cada tipo."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§cLe faltan " + missing.size()
                + " de " + piso.requiredRooms().size() + " salas:"), false);
        for (String room : missing) {
            ctx.getSource().sendSuccess(() -> Component.literal("  §7" + room), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§8'piso crear " + id + " desde <otro>' las copia para empezar."), false);
        return 1;
    }

    /**
     * Stamps a piso out of another: writes its config if absent, then copies every room it owes as
     * a real file. The copy is an authoring step, not a link — the two are unrelated afterwards.
     */
    private static int createPiso(CommandContext<CommandSourceStack> ctx, boolean overwrite) {
        String newId = StringArgumentType.getString(ctx, "nuevo");
        String sourceId = StringArgumentType.getString(ctx, "origen");
        var source = es.boffmedia.teras.dungeon.piso.PisoCatalog.piso(sourceId);
        if (source == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "El piso origen '" + sourceId + "' no existe o no tiene sus salas."));
            return 0;
        }
        String error = es.boffmedia.teras.dungeon.piso.PisoCatalog.createFrom(newId, source);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        es.boffmedia.teras.dungeon.piso.PisoCatalog.load();
        var target = es.boffmedia.teras.dungeon.piso.PisoCatalog.declaredPiso(newId);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Se escribió el piso pero no se pudo releer — revisa el log."));
            return 0;
        }
        var result = es.boffmedia.teras.dungeon.build.PisoAuthoring.copyRooms(
                ctx.getSource().getServer(), source, target, overwrite);
        es.boffmedia.teras.dungeon.piso.PisoCatalog.validateTemplates(
                ctx.getSource().getServer().getStructureManager());
        ctx.getSource().sendSuccess(() -> Component.literal("§a" + newId + ": " + result.copied()
                + " salas copiadas de " + sourceId + (overwrite ? " (rehechas)." : ".")), false);
        if (!overwrite && result.copied() == 0) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "§7Ya tenía todas sus salas; nada que copiar. Usa '… desde " + sourceId
                            + " rehacer' para reemplazarlas por las actuales."));
        }
        if (!result.ok()) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "§cNo se pudieron copiar: " + String.join(", ", result.failed())));
        }
        boolean usable = es.boffmedia.teras.dungeon.piso.PisoCatalog.isUsable(newId);
        ctx.getSource().sendSuccess(() -> Component.literal(usable
                ? "§7Ya es seleccionable. Edítalo con 'sala editar <tipo> 0 " + newId + "'."
                : "§eAún le faltan salas — 'piso info " + newId + "'."), false);
        return 1;
    }

    private static int markRoom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return editorCall(ctx, RoomEditor.placeMarker(ctx.getSource().getPlayerOrException(),
                StringArgumentType.getString(ctx, "marca")));
    }

    private static int saveRoom(CommandContext<CommandSourceStack> ctx, String name, String piso)
            throws CommandSyntaxException {
        return editorCall(ctx,
                RoomEditor.save(ctx.getSource().getPlayerOrException(), name, piso));
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
        es.boffmedia.teras.dungeon.piso.PisoCatalog.load();
        es.boffmedia.teras.dungeon.piso.PisoCatalog.validateTemplates(
                ctx.getSource().getServer().getStructureManager());
        es.boffmedia.teras.dungeon.encounter.SpawnTables.load();
        es.boffmedia.teras.dungeon.gear.GearConfig.load();
        // After the catalog, because a piece's kind decides which item it migrates onto.
        int migrated = es.boffmedia.teras.dungeon.gear.GearMigration.sweepAll(
                ctx.getSource().getServer());
        if (migrated > 0) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "§7Equipo migrado a los objetos propios: " + migrated + " pieza(s)."));
        }
        // config/teras/config.yml too, not just the dungeon files: a run's result is posted with
        // apiURL, apiToken and id from there, so a reload that left them stale meant fixing the
        // backend URL and watching the next run vanish anyway, with nothing saying why.
        java.util.List<String> restartOnly = es.boffmedia.teras.util.TerasConfig.reload();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Configuración de mazmorras y config/teras/config.yml recargadas."), false);
        if (!restartOnly.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("§eCambiaste " + String.join(", ",
                    restartOnly) + " — eso solo se aplica al reiniciar el servidor."));
        }
        return 1;
    }
}
