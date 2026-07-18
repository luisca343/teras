package es.boffmedia.teras.karts.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import es.boffmedia.teras.karts.vehicle.KartSpec;
import es.boffmedia.teras.karts.vehicle.KartVehicleService;
import es.boffmedia.teras.karts.vehicle.KartVehicles;
import es.boffmedia.teras.karts.vehicle.VehicleItemRef;
import es.boffmedia.teras.karts.vehicle.VehicleRef;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * {@code /karts debug …} — exercises the Immersive Vehicles bridge by hand, so the spawn/seat/
 * freeze/release/remove path can be proven in a dev world before the race engine depends on it.
 *
 * <p>Operator-only and not part of the player-facing command surface; it is a diagnostic, kept
 * because "the kart did not appear" is otherwise very hard to attribute between a missing content
 * pack, a bad spec, and a bug in the bridge.</p>
 *
 * <p>Attached to the root by {@link KartsCommand}, which owns {@code /karts}.</p>
 */
public final class KartsDebugCommand {
    private KartsDebugCommand() {}

    /** The last kart each operator spawned, so follow-up subcommands need no id typed out. */
    private static final java.util.Map<java.util.UUID, VehicleRef> LAST_SPAWNED =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final SuggestionProvider<CommandSourceStack> KART_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    KartVehicles.get().listVehicleItems().stream().map(ref -> ref.spec().toId()),
                    builder);

    static void contribute(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("debug")
                .requires(source -> source.hasPermission(KartsCommand.PERMISSION_DEBUG))
                .then(Commands.literal("modelos")
                        .executes(KartsDebugCommand::listModels))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("kart", StringArgumentType.greedyString())
                                .suggests(KART_SUGGESTIONS)
                                .executes(KartsDebugCommand::spawn)))
                .then(Commands.literal("sentar")
                        .executes(KartsDebugCommand::seat))
                .then(Commands.literal("congelar")
                        .executes(context -> control(context, true)))
                .then(Commands.literal("soltar")
                        .executes(context -> control(context, false)))
                .then(Commands.literal("info")
                        .executes(KartsDebugCommand::info))
                .then(Commands.literal("borrar")
                        .executes(KartsDebugCommand::remove))
                .then(Commands.literal("carrera")
                        .executes(KartsDebugCommand::raceDiagnostics)));
    }

    private static int listModels(CommandContext<CommandSourceStack> context) {
        KartVehicleService service = KartVehicles.get();
        if (!requireIv(context, service)) {
            return 0;
        }
        List<VehicleItemRef> items = service.listVehicleItems();
        if (items.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "No hay modelos instalados. Immersive Vehicles no trae vehículos: instala un content pack."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Modelos disponibles (" + items.size() + "):").withStyle(ChatFormatting.GOLD), false);
        items.stream().limit(50).forEach(item -> context.getSource().sendSuccess(() -> Component.literal(
                " " + item.spec().toId() + " — " + item.displayName()).withStyle(ChatFormatting.GRAY), false));
        if (items.size() > 50) {
            context.getSource().sendSuccess(() -> Component.literal(
                    " … y " + (items.size() - 50) + " más").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return items.size();
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KartVehicleService service = KartVehicles.get();
        if (!requireIv(context, service)) {
            return 0;
        }
        KartSpec spec = KartSpec.parse(StringArgumentType.getString(context, "kart"));
        if (spec == null) {
            context.getSource().sendFailure(Component.literal(
                    "Formato inválido. Usa pack:modelo o pack:modelo:variante."));
            return 0;
        }
        Optional<VehicleRef> spawned = service.spawn(player.serverLevel(),
                es.boffmedia.teras.karts.vehicle.KartLoadout.of(spec), player,
                player.getX(), player.getY(), player.getZ(), player.getYRot());
        if (spawned.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "No se pudo crear " + spec.toId() + ". ¿Está instalado ese content pack?"));
            return 0;
        }
        LAST_SPAWNED.put(player.getUUID(), spawned.get());
        context.getSource().sendSuccess(() -> Component.literal(
                "Kart " + spec.toId() + " creado.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int seat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        VehicleRef ref = requireLast(context, player);
        if (ref == null) {
            return 0;
        }
        if (!KartVehicles.get().seat(ref, player)) {
            context.getSource().sendFailure(Component.literal("No se pudo sentar al jugador en el kart."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Sentado.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int control(CommandContext<CommandSourceStack> context, boolean freeze)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        VehicleRef ref = requireLast(context, player);
        if (ref == null) {
            return 0;
        }
        if (freeze) {
            KartVehicles.get().freeze(ref);
        } else {
            KartVehicles.get().release(ref);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                freeze ? "Kart congelado." : "Kart liberado.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        KartVehicleService service = KartVehicles.get();
        VehicleRef ref = service.vehicleOf(player).orElseGet(() -> LAST_SPAWNED.get(player.getUUID()));
        if (ref == null) {
            context.getSource().sendFailure(Component.literal("No montas ningún kart ni has creado uno."));
            return 0;
        }
        String spec = service.specOf(ref).map(KartSpec::toId).orElse("desconocido");
        String pos = service.positionOf(ref)
                .map(v -> String.format("%.1f %.1f %.1f", v.x, v.y, v.z))
                .orElse("desconocida");
        boolean exists = service.exists(ref);
        context.getSource().sendSuccess(() -> Component.literal(
                "Kart " + spec + " | pos " + pos + " | existe: " + exists), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        VehicleRef ref = requireLast(context, player);
        if (ref == null) {
            return 0;
        }
        KartVehicles.get().remove(ref);
        LAST_SPAWNED.remove(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Kart borrado.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Why a lap is or is not counting, for the caller's current race.
     *
     * <p>Checkpoint detection has three independent ways to fail silently — the kart's position not
     * reaching the engine at all, the gate being somewhere other than where the kart drives, and the
     * gates being in an order nobody drives them in — and from inside the game they look identical.
     * This prints all three at once.</p>
     */
    private static int raceDiagnostics(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var source = context.getSource();

        var found = es.boffmedia.teras.karts.engine.KartsRaceManager.raceOf(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No estás en ninguna carrera."));
            return 0;
        }
        var session = found.get();
        var race = session.race();
        var participant = race.participant(player.getUUID());

        source.sendSuccess(() -> Component.literal("=== Diagnóstico de carrera ===")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(
                " Circuito: " + race.trackName() + " | estado: " + race.phase()
                        + " | vuelta " + participant.currentLap() + "/" + race.laps()), false);

        // 1. Is the kart's position reaching the engine at all?
        KartVehicleService service = KartVehicles.get();
        Optional<VehicleRef> riding = service.vehicleOf(player);
        if (riding.isEmpty()) {
            source.sendFailure(Component.literal(
                    " ✗ No estás montado en ningún kart: el motor no recibe tu posición, así que "
                            + "ningún checkpoint puede contar."));
            return 0;
        }
        Optional<net.minecraft.world.phys.Vec3> kartPos = service.positionOf(riding.get());
        if (kartPos.isEmpty()) {
            source.sendFailure(Component.literal(
                    " ✗ Montas un kart pero no se puede leer su posición (¿chunk descargado?)."));
            return 0;
        }
        var at = kartPos.get();
        source.sendSuccess(() -> Component.literal(String.format(
                " ✓ Posición del kart: %.2f %.2f %.2f", at.x, at.y, at.z))
                .withStyle(ChatFormatting.GREEN), false);

        // 2. Where is the gate it is waiting for, and is the kart's height inside its band?
        var track = race.track();
        if (participant.checkpointIndex() >= track.checkpoints().size()) {
            source.sendFailure(Component.literal(" ✗ Índice de checkpoint fuera de rango."));
            return 0;
        }
        var gate = track.checkpoints().get(participant.checkpointIndex());
        int gateNumber = participant.checkpointIndex() + 1;
        source.sendSuccess(() -> Component.literal(String.format(
                " Esperando checkpoint %d/%d — caja X[%.1f, %.1f] Y[%.1f, %.1f] Z[%.1f, %.1f]",
                gateNumber, track.checkpoints().size(),
                gate.minX(), gate.maxX(), gate.minY(), gate.maxY(), gate.minZ(), gate.maxZ())), false);

        boolean yInside = at.y >= gate.minY() && at.y <= gate.maxY();
        if (!yInside) {
            source.sendFailure(Component.literal(String.format(
                    " ✗ La altura del kart (%.2f) está fuera de la banda Y del checkpoint "
                            + "[%.1f, %.1f]. Es la causa más común: al seleccionar con WorldEdit se "
                            + "suelen marcar los bloques del suelo, y el centro del coche va por "
                            + "encima. Rehaz el checkpoint seleccionando también el aire por donde "
                            + "pasa el coche.", at.y, gate.minY(), gate.maxY()))
                    .withStyle(ChatFormatting.RED));
        } else {
            source.sendSuccess(() -> Component.literal(" ✓ La altura del kart entra en la banda Y."),
                    false);
        }

        double dx = Math.max(0, Math.max(gate.minX() - at.x, at.x - gate.maxX()));
        double dz = Math.max(0, Math.max(gate.minZ() - at.z, at.z - gate.maxZ()));
        double flatDistance = Math.sqrt(dx * dx + dz * dz);
        source.sendSuccess(() -> Component.literal(String.format(
                " Distancia horizontal al checkpoint: %.1f bloques", flatDistance)), false);

        source.sendSuccess(() -> Component.literal(
                " Recuerda: los checkpoints se cruzan en orden, y el último es la meta.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static boolean requireIv(CommandContext<CommandSourceStack> context, KartVehicleService service) {
        if (service.available()) {
            return true;
        }
        context.getSource().sendFailure(Component.literal(
                "Karts desactivado: falta Immersive Vehicles."));
        return false;
    }

    private static VehicleRef requireLast(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (!requireIv(context, KartVehicles.get())) {
            return null;
        }
        VehicleRef ref = LAST_SPAWNED.get(player.getUUID());
        if (ref == null) {
            context.getSource().sendFailure(Component.literal(
                    "No has creado ningún kart en esta sesión. Usa /karts debug spawn primero."));
        }
        return ref;
    }
}
