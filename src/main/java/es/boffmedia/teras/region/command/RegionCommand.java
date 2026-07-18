package es.boffmedia.teras.region.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.region.RegionStore;
import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.TerasRegion;
import es.boffmedia.teras.region.worldedit.SelectionResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /teras region} — the admin surface of the region system. Regions are created from the
 * caller's WorldEdit selection ({@code //wand} cuboid or {@code //sel poly} polygon), which is the
 * whole reason WorldEdit is a dependency; everything else here edits the stored catalog.
 *
 * <p>Polygon regions are stored full-height on purpose: a poly selection's Y range is just the Ys
 * the admin happened to click, and towns/roads always want the full column. Cuboids keep their
 * exact box ({@code //expand vert} first if a full column is wanted).</p>
 *
 * <p>WorldEdit is optional: {@link #fromSelection} checks {@code ModList} before any line that
 * names {@link es.boffmedia.teras.region.worldedit.WorldEditBridge}, so the class never links on
 * servers without it and the command explains what to install instead of crashing.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RegionCommand {
    private RegionCommand() {}

    private static final int PERMISSION_LEVEL = 2;

    // Leaflet-style defaults so freshly created regions are visible on maps before /teras region color.
    private static final int DEFAULT_FILL = 0x3388FF;
    private static final int DEFAULT_STROKE = 0x1144AA;

    private static final SimpleCommandExceptionType ERROR_NO_WORLDEDIT = new SimpleCommandExceptionType(
            Component.literal("WorldEdit no está instalado en este servidor; "
                    + "/teras region crear necesita una selección de WorldEdit (//wand o //sel poly)"));
    private static final SimpleCommandExceptionType ERROR_INCOMPLETE_SELECTION = new SimpleCommandExceptionType(
            Component.literal("Selección de WorldEdit incompleta: marca dos posiciones con //wand "
                    + "o define un polígono con //sel poly"));
    private static final DynamicCommandExceptionType ERROR_UNSUPPORTED_SELECTION = new DynamicCommandExceptionType(
            type -> Component.literal("Tipo de selección '" + type + "' no soportado: "
                    + "usa cuboide (//wand) o polígono (//sel poly)"));
    private static final DynamicCommandExceptionType ERROR_BAD_NAME = new DynamicCommandExceptionType(
            name -> Component.literal("Nombre de región inválido '" + name + "': solo minúsculas, "
                    + "números y '_' (es también el nombre del cartel y la clave de la web)"));
    private static final DynamicCommandExceptionType ERROR_EXISTS = new DynamicCommandExceptionType(
            name -> Component.literal("La región '" + name + "' ya existe; usa redefinir para cambiar su forma"));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_REGION = new DynamicCommandExceptionType(
            name -> Component.literal("No existe la región '" + name + "'"));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_FLAG = new DynamicCommandExceptionType(
            flag -> Component.literal("Flag desconocida '" + flag + "'; disponibles: "
                    + String.join(", ", Arrays.stream(RegionFlag.values()).map(RegionFlag::key).toList())));
    private static final DynamicCommandExceptionType ERROR_BAD_COLOR = new DynamicCommandExceptionType(
            value -> Component.literal("Color inválido '" + value + "': usa hex tipo #3388FF"));

    private static final SuggestionProvider<CommandSourceStack> REGION_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(RegionStore.names(), builder);
    private static final SuggestionProvider<CommandSourceStack> FLAG_KEYS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(RegionFlag.values()).map(RegionFlag::key), builder);
    private static final SuggestionProvider<CommandSourceStack> BANNER_VALUES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{TerasRegion.BANNER_NONE, "defecto"}, builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("teras")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("region")
                        .then(Commands.literal("crear")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .executes(RegionCommand::create)))
                        .then(Commands.literal("redefinir")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(REGION_NAMES)
                                        .executes(RegionCommand::redefine)))
                        .then(Commands.literal("borrar")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(REGION_NAMES)
                                        .executes(RegionCommand::delete)))
                        .then(Commands.literal("lista")
                                .executes(ctx -> list(ctx, null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(ctx -> list(ctx, DimensionArgument.getDimension(ctx, "dimension")
                                                .dimension().location().toString()))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(REGION_NAMES)
                                        .executes(RegionCommand::info)))
                        .then(Commands.literal("flag")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(REGION_NAMES)
                                        .then(Commands.argument("flag", StringArgumentType.word())
                                                .suggests(FLAG_KEYS)
                                                .then(Commands.literal("permitir")
                                                        .executes(ctx -> setFlag(ctx, Boolean.TRUE)))
                                                .then(Commands.literal("denegar")
                                                        .executes(ctx -> setFlag(ctx, Boolean.FALSE)))
                                                .then(Commands.literal("quitar")
                                                        .executes(ctx -> setFlag(ctx, null))))))
                        .then(Commands.literal("color")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(REGION_NAMES)
                                        .then(Commands.argument("relleno", StringArgumentType.word())
                                                .then(Commands.argument("borde", StringArgumentType.word())
                                                        .executes(RegionCommand::color)))))
                        .then(Commands.literal("cartel")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(REGION_NAMES)
                                        .then(Commands.argument("valor", StringArgumentType.word())
                                                .suggests(BANNER_VALUES)
                                                .executes(RegionCommand::banner))))
                        .then(Commands.literal("ruta")
                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                        .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                .executes(ctx -> route(ctx, ctx.getSource().getPlayerOrException()))
                                                                .then(Commands.argument("jugador", EntityArgument.player())
                                                                        .executes(ctx -> route(ctx,
                                                                                EntityArgument.getPlayer(ctx, "jugador")))))))))
                        .then(Commands.literal("recargar")
                                .executes(RegionCommand::reload))));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "nombre");
        if (!name.matches(TerasRegion.NAME_PATTERN)) throw ERROR_BAD_NAME.create(name);
        if (RegionStore.get(name) != null) throw ERROR_EXISTS.create(name);

        TerasRegion region = fromSelection(player, name);
        region.setFillColor(DEFAULT_FILL);
        region.setStrokeColor(DEFAULT_STROKE);
        region.setCreatedBy(player.getGameProfile().getName());
        region.setCreatedAt(System.currentTimeMillis());
        RegionStore.put(region);
        RegionStore.saveIfDirty();
        afterMutation();

        String shape = region.getShape() == TerasRegion.Shape.POLYGON
                ? "polígono de " + region.getPoints().size() + " puntos (altura completa)"
                : "cuboide " + describeCuboid(region);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Región '" + name + "' creada: " + shape + " en " + region.getDimension()), true);
        return 1;
    }

    private static int redefine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "nombre");
        TerasRegion existing = require(name);

        TerasRegion region = fromSelection(player, name);
        region.inheritSettingsFrom(existing);
        RegionStore.put(region);
        RegionStore.saveIfDirty();
        afterMutation();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Región '" + name + "' redefinida (colores, flags y cartel conservados)"), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nombre");
        if (!RegionStore.remove(name)) throw ERROR_UNKNOWN_REGION.create(name);
        RegionStore.saveIfDirty();
        afterMutation();
        ctx.getSource().sendSuccess(() -> Component.literal("Región '" + name + "' borrada"), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, String dimension) {
        var all = RegionStore.all().values().stream()
                .filter(r -> dimension == null || dimension.equals(r.getDimension()))
                .toList();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No hay regiones"
                    + (dimension != null ? " en " + dimension : "")), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(all.size() + " región(es):"), false);
        for (TerasRegion region : all) {
            String tag = region.isTown() ? " [pueblo]" : region.isRoad() ? " [carretera]" : "";
            String line = "- " + region.getName() + tag + ": "
                    + region.getShape().name().toLowerCase(Locale.ROOT) + " en " + region.getDimension();
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nombre");
        TerasRegion region = require(name);
        var source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("Región '" + name + "'"
                + (region.isTown() ? " [pueblo]" : region.isRoad() ? " [carretera]" : "")), false);
        source.sendSuccess(() -> Component.literal("  dimensión: " + region.getDimension()), false);
        if (region.getShape() == TerasRegion.Shape.CUBOID) {
            source.sendSuccess(() -> Component.literal("  forma: cuboide " + describeCuboid(region)), false);
        } else {
            String height = region.getMinY() == null && region.getMaxY() == null
                    ? "altura completa"
                    : "Y " + region.getMinY() + ".." + region.getMaxY();
            source.sendSuccess(() -> Component.literal("  forma: polígono de "
                    + region.getPoints().size() + " puntos, " + height), false);
        }
        source.sendSuccess(() -> Component.literal(String.format("  colores: relleno #%06X, borde #%06X",
                region.getFillColor(), region.getStrokeColor())), false);
        Map<String, Boolean> flags = region.getFlags();
        source.sendSuccess(() -> Component.literal("  flags: " + (flags == null || flags.isEmpty()
                ? "(ninguna; todo permitido)" : flags.toString()
                + " — denegar gana si hay regiones superpuestas")), false);
        String banner = region.bannerOrNull();
        source.sendSuccess(() -> Component.literal("  cartel: "
                + (banner == null ? "(ninguno)" : banner + ".png")), false);
        if (region.getCreatedBy() != null) {
            String date = Instant.ofEpochMilli(region.getCreatedAt())
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString();
            source.sendSuccess(() -> Component.literal("  creada por " + region.getCreatedBy()
                    + " el " + date), false);
        }
        return 1;
    }

    private static int setFlag(CommandContext<CommandSourceStack> ctx, Boolean value)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nombre");
        String flagKey = StringArgumentType.getString(ctx, "flag");
        TerasRegion region = require(name);
        RegionFlag flag = RegionFlag.fromKey(flagKey);
        if (flag == null) throw ERROR_UNKNOWN_FLAG.create(flagKey);
        region.setFlag(flag, value);
        RegionStore.put(region);
        RegionStore.saveIfDirty();
        afterMutation();
        String state = value == null ? "sin opinión" : value ? "permitido" : "denegado";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Flag '" + flagKey + "' de '" + name + "': " + state), true);
        return 1;
    }

    private static int color(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nombre");
        TerasRegion region = require(name);
        region.setFillColor(parseColor(StringArgumentType.getString(ctx, "relleno")));
        region.setStrokeColor(parseColor(StringArgumentType.getString(ctx, "borde")));
        RegionStore.put(region);
        RegionStore.saveIfDirty();
        afterMutation();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Colores de '%s': relleno #%06X, borde #%06X", name,
                region.getFillColor(), region.getStrokeColor())), true);
        return 1;
    }

    private static int banner(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nombre");
        String value = StringArgumentType.getString(ctx, "valor");
        TerasRegion region = require(name);
        if ("defecto".equals(value)) {
            region.setBanner(null);
        } else if (!value.equals(TerasRegion.BANNER_NONE) && !value.matches(TerasRegion.NAME_PATTERN)) {
            throw ERROR_BAD_NAME.create(value);
        } else {
            region.setBanner(value);
        }
        RegionStore.put(region);
        RegionStore.saveIfDirty();
        afterMutation();
        String resolved = region.bannerOrNull();
        ctx.getSource().sendSuccess(() -> Component.literal("Cartel de '" + name + "': "
                + (resolved == null ? "(ninguno)" : "textures/carteles/" + resolved + ".png")), true);
        return 1;
    }

    /** Dev/test trigger for the JourneyMap route drawing; the backend can reuse the same payload. */
    private static int route(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        var payload = new es.boffmedia.teras.net.RegionRoutePayload(
                IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "z1"),
                IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "z2"));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(target, payload);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Ruta enviada a " + target.getGameProfile().getName()), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        RegionStore.reload();
        afterMutation();
        int count = RegionStore.all().size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Regiones recargadas desde disco: " + count), true);
        return count;
    }

    /** Everything that must happen after the catalog changes: push the new list to every client. */
    private static void afterMutation() {
        es.boffmedia.teras.region.RegionSyncEvents.broadcast();
    }

    private static TerasRegion fromSelection(ServerPlayer player, String name)
            throws CommandSyntaxException {
        if (!ModList.get().isLoaded("worldedit")) throw ERROR_NO_WORLDEDIT.create();
        SelectionResult selection = es.boffmedia.teras.region.worldedit.WorldEditBridge
                .readSelection(player);
        switch (selection.status()) {
            case INCOMPLETE -> throw ERROR_INCOMPLETE_SELECTION.create();
            case UNSUPPORTED -> throw ERROR_UNSUPPORTED_SELECTION.create(selection.detail());
            case OK -> { }
        }
        String dimension = player.level().dimension().location().toString();
        return switch (selection.shape()) {
            case CUBOID -> TerasRegion.cuboid(name, dimension, selection.min(), selection.max());
            // Full-height policy: see class javadoc.
            case POLYGON -> TerasRegion.polygon(name, dimension, selection.points(), null, null);
        };
    }

    private static TerasRegion require(String name) throws CommandSyntaxException {
        TerasRegion region = RegionStore.get(name);
        if (region == null) throw ERROR_UNKNOWN_REGION.create(name);
        return region;
    }

    private static String describeCuboid(TerasRegion region) {
        return "(" + region.getMin().getX() + "," + region.getMin().getY() + "," + region.getMin().getZ()
                + ") a (" + region.getMax().getX() + "," + region.getMax().getY() + ","
                + region.getMax().getZ() + ")";
    }

    private static int parseColor(String value) throws CommandSyntaxException {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            throw ERROR_BAD_COLOR.create(value);
        }
    }
}
