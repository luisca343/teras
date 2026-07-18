package es.boffmedia.teras.karts.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.karts.engine.KartsRaceManager;
import es.boffmedia.teras.karts.engine.RaceCore;
import es.boffmedia.teras.karts.engine.RaceEngine;
import es.boffmedia.teras.karts.engine.RaceParticipantState;
import es.boffmedia.teras.karts.engine.RaceSession;
import es.boffmedia.teras.karts.model.KartProvisioning;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackValidator;
import es.boffmedia.teras.karts.mode.TimeTrialMode;
import es.boffmedia.teras.karts.store.TrackStore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * The racing commands: what players use to enter and leave a race, and what the TokiKarts NPCs use
 * to run one on their behalf.
 *
 * <p>The {@code provision} argument on {@code /karts carrera entrar} is what an NPC uses to decide
 * how karts are handed out: {@code spec:<preset>}, {@code seleccion:<nombre>} or {@code garaje}.
 * Omitted, the race falls back to a spec race on the first defined preset.</p>
 */
public final class CarreraCommands {
    private CarreraCommands() {}

    /** Parses the {@code provision} argument. Returns null for "use the default". */
    private static KartProvisioning parseProvisioning(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase("garaje")) {
            return KartProvisioning.garage();
        }
        if (value.regionMatches(true, 0, "spec:", 0, 5)) {
            return KartProvisioning.spec(value.substring(5));
        }
        if (value.regionMatches(true, 0, "seleccion:", 0, 10)) {
            return KartProvisioning.curated(value.substring(10));
        }
        return null;
    }

    static void contribute(LiteralArgumentBuilder<CommandSourceStack> root) {
        // --- players ---
        root.then(Commands.literal("listar").executes(CarreraCommands::listTracks));
        root.then(Commands.literal("unirse")
                .then(Commands.argument("circuito", StringArgumentType.word())
                        .suggests(KartsCommand.TRACK_SUGGESTIONS)
                        .executes(context -> join(context, null))
                        .then(Commands.argument("vueltas", IntegerArgumentType.integer(1, 50))
                                .executes(context -> join(context,
                                        IntegerArgumentType.getInteger(context, "vueltas"))))));
        root.then(Commands.literal("contrarreloj")
                .then(Commands.argument("circuito", StringArgumentType.word())
                        .suggests(KartsCommand.TRACK_SUGGESTIONS)
                        .executes(context -> timeTrial(context, null))
                        .then(Commands.argument("vueltas", IntegerArgumentType.integer(1, 50))
                                .executes(context -> timeTrial(context,
                                        IntegerArgumentType.getInteger(context, "vueltas"))))));
        root.then(Commands.literal("votar").executes(context -> vote(context, true)));
        root.then(Commands.literal("cancelarvoto").executes(context -> vote(context, false)));
        root.then(Commands.literal("salir").executes(CarreraCommands::leave));
        root.then(Commands.literal("status").executes(CarreraCommands::status));

        // --- race control (admins and the TokiKarts NPCs) ---
        root.then(Commands.literal("carrera")
                .requires(source -> source.hasPermission(KartsCommand.PERMISSION_RACE_CONTROL))
                .then(Commands.literal("entrar")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("circuito", StringArgumentType.word())
                                        .suggests(KartsCommand.TRACK_SUGGESTIONS)
                                        .then(Commands.argument("vueltas", IntegerArgumentType.integer(1, 50))
                                                .executes(CarreraCommands::joinOther)
                                                .then(Commands.argument("provision", StringArgumentType.word())
                                                        .executes(CarreraCommands::joinOther))))))
                .then(Commands.literal("salir")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(CarreraCommands::removeOther)))
                .then(Commands.literal("cuentaatras")
                        .then(Commands.argument("circuito", StringArgumentType.word())
                                .suggests(KartsCommand.TRACK_SUGGESTIONS)
                                .executes(context -> control(context, false))))
                .then(Commands.literal("iniciar")
                        .then(Commands.argument("circuito", StringArgumentType.word())
                                .suggests(KartsCommand.TRACK_SUGGESTIONS)
                                .executes(context -> control(context, true))))
                .then(Commands.literal("cancelar")
                        .then(Commands.argument("circuito", StringArgumentType.word())
                                .suggests(KartsCommand.TRACK_SUGGESTIONS)
                                .executes(CarreraCommands::cancel)))
                .then(Commands.literal("status").executes(CarreraCommands::statusAll)));
    }

    private static int listTracks(CommandContext<CommandSourceStack> context) {
        var names = TrackStore.names();
        var raceable = names.stream().filter(name -> TrackValidator.isRaceable(TrackStore.get(name))).toList();
        if (raceable.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "No hay circuitos disponibles todavía."), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Circuitos disponibles:")
                .withStyle(ChatFormatting.GOLD), false);
        for (String name : raceable) {
            KartTrack track = TrackStore.get(name);
            var session = KartsRaceManager.race(name);
            String state = session.map(active -> switch (active.race().phase()) {
                case LOBBY -> " — esperando jugadores (" + active.race().participants().size() + ")";
                case COUNTDOWN -> " — empezando";
                case RUNNING -> " — en curso";
                default -> "";
            }).orElse("");
            context.getSource().sendSuccess(() -> Component.literal(
                    " " + track.displayName() + " (" + name + ") — " + track.defaultLaps()
                            + " vueltas, " + track.gridSize() + " plazas" + state)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return raceable.size();
    }

    private static int join(CommandContext<CommandSourceStack> context, Integer laps)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return doJoin(context, player, StringArgumentType.getString(context, "circuito"), laps, null);
    }

    private static int joinOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "jugador");
        String provision = hasArgument(context, "provision")
                ? StringArgumentType.getString(context, "provision")
                : null;
        return doJoin(context, player, StringArgumentType.getString(context, "circuito"),
                IntegerArgumentType.getInteger(context, "vueltas"), parseProvisioning(provision));
    }

    private static boolean hasArgument(CommandContext<CommandSourceStack> context, String name) {
        try {
            context.getArgument(name, String.class);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int doJoin(CommandContext<CommandSourceStack> context, ServerPlayer player,
                              String trackName, Integer laps, KartProvisioning provisioning) {
        KartsRaceManager.JoinResult result = KartsRaceManager.join(
                context.getSource().getServer(), player, trackName, laps, null, provisioning);

        if (result != KartsRaceManager.JoinResult.OK) {
            context.getSource().sendFailure(Component.literal(explain(result, trackName)));
            return 0;
        }
        Optional<RaceSession> session = KartsRaceManager.raceOf(player.getUUID());
        int entrants = session.map(active -> active.race().participants().size()).orElse(1);
        int needed = session.map(active -> active.race().votesNeeded()).orElse(1);

        player.sendSystemMessage(Component.literal(
                "Te has apuntado a la carrera en '" + trackName + "'. Jugadores: " + entrants
                        + ". Escribe /karts votar cuando estés listo (" + needed + " votos necesarios).")
                .withStyle(ChatFormatting.GREEN));
        session.ifPresent(active -> active.race().participants().stream()
                .filter(participant -> !participant.playerId().equals(player.getUUID()))
                .forEach(participant -> notify(context, participant.playerId(),
                        player.getGameProfile().getName() + " se ha apuntado a la carrera.")));
        return 1;
    }

    /**
     * A solo run against the clock. There is nobody to wait for, so it goes straight to the grid
     * instead of sitting in a lobby waiting for a vote that can never carry.
     */
    private static int timeTrial(CommandContext<CommandSourceStack> context, Integer laps)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String trackName = StringArgumentType.getString(context, "circuito");

        KartsRaceManager.JoinResult result = KartsRaceManager.join(
                context.getSource().getServer(), player, trackName, laps,
                new TimeTrialMode(), null);
        if (result != KartsRaceManager.JoinResult.OK) {
            context.getSource().sendFailure(Component.literal(explain(result, trackName)));
            return 0;
        }
        KartsRaceManager.beginCountdown(trackName, RaceEngine.currentTick());
        player.sendSystemMessage(Component.literal(
                "Contrarreloj en '" + trackName + "'. ¡Suerte!").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int vote(CommandContext<CommandSourceStack> context, boolean voting)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!KartsRaceManager.vote(player.getUUID(), voting, RaceEngine.currentTick())) {
            context.getSource().sendFailure(Component.literal(
                    "No estás en ninguna carrera en espera."));
            return 0;
        }
        Optional<RaceSession> session = KartsRaceManager.raceOf(player.getUUID());
        session.ifPresent(active -> {
            int votes = active.race().votes();
            int needed = active.race().votesNeeded();
            String text = voting
                    ? player.getGameProfile().getName() + " está listo (" + votes + "/" + needed + ")."
                    : player.getGameProfile().getName() + " ya no está listo (" + votes + "/" + needed + ").";
            active.race().participants().forEach(participant ->
                    notify(context, participant.playerId(), text));
        });
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!KartsRaceManager.leave(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("No estás en ninguna carrera."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Has salido de la carrera."), false);
        return 1;
    }

    private static int removeOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "jugador");
        if (!KartsRaceManager.leave(player.getUUID())) {
            context.getSource().sendFailure(Component.literal(
                    player.getGameProfile().getName() + " no está en ninguna carrera."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                player.getGameProfile().getName() + " ha salido de la carrera."), true);
        return 1;
    }

    private static int control(CommandContext<CommandSourceStack> context, boolean immediate) {
        String trackName = StringArgumentType.getString(context, "circuito");
        boolean started = immediate
                ? KartsRaceManager.forceStart(trackName, RaceEngine.currentTick())
                : KartsRaceManager.beginCountdown(trackName, RaceEngine.currentTick());
        if (!started) {
            context.getSource().sendFailure(Component.literal(
                    "No hay una carrera en espera en '" + trackName + "'."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                immediate ? "Carrera iniciada." : "Cuenta atrás iniciada."), true);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        String trackName = StringArgumentType.getString(context, "circuito");
        if (!KartsRaceManager.cancel(trackName, "La carrera ha sido cancelada por un administrador.")) {
            context.getSource().sendFailure(Component.literal(
                    "No hay ninguna carrera en '" + trackName + "'."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Carrera cancelada."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<RaceSession> found = KartsRaceManager.raceOf(player.getUUID());
        if (found.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No estás en ninguna carrera."), false);
            return 0;
        }
        RaceCore race = found.get().race();
        RaceParticipantState participant = race.participant(player.getUUID());
        String summary = switch (race.phase()) {
            case LOBBY -> "Esperando jugadores: " + race.participants().size()
                    + ", votos " + race.votes() + "/" + race.votesNeeded();
            case COUNTDOWN -> "La carrera está a punto de empezar.";
            case RUNNING -> "Vuelta " + Math.min(participant.currentLap(), race.laps()) + "/" + race.laps()
                    + " — posición " + race.positionOf(player.getUUID()) + "/" + race.participants().size();
            case FINISHED -> "La carrera ha terminado.";
            case CANCELLED -> "La carrera fue cancelada.";
        };
        context.getSource().sendSuccess(() -> Component.literal(
                "[" + race.trackName() + "] " + summary).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int statusAll(CommandContext<CommandSourceStack> context) {
        var races = KartsRaceManager.activeRaces();
        if (races.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No hay carreras activas."), false);
            return 0;
        }
        for (RaceSession session : races) {
            RaceCore race = session.race();
            context.getSource().sendSuccess(() -> Component.literal(
                    " " + race.trackName() + " — " + race.phase() + ", "
                            + race.participants().size() + " jugadores"), false);
        }
        return races.size();
    }

    private static void notify(CommandContext<CommandSourceStack> context, UUID playerId, String message) {
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayer(playerId);
        if (target != null) {
            target.sendSystemMessage(Component.literal(message));
        }
    }

    private static String explain(KartsRaceManager.JoinResult result, String trackName) {
        return switch (result) {
            case NO_TRACK -> "No existe el circuito '" + trackName + "'.";
            case TRACK_NOT_RACEABLE -> "El circuito '" + trackName
                    + "' está incompleto; avisa a un administrador.";
            case ALREADY_RACING -> "Ya estás en una carrera.";
            case RACE_IN_PROGRESS -> "La carrera en '" + trackName + "' ya ha empezado.";
            case GRID_FULL -> "No quedan plazas en la parrilla de '" + trackName + "'.";
            case NO_VEHICLES -> "Karts desactivado: falta Immersive Vehicles.";
            case WRONG_DIMENSION -> "El circuito '" + trackName + "' está en una dimensión que no existe.";
            case OK -> "";
        };
    }
}
