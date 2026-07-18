package es.boffmedia.teras.karts.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import es.boffmedia.teras.karts.engine.KartsRaceManager;
import es.boffmedia.teras.karts.engine.RaceSession;
import es.boffmedia.teras.karts.store.GarageStore;
import es.boffmedia.teras.karts.store.KartPresetStore;
import es.boffmedia.teras.karts.store.KartSelectionStore;
import es.boffmedia.teras.karts.vehicle.KartLoadout;
import es.boffmedia.teras.karts.vehicle.KartSpec;
import es.boffmedia.teras.karts.vehicle.KartVehicles;
import es.boffmedia.teras.karts.vehicle.VehicleRef;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Commands for the three ways a racer gets a kart: admin-defined presets, curated selections, and
 * each player's own garage.
 */
public final class KartProvisioningCommands {
    private KartProvisioningCommands() {}

    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(KartPresetStore.names(), builder);
    private static final SuggestionProvider<CommandSourceStack> SELECTION_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(KartSelectionStore.names(), builder);
    private static final SuggestionProvider<CommandSourceStack> MODEL_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    KartVehicles.get().listVehicleItems().stream().map(item -> item.spec().toId()), builder);
    /** What the caller may pick right now: their race's selection, or their own garage. */
    private static final SuggestionProvider<CommandSourceStack> CHOICE_SUGGESTIONS = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        List<String> options = KartsRaceManager.raceOf(player.getUUID())
                .map(session -> session.resolver().optionsFor(player.getUUID()))
                .orElse(List.of());
        return SharedSuggestionProvider.suggest(options, builder);
    };

    static void contribute(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("elegir")
                .then(Commands.argument("kart", StringArgumentType.word())
                        .suggests(CHOICE_SUGGESTIONS)
                        .executes(KartProvisioningCommands::choose)));

        root.then(Commands.literal("garaje")
                .executes(KartProvisioningCommands::listGarage)
                .then(Commands.literal("elegir")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(KartProvisioningCommands::setPreferred))));

        root.then(Commands.literal("preset")
                .requires(source -> source.hasPermission(KartsCommand.PERMISSION_ADMIN))
                .then(Commands.literal("crear")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .executes(context -> createPresetFromRiddenKart(context))
                                .then(Commands.argument("kart", StringArgumentType.greedyString())
                                        .suggests(MODEL_SUGGESTIONS)
                                        .executes(KartProvisioningCommands::createPresetFromId))))
                .then(Commands.literal("listar").executes(KartProvisioningCommands::listPresets))
                .then(Commands.literal("eliminar")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .suggests(PRESET_SUGGESTIONS)
                                .executes(KartProvisioningCommands::deletePreset))));

        root.then(Commands.literal("seleccion")
                .requires(source -> source.hasPermission(KartsCommand.PERMISSION_ADMIN))
                .then(Commands.literal("crear")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .then(Commands.argument("presets", StringArgumentType.greedyString())
                                        .executes(KartProvisioningCommands::createSelection))))
                .then(Commands.literal("listar").executes(KartProvisioningCommands::listSelections))
                .then(Commands.literal("eliminar")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .suggests(SELECTION_SUGGESTIONS)
                                .executes(KartProvisioningCommands::deleteSelection))));

        root.then(Commands.literal("admin")
                .requires(source -> source.hasPermission(KartsCommand.PERMISSION_ADMIN))
                .then(Commands.literal("garaje")
                        .then(Commands.literal("dar")
                                .then(Commands.argument("jugador", EntityArgument.player())
                                        .then(Commands.argument("kart", StringArgumentType.greedyString())
                                                .suggests(MODEL_SUGGESTIONS)
                                                .executes(KartProvisioningCommands::giveKart))))
                        .then(Commands.literal("quitar")
                                .then(Commands.argument("jugador", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(KartProvisioningCommands::takeKart)))))
                .then(Commands.literal("limpiarvehiculos")
                        .executes(KartProvisioningCommands::cullVehicles)
                        .then(Commands.literal("olvidar")
                                .executes(KartProvisioningCommands::forgetVehicles))));
    }

    // --- player choice ----------------------------------------------------------------------

    private static int choose(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String choice = StringArgumentType.getString(context, "kart");

        Optional<RaceSession> session = KartsRaceManager.raceOf(player.getUUID());
        if (session.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No estás en ninguna carrera."));
            return 0;
        }
        if (!session.get().resolver().choose(player.getUUID(), choice)) {
            List<String> options = session.get().resolver().optionsFor(player.getUUID());
            context.getSource().sendFailure(Component.literal(options.isEmpty()
                    ? "En esta carrera no puedes elegir kart."
                    : "Opciones: " + String.join(", ", options)));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Kart elegido: " + choice)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int listGarage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<GarageStore.GarageEntry> owned = GarageStore.listFor(player.getUUID());
        if (owned.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Tu garaje está vacío."), false);
            return 0;
        }
        Optional<GarageStore.GarageEntry> preferred = GarageStore.preferred(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Tu garaje:")
                .withStyle(ChatFormatting.GOLD), false);
        for (GarageStore.GarageEntry entry : owned) {
            boolean isPreferred = preferred.isPresent() && preferred.get().id().equals(entry.id());
            context.getSource().sendSuccess(() -> Component.literal(
                    " [" + entry.id() + "] " + entry.name() + (isPreferred ? " (por defecto)" : ""))
                    .withStyle(isPreferred ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
        return owned.size();
    }

    private static int setPreferred(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "id");
        if (!GarageStore.setPreferred(player.getUUID(), id)) {
            context.getSource().sendFailure(Component.literal("No tienes ningún kart con id '" + id + "'."));
            return 0;
        }
        GarageStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal("Kart por defecto actualizado.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // --- presets ----------------------------------------------------------------------------

    private static int createPresetFromRiddenKart(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<VehicleRef> riding = KartVehicles.get().vehicleOf(player);
        if (riding.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "No estás montado en ningún vehículo. Móntate en el kart que quieras guardar, "
                            + "o indica el modelo: /karts preset crear <nombre> <pack:modelo>"));
            return 0;
        }
        Optional<KartLoadout> loadout = KartVehicles.get().loadoutOf(riding.get());
        if (loadout.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No se pudo identificar ese vehículo."));
            return 0;
        }
        return savePreset(context, StringArgumentType.getString(context, "nombre"), loadout.get());
    }

    private static int createPresetFromId(CommandContext<CommandSourceStack> context) {
        KartSpec spec = KartSpec.parse(StringArgumentType.getString(context, "kart"));
        if (spec == null) {
            context.getSource().sendFailure(Component.literal(
                    "Formato inválido. Usa pack:modelo o pack:modelo:variante."));
            return 0;
        }
        return savePreset(context, StringArgumentType.getString(context, "nombre"), KartLoadout.of(spec));
    }

    private static int savePreset(CommandContext<CommandSourceStack> context, String name,
                                  KartLoadout loadout) {
        if (!KartVehicles.get().isInstalled(loadout.model())) {
            context.getSource().sendFailure(Component.literal(
                    "No hay ningún vehículo instalado con id " + loadout.model().toId() + "."));
            return 0;
        }
        KartPresetStore.put(new KartPresetStore.Preset(name, loadout, name));
        KartPresetStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal(
                "Preset '" + name + "' guardado (" + loadout.describe() + ").")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listPresets(CommandContext<CommandSourceStack> context) {
        List<KartPresetStore.Preset> presets = KartPresetStore.all();
        if (presets.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "No hay presets. Crea uno con /karts preset crear <nombre>."), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Presets:")
                .withStyle(ChatFormatting.GOLD), false);
        for (KartPresetStore.Preset preset : presets) {
            boolean installed = KartVehicles.get().isInstalled(preset.spec());
            context.getSource().sendSuccess(() -> Component.literal(
                    " " + preset.name() + " — " + preset.loadout().describe()
                            + (installed ? "" : " (pack no instalado)"))
                    .withStyle(installed ? ChatFormatting.GRAY : ChatFormatting.RED), false);
        }
        return presets.size();
    }

    private static int deletePreset(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "nombre");
        if (!KartPresetStore.remove(name)) {
            context.getSource().sendFailure(Component.literal("No existe el preset '" + name + "'."));
            return 0;
        }
        KartPresetStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal("Preset '" + name + "' eliminado."), true);
        return 1;
    }

    // --- selections -------------------------------------------------------------------------

    private static int createSelection(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "nombre");
        String[] requested = StringArgumentType.getString(context, "presets").trim().split("\\s+");

        List<String> valid = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String preset : requested) {
            if (KartPresetStore.exists(preset)) {
                valid.add(preset);
            } else {
                missing.add(preset);
            }
        }
        if (!missing.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "Estos presets no existen: " + String.join(", ", missing)));
            return 0;
        }
        KartSelectionStore.put(name, valid);
        KartSelectionStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal(
                "Selección '" + name + "' guardada con " + valid.size() + " karts.")
                .withStyle(ChatFormatting.GREEN), true);
        return valid.size();
    }

    private static int listSelections(CommandContext<CommandSourceStack> context) {
        var names = KartSelectionStore.names();
        if (names.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No hay selecciones."), false);
            return 0;
        }
        for (String name : names) {
            context.getSource().sendSuccess(() -> Component.literal(
                    " " + name + " — " + String.join(", ", KartSelectionStore.get(name)))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return names.size();
    }

    private static int deleteSelection(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "nombre");
        if (!KartSelectionStore.remove(name)) {
            context.getSource().sendFailure(Component.literal("No existe la selección '" + name + "'."));
            return 0;
        }
        KartSelectionStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal("Selección '" + name + "' eliminada."), true);
        return 1;
    }

    // --- admin garage -----------------------------------------------------------------------

    private static int giveKart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
        KartSpec spec = KartSpec.parse(StringArgumentType.getString(context, "kart"));
        if (spec == null || !KartVehicles.get().isInstalled(spec)) {
            context.getSource().sendFailure(Component.literal("Ese kart no existe o no está instalado."));
            return 0;
        }
        GarageStore.GarageEntry entry = GarageStore.add(
                target.getUUID(), spec.systemName(), KartLoadout.of(spec), "admin");
        GarageStore.saveIfDirty();

        target.sendSystemMessage(Component.literal(
                "Has recibido un kart: " + entry.name() + " [" + entry.id() + "]")
                .withStyle(ChatFormatting.GREEN));
        context.getSource().sendSuccess(() -> Component.literal(
                "Kart añadido al garaje de " + target.getGameProfile().getName()
                        + " con id " + entry.id() + "."), true);
        return 1;
    }

    private static int takeKart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
        String id = StringArgumentType.getString(context, "id");
        if (!GarageStore.remove(target.getUUID(), id)) {
            context.getSource().sendFailure(Component.literal(
                    target.getGameProfile().getName() + " no tiene ningún kart con id '" + id + "'."));
            return 0;
        }
        GarageStore.saveIfDirty();
        context.getSource().sendSuccess(() -> Component.literal("Kart eliminado del garaje."), true);
        return 1;
    }

    private static int cullVehicles(CommandContext<CommandSourceStack> context) {
        int removed = es.boffmedia.teras.karts.store.RaceVehicleLedger.cullAll();
        int pending = es.boffmedia.teras.karts.store.RaceVehicleLedger.tracked().size();
        context.getSource().sendSuccess(() -> Component.literal(
                "Karts de carrera eliminados: " + removed
                        + (pending > 0 ? ". Quedan " + pending
                        + " sin localizar (probablemente en chunks descargados); vuelve a "
                        + "ejecutarlo tras recorrer el circuito." : ".")), true);
        return removed;
    }

    private static int forgetVehicles(CommandContext<CommandSourceStack> context) {
        int forgotten = es.boffmedia.teras.karts.store.RaceVehicleLedger.forgetAll();
        context.getSource().sendSuccess(() -> Component.literal(
                "Registro de karts vaciado (" + forgotten + " entradas). "
                        + "Los karts que sigan en el mundo habrá que borrarlos a mano."), true);
        return forgotten;
    }
}
