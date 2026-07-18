package es.boffmedia.teras.karts.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import es.boffmedia.teras.karts.KartsConfig;
import es.boffmedia.teras.karts.editor.TrackEditorSessions;
import es.boffmedia.teras.karts.editor.TrackVisualizer;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackCheckpoint;
import es.boffmedia.teras.karts.model.TrackPoint;
import es.boffmedia.teras.karts.model.TrackValidator;
import es.boffmedia.teras.karts.store.TrackStore;
import es.boffmedia.teras.region.model.TerasRegion;
import es.boffmedia.teras.region.worldedit.SelectionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * {@code /karts circuito …} — authoring circuits in-world.
 *
 * <p>Checkpoints come from a WorldEdit cuboid selection (the two corners of the gate); grid slots
 * come from where the admin is standing and which way they are facing, so lining up a staggered
 * grid is a matter of walking it. WorldEdit is optional throughout: only {@link #fromSelection}
 * names the bridge class, and it checks {@code ModList} first, so nothing links on a server without
 * it and the command explains what to install.</p>
 *
 * <p>Unlike 1.16.5, every mutation writes {@code circuitos.json} immediately. There, {@code guardar}
 * put the track in a map and never persisted it, so a restart discarded the admin's work.</p>
 */
public final class CircuitoCommands {
    private CircuitoCommands() {}

    private static final SimpleCommandExceptionType ERROR_NO_WORLDEDIT = new SimpleCommandExceptionType(
            Component.literal("WorldEdit no está instalado; /karts circuito checkpoint necesita una "
                    + "selección de WorldEdit (//wand y marca dos esquinas)"));
    private static final SimpleCommandExceptionType ERROR_INCOMPLETE_SELECTION = new SimpleCommandExceptionType(
            Component.literal("Selección de WorldEdit incompleta: marca las dos esquinas del "
                    + "checkpoint con //wand"));
    private static final SimpleCommandExceptionType ERROR_NOT_CUBOID = new SimpleCommandExceptionType(
            Component.literal("Un checkpoint debe ser una selección cuboide (//sel cuboid)"));
    private static final SimpleCommandExceptionType ERROR_NO_SESSION = new SimpleCommandExceptionType(
            Component.literal("No estás editando ningún circuito. Usa /karts circuito crear <nombre> "
                    + "o /karts circuito editar <nombre>"));

    static void contribute(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("circuito")
                .requires(source -> source.hasPermission(KartsCommand.PERMISSION_ADMIN))
                .then(Commands.literal("crear")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .executes(CircuitoCommands::create)))
                .then(Commands.literal("editar")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .suggests(KartsCommand.TRACK_SUGGESTIONS)
                                .executes(CircuitoCommands::edit)))
                .then(Commands.literal("checkpoint")
                        .executes(CircuitoCommands::addCheckpoint))
                .then(Commands.literal("inicio")
                        .executes(CircuitoCommands::addStartingPoint))
                .then(Commands.literal("vueltas")
                        .then(Commands.argument("vueltas", IntegerArgumentType.integer(1, 50))
                                .executes(CircuitoCommands::setLaps)))
                .then(Commands.literal("lista")
                        .executes(CircuitoCommands::listElements))
                .then(Commands.literal("borrar")
                        .then(Commands.literal("checkpoint")
                                .then(Commands.argument("indice", IntegerArgumentType.integer(1))
                                        .executes(context -> removeElement(context, true))))
                        .then(Commands.literal("inicio")
                                .then(Commands.argument("indice", IntegerArgumentType.integer(1))
                                        .executes(context -> removeElement(context, false)))))
                .then(Commands.literal("validar")
                        .executes(CircuitoCommands::validate))
                .then(Commands.literal("visualizar")
                        .executes(context -> visualise(context, true))
                        .then(Commands.literal("off")
                                .executes(context -> visualise(context, false))))
                .then(Commands.literal("listar")
                        .executes(CircuitoCommands::listTracks))
                .then(Commands.literal("eliminar")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .suggests(KartsCommand.TRACK_SUGGESTIONS)
                                .executes(CircuitoCommands::deleteTrack)))
                .then(Commands.literal("recargar")
                        .executes(CircuitoCommands::reload)));
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "nombre");
        if (TrackStore.exists(name)) {
            context.getSource().sendFailure(Component.literal(
                    "Ya existe un circuito llamado '" + name + "'. Usa /karts circuito editar " + name));
            return 0;
        }
        KartTrack track = new KartTrack(name);
        track.setDimension(player.serverLevel().dimension().location().toString());
        TrackStore.put(track);
        TrackStore.saveIfDirty();
        TrackEditorSessions.edit(player.getUUID(), name);

        context.getSource().sendSuccess(() -> Component.literal(
                "Circuito '" + name + "' creado y en edición. Añade checkpoints con "
                        + "//wand + /karts circuito checkpoint, y salidas con /karts circuito inicio.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "nombre");
        KartTrack track = TrackStore.get(name);
        if (track == null) {
            context.getSource().sendFailure(Component.literal("No existe el circuito '" + name + "'."));
            return 0;
        }
        TrackEditorSessions.edit(player.getUUID(), name);
        context.getSource().sendSuccess(() -> Component.literal(
                "Editando '" + name + "': " + track.checkpoints().size() + " checkpoints, "
                        + track.startingPoints().size() + " salidas.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int addCheckpoint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KartTrack track = requireSession(player);

        SelectionResult selection = fromSelection(player);
        if (selection.status() == SelectionResult.Status.INCOMPLETE) {
            throw ERROR_INCOMPLETE_SELECTION.create();
        }
        if (selection.status() != SelectionResult.Status.OK || selection.min() == null) {
            throw ERROR_NOT_CUBOID.create();
        }
        TerasRegion.Corner min = selection.min();
        TerasRegion.Corner max = selection.max();
        // Grown upward so the gate is a wall a car drives through, not a slab it drives over: the
        // position a race tracks is the vehicle's centre, well above the road blocks an admin
        // naturally selects.
        int extraHeight = Math.max(0, KartsConfig.checkpointHeight());
        TrackCheckpoint checkpoint = new TrackCheckpoint(
                TrackPoint.at(min.getX(), min.getY(), min.getZ()),
                TrackPoint.at(max.getX(), max.getY() + extraHeight, max.getZ()));
        track.addCheckpoint(checkpoint);
        TrackStore.markDirty();
        TrackStore.saveIfDirty();

        int number = track.checkpoints().size();
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "Checkpoint %d añadido a '%s' — X[%.1f, %.1f] Y[%.1f, %.1f] Z[%.1f, %.1f]. "
                        + "Comprueba que la banda Y cubre la altura a la que pasa el coche.",
                number, track.name(), checkpoint.minX(), checkpoint.maxX(),
                checkpoint.minY(), checkpoint.maxY(), checkpoint.minZ(), checkpoint.maxZ()))
                .withStyle(ChatFormatting.GREEN), false);
        return number;
    }

    private static int addStartingPoint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KartTrack track = requireSession(player);

        track.addStartingPoint(new TrackPoint(player.getX(), player.getY(), player.getZ(), player.getYRot()));
        TrackStore.markDirty();
        TrackStore.saveIfDirty();

        int number = track.startingPoints().size();
        context.getSource().sendSuccess(() -> Component.literal(
                "Salida " + number + " añadida mirando a " + Math.round(player.getYRot()) + "°.")
                .withStyle(ChatFormatting.GREEN), false);
        return number;
    }

    private static int setLaps(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KartTrack track = requireSession(player);
        int laps = IntegerArgumentType.getInteger(context, "vueltas");
        track.setDefaultLaps(laps);
        TrackStore.markDirty();
        TrackStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal(
                "'" + track.name() + "' correrá " + laps + " vueltas por defecto.")
                .withStyle(ChatFormatting.GREEN), false);
        return laps;
    }

    private static int listElements(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KartTrack track = requireSession(player);

        context.getSource().sendSuccess(() -> Component.literal(
                "=== " + track.name() + " ===").withStyle(ChatFormatting.GOLD), false);
        List<TrackCheckpoint> checkpoints = track.checkpoints();
        for (int i = 0; i < checkpoints.size(); i++) {
            TrackPoint mid = checkpoints.get(i).midpoint();
            int number = i + 1;
            context.getSource().sendSuccess(() -> Component.literal(String.format(
                    " Checkpoint %d — centro %.1f %.1f %.1f", number, mid.x(), mid.y(), mid.z()))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        List<TrackPoint> starts = track.startingPoints();
        for (int i = 0; i < starts.size(); i++) {
            TrackPoint slot = starts.get(i);
            int number = i + 1;
            context.getSource().sendSuccess(() -> Component.literal(String.format(
                    " Salida %d — %.1f %.1f %.1f mirando a %.0f°",
                    number, slot.x(), slot.y(), slot.z(), slot.yaw())).withStyle(ChatFormatting.GRAY), false);
        }
        return checkpoints.size() + starts.size();
    }

    private static int removeElement(CommandContext<CommandSourceStack> context, boolean checkpoint)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KartTrack track = requireSession(player);
        int index = IntegerArgumentType.getInteger(context, "indice");

        boolean removed = checkpoint
                ? track.removeCheckpoint(index - 1)
                : track.removeStartingPoint(index - 1);
        if (!removed) {
            context.getSource().sendFailure(Component.literal(
                    "No existe " + (checkpoint ? "el checkpoint " : "la salida ") + index + "."));
            return 0;
        }
        TrackStore.markDirty();
        TrackStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal(
                (checkpoint ? "Checkpoint " : "Salida ") + index + " eliminado.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int validate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KartTrack track = requireSession(player);

        List<String> problems = TrackValidator.validate(track);
        if (problems.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "'" + track.name() + "' es válido y se puede correr.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("'" + track.name() + "' no se puede correr:"));
        problems.forEach(problem -> context.getSource().sendFailure(Component.literal(" - " + problem)));
        return 0;
    }

    private static int visualise(CommandContext<CommandSourceStack> context, boolean show)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!show) {
            TrackEditorSessions.hideTrack(player.getUUID());
            context.getSource().sendSuccess(() -> Component.literal("Visualización desactivada."), false);
            return 1;
        }
        KartTrack track = requireSession(player);
        TrackEditorSessions.showTrack(player.getUUID(), track.name());
        TrackVisualizer.draw(player, track);
        context.getSource().sendSuccess(() -> Component.literal(
                "Mostrando '" + track.name() + "'. Desactiva con /karts circuito visualizar off.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int listTracks(CommandContext<CommandSourceStack> context) {
        var names = TrackStore.names();
        if (names.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "No hay circuitos. Crea uno con /karts circuito crear <nombre>."), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Circuitos (" + names.size() + "):").withStyle(ChatFormatting.GOLD), false);
        for (String name : names) {
            KartTrack track = TrackStore.get(name);
            boolean raceable = TrackValidator.isRaceable(track);
            context.getSource().sendSuccess(() -> Component.literal(
                    " " + name + " — " + track.checkpoints().size() + " checkpoints, "
                            + track.gridSize() + " salidas, " + track.defaultLaps() + " vueltas"
                            + (raceable ? "" : " (incompleto)"))
                    .withStyle(raceable ? ChatFormatting.GRAY : ChatFormatting.RED), false);
        }
        return names.size();
    }

    private static int deleteTrack(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "nombre");
        if (!TrackStore.remove(name)) {
            context.getSource().sendFailure(Component.literal("No existe el circuito '" + name + "'."));
            return 0;
        }
        TrackStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal(
                "Circuito '" + name + "' eliminado.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        TrackStore.reload();
        context.getSource().sendSuccess(() -> Component.literal(
                "Circuitos recargados desde disco: " + TrackStore.names().size() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return TrackStore.names().size();
    }

    private static KartTrack requireSession(ServerPlayer player) throws CommandSyntaxException {
        String name = TrackEditorSessions.editing(player.getUUID());
        if (name == null) {
            throw ERROR_NO_SESSION.create();
        }
        KartTrack track = TrackStore.get(name);
        if (track == null) {
            TrackEditorSessions.stopEditing(player.getUUID());
            throw ERROR_NO_SESSION.create();
        }
        return track;
    }

    /**
     * Reads the caller's WorldEdit selection. The {@code ModList} check must come before anything
     * that names {@code WorldEditBridge}, so the class never links without WorldEdit present.
     */
    private static SelectionResult fromSelection(ServerPlayer player) throws CommandSyntaxException {
        if (!ModList.get().isLoaded("worldedit")) {
            throw ERROR_NO_WORLDEDIT.create();
        }
        return es.boffmedia.teras.region.worldedit.WorldEditBridge.readSelection(player);
    }
}
