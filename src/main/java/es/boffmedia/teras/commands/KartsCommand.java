package es.boffmedia.teras.commands;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.ForgePlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldedit.world.World;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects._old.karts.Circuito;
import es.boffmedia.teras.util.objects._old.karts.Punto;
import es.boffmedia.teras.util.objects.karts.Checkpoint;
import es.boffmedia.teras.util.objects.karts.CoordinatePoint;
import es.boffmedia.teras.util.objects.karts.RaceTrack;
import es.boffmedia.teras.util.objects.karts.StartingDirection;
import es.boffmedia.teras.util.objects.karts.editor.TrackEditor;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.UUID;

public class KartsCommand {
    private RaceTrack currentTrack;

    public KartsCommand(CommandDispatcher<CommandSource> dispatcher){
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("karts")
                .then(votarCarrera())
                .then(desvotarCarrera())
                .then(salir())
                .then(Commands.literal("carrera")
                        .then(entrarCarrera())
                        .then(salirCarrera())
                        .then(cuentaAtras())
                        .then(iniciarCarrera())
                        .then(raceStatus())
                )
                .then(Commands.literal("circuito")
                        .requires((commandSource -> commandSource.hasPermission(3)))
                        .then(crearCircuito())
                        .then(nuevoInicio())
                        .then(nuevoCheckpoint())
                        .then(guardarCircuito())
                        .then(listaCircuitos())
                        .then(visualizeTrack())
                        .then(validateTrack())
                        // New edit commands
                        .then(Commands.literal("edit")
                                .then(Commands.argument("nombre", StringArgumentType.string())
                                        .executes(this::loadTrackForEditing)))
                        .then(Commands.literal("list")
                                .then(Commands.literal("checkpoints")
                                        .executes(this::listCheckpoints))
                                .then(Commands.literal("starts")
                                        .executes(this::listStartPoints)))
                        .then(Commands.literal("remove")
                                .then(Commands.literal("checkpoint")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::removeCheckpoint)))
                                .then(Commands.literal("start")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::removeStartPoint))))
                        // Add these new preview commands
                        .then(Commands.literal("preview")
                                .then(Commands.literal("checkpoint")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::previewCheckpoint)))
                                .then(Commands.literal("start")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::previewStartPoint))))
                        // Add these new WorldEdit editing commands
                        /*.then(Commands.literal("editpoint")
                                .then(Commands.literal("checkpoint")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::editCheckpoint)))
                                .then(Commands.literal("start")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::editStartPoint))))*/
                        .then(Commands.literal("direction")
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (StartingDirection dir : StartingDirection.values()) {
                                                builder.suggest(dir.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::setDirection)))
                );
        dispatcher.register(literalBuilder);
    }



    public boolean esJugador(CommandContext<CommandSource> command) {
        Entity entity = command.getSource().getEntity();
        if (entity instanceof ServerPlayerEntity) {
            entity.sendMessage(new StringTextComponent("Solo los NPC de TokiKarts pueden ejecutar ese comando."), UUID.randomUUID());
            return true;
        }
        return false;
    }

    private ArgumentBuilder<CommandSource,?> listaCircuitos() {
        return Commands.literal("listar")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        player.sendMessage(new StringTextComponent("Listando circuitos..."), UUID.randomUUID());
                        Teras.raceManager.listTracks(player);
                        return 1;
                    }
                    return 0;
                });
    }

    private ArgumentBuilder<CommandSource,?> votarCarrera() {
        return Commands.literal("votar")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        player.sendMessage(new StringTextComponent("Votando..." + player.getUUID()), UUID.randomUUID());
                        Teras.raceManager.voteStart(player);
                        return 1;
                    }
                    return 0;
                });
    }


    private ArgumentBuilder<CommandSource,?> desvotarCarrera() {
        return Commands.literal("cancelarvoto")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        player.sendMessage(new StringTextComponent("Desvotando..." + player.getUUID()), UUID.randomUUID());
                        Teras.raceManager.unvoteStart(player);
                        return 1;
                    }
                    return 0;
                });
    }


    private ArgumentBuilder<CommandSource,?> salir() {
        return Commands.literal("salir")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        player.sendMessage(new StringTextComponent("Saliendo..." + player.getUUID()), UUID.randomUUID());
                        Teras.raceManager.leaveRace(player);
                        return 1;
                    }
                    return 0;
                });
    }

    private ArgumentBuilder<CommandSource,?> cuentaAtras() {
        return Commands.literal("cuentaAtras")
                .then(Commands.argument("nombre", StringArgumentType.string())
                        .executes((command) -> {
                            if(esJugador(command)) return 0;
                            String nombre = StringArgumentType.getString(command, "nombre");

                            Teras.raceManager.startCountdown(nombre);
                            return 1;
                        })
                );
    }

    private ArgumentBuilder<CommandSource,?> iniciarCarrera() {
        return Commands.literal("iniciar")
                .then(Commands.argument("nombre", StringArgumentType.string())
                        .executes((command) -> {
                            if(esJugador(command)) return 0;
                            String nombre = StringArgumentType.getString(command, "nombre");
                            // Usar el CarreraManager para crear la carrera
                            Teras.raceManager.startRace(nombre);
                            return 1;
                        })
                );
    }

    private ArgumentBuilder<CommandSource,?> salirCarrera() {
        return Commands.literal("salir")
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes((command) -> {
                            ServerPlayerEntity player = EntityArgument.getPlayer(command, "jugador");
                            Teras.raceManager.leaveRace(player);
                            return 1;
                        })
                );


    }

    private ArgumentBuilder<CommandSource,?> entrarCarrera() {
        return Commands.literal("entrar")
                .then(Commands.argument("jugador", EntityArgument.player())
                        .then(Commands.argument("nombre", StringArgumentType.string())
                                .then(Commands.argument("vueltas", IntegerArgumentType.integer())
                                        .executes((command) -> {
                                            if(esJugador(command)) return 0;
                                            int vueltas = IntegerArgumentType.getInteger(command, "vueltas");
                                            String nombre = StringArgumentType.getString(command, "nombre");
                                            ServerPlayerEntity player = EntityArgument.getPlayer(command, "jugador");


                                            // Usar el CarreraManager para crear la carrera
                                            Teras.raceManager.joinRace(nombre, vueltas, player);


                                            return 1;
                                        })
                                )));
    }

    public LiteralArgumentBuilder<CommandSource> crearCircuito() {
        return Commands.literal("crear")
                .then(Commands.argument("nombre", StringArgumentType.string())
                        .executes((command) -> {
                            String nombre = StringArgumentType.getString(command, "nombre");
                            currentTrack = new RaceTrack(nombre, StartingDirection.NORTH, new LinkedList<>(), new ArrayList<>());
                            command.getSource().sendSuccess(new StringTextComponent("Circuito creado: " + nombre), false);
                            return 1;
                        })
                );
    }

    public LiteralArgumentBuilder<CommandSource> nuevoCheckpoint() {
        return Commands.literal("checkpoint")
                .executes((command) -> {
                    CommandSource source = command.getSource();
                    if (currentTrack == null) {
                        source.sendFailure(new StringTextComponent("Crea un circuito primero con /karts circuito crear <nombre>"));
                        return 0;
                    }
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    SessionManager manager = WorldEdit.getInstance().getSessionManager();
                    ForgePlayer adaptedPlayer = ForgeAdapter.adaptPlayer(player);
                    try {
                        Region selection = manager.get(adaptedPlayer).getSelection();
                        BlockVector3 punto1 = selection.getMinimumPoint();
                        BlockVector3 punto2 = selection.getMaximumPoint();

                        CoordinatePoint start = new CoordinatePoint(punto1.getX(), punto1.getY(), punto1.getZ());
                        CoordinatePoint end = new CoordinatePoint(punto2.getX(), punto2.getY(), punto2.getZ());

                        Checkpoint checkpoint = new Checkpoint(start, end);
                        currentTrack.addCheckpoint(checkpoint);

                        source.sendSuccess(new StringTextComponent("Checkpoint añadido"), false);
                        source.sendSuccess(new StringTextComponent("Checkpoints totales: " + currentTrack.getCheckpoints().size()), false);

                    } catch (IncompleteRegionException e) {
                        source.sendFailure(new StringTextComponent("Selecciona una región primero"));
                        e.printStackTrace();
                    }
                    return 1;
                });
    }



    private ArgumentBuilder<CommandSource,?> nuevoInicio() {
        return Commands.literal("inicio")
                .executes((command) -> {
                    CommandSource source = command.getSource();
                    if (currentTrack == null) {
                        source.sendFailure(new StringTextComponent("Crea un circuito primero con /karts circuito crear <nombre>"));
                        return 0;
                    }
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    SessionManager manager = WorldEdit.getInstance().getSessionManager();
                    ForgePlayer adaptedPlayer = ForgeAdapter.adaptPlayer(player);
                    try {
                        Region selection = manager.get(adaptedPlayer).getSelection();
                        BlockVector3 v1 = selection.getMinimumPoint();

                        CoordinatePoint startPoint = new CoordinatePoint(v1.getX(), v1.getY(), v1.getZ());
                        currentTrack.addStartingPoint(startPoint);

                        source.sendSuccess(new StringTextComponent("Punto de inicio añadido"), false);
                        source.sendSuccess(new StringTextComponent("Puntos de inicio totales: " +
                                currentTrack.getStartingPoints().size()), false);

                    } catch (IncompleteRegionException e) {
                        source.sendFailure(new StringTextComponent("Selecciona una región primero"));
                        e.printStackTrace();
                    }
                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource,?> guardarCircuito() {
        return Commands.literal("guardar")
                .executes((command) -> {
                    CommandSource source = command.getSource();
                    if (currentTrack == null) {
                        source.sendFailure(new StringTextComponent("Crea un circuito primero con /karts circuito crear <nombre>"));
                        return 0;
                    }

                    if(currentTrack.getCheckpoints().size() < 2) {
                        source.sendFailure(new StringTextComponent("El circuito debe tener al menos 2 checkpoints"));
                        return 0;
                    }

                    if(currentTrack.getStartingPoints().size() < 1) {
                        source.sendFailure(new StringTextComponent("El circuito debe tener al menos 1 punto de inicio"));
                        return 0;
                    }

                    // Add the track to RaceManager
                    Teras.raceManager.tracks.put(currentTrack.getName(), currentTrack);
                    // Here you would also save to file

                    source.sendSuccess(new StringTextComponent("Circuito guardado"), false);
                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource, ?> raceStatus() {
        return Commands.literal("status")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        Teras.raceManager.displayRaceStatus(player);
                        return 1;
                    }
                    return 0;
                });
    }


    private ArgumentBuilder<CommandSource,?> editTrack() {
        return Commands.literal("edit")
                .requires(source -> source.hasPermission(3))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
                            String trackName = StringArgumentType.getString(context, "name");
                            RaceTrack track = Teras.raceManager.tracks.get(trackName);

                            if (track == null) {
                                context.getSource().sendFailure(new StringTextComponent("Track not found: " + trackName));
                                return 0;
                            }

                            TrackEditor editor = new TrackEditor(track);
                            editor.startEditing(player);
                            return 1;
                        }));
    }

    private ArgumentBuilder<CommandSource,?> visualizeTrack() {
        return Commands.literal("visualizar")
                .executes((command) -> {
                    CommandSource source = command.getSource();
                    if (currentTrack == null) {
                        source.sendFailure(new StringTextComponent("No hay circuito en edición"));
                        return 0;
                    }

                    if (!(command.getSource().getEntity() instanceof ServerPlayerEntity)) {
                        return 0;
                    }

                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    TrackEditor editor = new TrackEditor(currentTrack);
                    editor.visualizeTrack(player);
                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource,?> validateTrack() {
        return Commands.literal("validate")
                .requires(source -> source.hasPermission(3))
                .executes(context -> {
                    if (currentTrack == null) {
                        context.getSource().sendFailure(new StringTextComponent("No track being edited"));
                        return 0;
                    }

                    ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
                    TrackEditor editor = new TrackEditor(currentTrack);
                    editor.validateTrack(player);
                    return 1;
                });
    }


    private int loadTrackForEditing(CommandContext<CommandSource> context) {
        String trackName = StringArgumentType.getString(context, "nombre");
        RaceTrack track = Teras.raceManager.tracks.get(trackName);

        if (track == null) {
            context.getSource().sendFailure(new StringTextComponent("No se encontró el circuito: " + trackName));
            return 0;
        }

        currentTrack = track;
        context.getSource().sendSuccess(new StringTextComponent("Editando circuito: " + trackName), false);

        // Automatically show track info
        listTrackInfo(context);
        return 1;
    }

    private int listCheckpoints(CommandContext<CommandSource> context) {
        if (currentTrack == null) {
            context.getSource().sendFailure(new StringTextComponent("No hay circuito en edición"));
            return 0;
        }

        context.getSource().sendSuccess(new StringTextComponent("§6=== Checkpoints del circuito " + currentTrack.getName() + " ==="), false);

        ArrayList<Checkpoint> checkpoints = currentTrack.getCheckpoints();
        for (int i = 0; i < checkpoints.size(); i++) {
            Checkpoint cp = checkpoints.get(i);

            // Create the main text component
            StringTextComponent mainText = new StringTextComponent(String.format(
                    "§7#%d: §fdesde %s hasta %s ",
                    i + 1,
                    formatPoint(cp.getStart()),
                    formatPoint(cp.getEnd())
            ));

            // Create action buttons
            StringTextComponent deleteButton = new StringTextComponent("§c[X] ");
            deleteButton.setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts circuito remove checkpoint " + (i + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Eliminar checkpoint")))
            );

            StringTextComponent previewButton = new StringTextComponent("§e[👁] ");
            previewButton.setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts circuito preview checkpoint " + (i + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Previsualizar checkpoint")))
            );

            StringTextComponent editButton = new StringTextComponent("§a[✎]");
            editButton.setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts circuito edit checkpoint " + (i + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Editar en WorldEdit")))
            );

            // Combine all components
            mainText.append(deleteButton).append(previewButton).append(editButton);
            context.getSource().sendSuccess(mainText, false);
        }
        return 1;
    }

    private int listStartPoints(CommandContext<CommandSource> context) {
        if (currentTrack == null) {
            context.getSource().sendFailure(new StringTextComponent("No hay circuito en edición"));
            return 0;
        }

        context.getSource().sendSuccess(new StringTextComponent("§6=== Puntos de inicio del circuito " + currentTrack.getName() + " ==="), false);

        LinkedList<CoordinatePoint> starts = currentTrack.getStartingPoints();
        for (int i = 0; i < starts.size(); i++) {
            CoordinatePoint point = starts.get(i);

            // Create the main text component
            StringTextComponent mainText = new StringTextComponent(String.format(
                    "§7#%d: §f%s ",
                    i + 1,
                    formatPoint(point)
            ));

            // Create action buttons
            StringTextComponent deleteButton = new StringTextComponent("§c[X] ");
            deleteButton.setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts circuito remove start " + (i + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Eliminar punto de inicio")))
            );

            StringTextComponent previewButton = new StringTextComponent("§e[👁] ");
            previewButton.setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts circuito preview start " + (i + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Previsualizar punto de inicio")))
            );

            StringTextComponent editButton = new StringTextComponent("§a[✎]");
            editButton.setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/karts circuito edit start " + (i + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Editar en WorldEdit")))
            );

            // Combine all components
            mainText.append(deleteButton).append(previewButton).append(editButton);
            context.getSource().sendSuccess(mainText, false);
        }
        return 1;
    }

    private int removeCheckpoint(CommandContext<CommandSource> context) {
        if (currentTrack == null) {
            context.getSource().sendFailure(new StringTextComponent("No hay circuito en edición"));
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        ArrayList<Checkpoint> checkpoints = currentTrack.getCheckpoints();

        if (index < 0 || index >= checkpoints.size()) {
            context.getSource().sendFailure(new StringTextComponent("Índice de checkpoint inválido"));
            return 0;
        }

        checkpoints.remove(index);
        context.getSource().sendSuccess(new StringTextComponent("Checkpoint eliminado"), false);
        return 1;
    }

    private int removeStartPoint(CommandContext<CommandSource> context) {
        if (currentTrack == null) {
            context.getSource().sendFailure(new StringTextComponent("No hay circuito en edición"));
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        LinkedList<CoordinatePoint> starts = currentTrack.getStartingPoints();

        if (index < 0 || index >= starts.size()) {
            context.getSource().sendFailure(new StringTextComponent("Índice de punto de inicio inválido"));
            return 0;
        }

        starts.remove(index);
        context.getSource().sendSuccess(new StringTextComponent("Punto de inicio eliminado"), false);
        return 1;
    }

    private int setDirection(CommandContext<CommandSource> context) {
        if (currentTrack == null) {
            context.getSource().sendFailure(new StringTextComponent("No hay circuito en edición"));
            return 0;
        }

        String dirStr = StringArgumentType.getString(context, "direction");
        try {
            StartingDirection direction = StartingDirection.valueOf(dirStr.toUpperCase());
            currentTrack.setStartingDirection(direction);
            context.getSource().sendSuccess(new StringTextComponent("Dirección inicial establecida a: " + direction), false);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(new StringTextComponent("Dirección inválida. Usa: NORTH, SOUTH, EAST, o WEST"));
            return 0;
        }
    }

    private void listTrackInfo(CommandContext<CommandSource> context) {
        context.getSource().sendSuccess(new StringTextComponent("=== Información del Circuito ==="), false);
        context.getSource().sendSuccess(new StringTextComponent("Nombre: " + currentTrack.getName()), false);
        context.getSource().sendSuccess(new StringTextComponent("Dirección: " + currentTrack.getStartingDirection()), false);
        context.getSource().sendSuccess(new StringTextComponent("Checkpoints: " + currentTrack.getCheckpoints().size()), false);
        context.getSource().sendSuccess(new StringTextComponent("Puntos de inicio: " + currentTrack.getStartingPoints().size()), false);
    }

    private String formatPoint(CoordinatePoint point) {
        return String.format("(%.1f, %.1f, %.1f)", point.getX(), point.getY(), point.getZ());
    }

    private int previewCheckpoint(CommandContext<CommandSource> context) {
        if (currentTrack == null || !(context.getSource().getEntity() instanceof ServerPlayerEntity)) {
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        ArrayList<Checkpoint> checkpoints = currentTrack.getCheckpoints();

        if (index < 0 || index >= checkpoints.size()) {
            context.getSource().sendFailure(new StringTextComponent("Índice de checkpoint inválido"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
        TrackEditor editor = new TrackEditor(currentTrack);
        editor.visualizeCheckpoint((ServerWorld)player.level, checkpoints.get(index));

        return 1;
    }

    private int previewStartPoint(CommandContext<CommandSource> context) {
        if (currentTrack == null || !(context.getSource().getEntity() instanceof ServerPlayerEntity)) {
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        LinkedList<CoordinatePoint> starts = currentTrack.getStartingPoints();

        if (index < 0 || index >= starts.size()) {
            context.getSource().sendFailure(new StringTextComponent("Índice de punto de inicio inválido"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
        TrackEditor editor = new TrackEditor(currentTrack);
        editor.visualizeStartingPoint((ServerWorld)player.level, starts.get(index));

        return 1;
    }

    private int editCheckpoint(CommandContext<CommandSource> context) {
        if (currentTrack == null || !(context.getSource().getEntity() instanceof ServerPlayerEntity)) {
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        ArrayList<Checkpoint> checkpoints = currentTrack.getCheckpoints();

        if (index < 0 || index >= checkpoints.size()) {
            context.getSource().sendFailure(new StringTextComponent("Índice de checkpoint inválido"));
            return 0;
        }

        try {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
            Checkpoint checkpoint = checkpoints.get(index);

            // Get WorldEdit session manager
            WorldEdit worldEdit = WorldEdit.getInstance();
            SessionManager manager = worldEdit.getSessionManager();

            // Create selection
            LocalSession session = manager.get(ForgeAdapter.adaptPlayer(player));
            session.getRegionSelector(ForgeAdapter.adapt(player.level)).selectPrimary(
                    BlockVector3.at(checkpoint.getStart().getX(), checkpoint.getStart().getY(), checkpoint.getStart().getZ()),
                    null
            );
            session.getRegionSelector(ForgeAdapter.adapt(player.level)).selectSecondary(
                    BlockVector3.at(checkpoint.getEnd().getX(), checkpoint.getEnd().getY(), checkpoint.getEnd().getZ()),
                    null
            );

            context.getSource().sendSuccess(new StringTextComponent("Selección de WorldEdit actualizada al checkpoint " + (index + 1)), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Error al establecer la selección de WorldEdit"));
            e.printStackTrace();
            return 0;
        }
    }


    private int editStartPoint(CommandContext<CommandSource> context) {
        if (currentTrack == null || !(context.getSource().getEntity() instanceof ServerPlayerEntity)) {
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        LinkedList<CoordinatePoint> starts = currentTrack.getStartingPoints();

        if (index < 0 || index >= starts.size()) {
            context.getSource().sendFailure(new StringTextComponent("Índice de punto de inicio inválido"));
            return 0;
        }

        try {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().getEntity();
            CoordinatePoint point = starts.get(index);

            // Get WorldEdit session manager
            WorldEdit worldEdit = WorldEdit.getInstance();
            SessionManager manager = worldEdit.getSessionManager();

            // Create selection
            LocalSession session = manager.get(ForgeAdapter.adaptPlayer(player));
            BlockVector3 pos = BlockVector3.at(point.getX(), point.getY(), point.getZ());
            session.getRegionSelector(ForgeAdapter.adapt(player.level)).selectPrimary(pos, null);
            session.getRegionSelector(ForgeAdapter.adapt(player.level)).selectSecondary(pos, null);

            context.getSource().sendSuccess(new StringTextComponent("Selección de WorldEdit actualizada al punto de inicio " + (index + 1)), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(new StringTextComponent("Error al establecer la selección de WorldEdit"));
            e.printStackTrace();
            return 0;
        }
    }

}

