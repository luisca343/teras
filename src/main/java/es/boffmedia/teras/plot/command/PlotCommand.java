package es.boffmedia.teras.plot.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.plot.PlotStore;
import es.boffmedia.teras.plot.model.PlotOwnership;
import es.boffmedia.teras.plot.model.PlotTransaction;
import es.boffmedia.teras.region.RegionStore;
import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /teras parcela} — the admin surface of the plot system. A plot is a region that has an
 * ownership row; this command is what creates, transfers and removes those rows. Geometry still
 * comes from {@code /teras region crear}, so the flow is: define the region, then list it here.
 *
 * <p>Player-facing purchase is deliberately absent — PLOTS.md §6 stage 6. Everything here spends
 * no money; {@code dueño} is an administrative grant and writes an {@code admin_grant} ledger row
 * so the audit trail still explains how ownership moved.</p>
 *
 * <p>Grants accept offline players via {@link GameProfileArgument}: an admin assigning a plot to
 * someone who is not connected is the normal case, not the exception.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class PlotCommand {
    private PlotCommand() {}

    private static final int PERMISSION_LEVEL = 2;

    /** Plots outrank the towns they sit in, or their flags could never be more permissive. */
    private static final int DEFAULT_PLOT_PRIORITY = 10;

    private static final SimpleCommandExceptionType ERROR_NO_DATABASE = new SimpleCommandExceptionType(
            Component.literal("La base de datos de parcelas no está disponible; "
                    + "revisa el log del servidor. No se concede nada que no quede guardado."));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_REGION = new DynamicCommandExceptionType(
            name -> Component.literal("No existe la región '" + name + "'; créala primero con "
                    + "/teras region crear"));
    private static final DynamicCommandExceptionType ERROR_NOT_A_PLOT = new DynamicCommandExceptionType(
            name -> Component.literal("La región '" + name + "' no es una parcela; "
                    + "ponla en venta con /teras parcela vender " + name + " <precio>"));
    private static final SimpleCommandExceptionType ERROR_AMBIGUOUS_PLAYER = new SimpleCommandExceptionType(
            Component.literal("Ese selector devuelve varios jugadores; una parcela tiene un solo dueño"));
    private static final SimpleCommandExceptionType ERROR_NO_PLAYER = new SimpleCommandExceptionType(
            Component.literal("Ese jugador no existe o nunca ha entrado en este servidor"));
    private static final DynamicCommandExceptionType ERROR_WRITE_FAILED = new DynamicCommandExceptionType(
            what -> Component.literal("No se pudo guardar (" + what + "); "
                    + "revisa el log del servidor. Nada ha cambiado."));

    private static final SuggestionProvider<CommandSourceStack> REGION_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(RegionStore.names(), builder);
    private static final SuggestionProvider<CommandSourceStack> PLOT_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(PlotStore.all().keySet(), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("teras")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("parcela")
                        .then(Commands.literal("vender")
                                .then(Commands.argument("region", StringArgumentType.word())
                                        .suggests(REGION_NAMES)
                                        .then(Commands.argument("precio", LongArgumentType.longArg(0))
                                                .executes(PlotCommand::sell))))
                        .then(Commands.literal("retirar")
                                .then(Commands.argument("region", StringArgumentType.word())
                                        .suggests(PLOT_NAMES)
                                        .executes(PlotCommand::withdraw)))
                        .then(Commands.literal("dueño")
                                .then(Commands.argument("region", StringArgumentType.word())
                                        .suggests(PLOT_NAMES)
                                        .then(Commands.argument("jugador", GameProfileArgument.gameProfile())
                                                .executes(PlotCommand::grant))))
                        .then(Commands.literal("revocar")
                                .then(Commands.argument("region", StringArgumentType.word())
                                        .suggests(PLOT_NAMES)
                                        .executes(PlotCommand::revoke)))
                        .then(Commands.literal("miembro")
                                .then(Commands.argument("region", StringArgumentType.word())
                                        .suggests(PLOT_NAMES)
                                        .then(Commands.literal("anadir")
                                                .then(Commands.argument("jugador", GameProfileArgument.gameProfile())
                                                        .executes(PlotCommand::addMember)))
                                        .then(Commands.literal("quitar")
                                                .then(Commands.argument("jugador", GameProfileArgument.gameProfile())
                                                        .executes(PlotCommand::removeMember)))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("region", StringArgumentType.word())
                                        .suggests(PLOT_NAMES)
                                        .executes(PlotCommand::info)))
                        .then(Commands.literal("lista")
                                .executes(PlotCommand::list))
                        .then(Commands.literal("recargar")
                                .executes(PlotCommand::reload))));
    }

    /**
     * Lists a region for sale, creating its ownership row. Also nudges priority up on first
     * listing: a plot at the default 0 would be shadowed flat by the town around it, which is the
     * one mistake that makes the whole feature look broken.
     */
    private static int sell(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireDatabase();
        String name = StringArgumentType.getString(ctx, "region");
        long price = LongArgumentType.getLong(ctx, "precio");
        TerasRegion region = requireRegion(name);

        boolean wasPlot = PlotStore.isPlot(name);
        if (!PlotStore.register(name, region.getDimension()).ok()) {
            throw ERROR_WRITE_FAILED.create("registro");
        }

        region.setPurchasable(true);
        region.setPrice(price);
        boolean raisedPriority = false;
        if (!wasPlot && region.getPriority() == 0) {
            region.setPriority(DEFAULT_PLOT_PRIORITY);
            raisedPriority = true;
        }
        saveRegion(region);

        String note = raisedPriority
                ? " (prioridad subida a " + DEFAULT_PLOT_PRIORITY + " para que mande sobre su pueblo)"
                : "";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Parcela '" + name + "' en venta por " + price + note), true);
        warnAboutShadowedProtections(ctx, region);
        return 1;
    }

    /**
     * Warns when the new plot's priority silently reopens a protection its town closes. Only
     * {@code pvp} and {@code explosions} can go quiet this way: the building flags are covered by
     * the plot's own deny-unless-owner rule, but these two are not, so shadowing a town that
     * denies them turns the plot into a hole in that protection with nothing in the output to say
     * so. Inheritance (PLOTS.md §6 stage 8) is the real fix; until then, say it out loud.
     */
    private static void warnAboutShadowedProtections(CommandContext<CommandSourceStack> ctx,
                                                     TerasRegion plot) {
        RegionPoint centre = plot.centroid();
        double y = plot.getMinY() != null ? plot.getMinY()
                : plot.getMin() != null ? plot.getMin().getY() : 64.0;
        for (RegionFlag flag : RegionFlag.values()) {
            if (flag.isOwnershipGated() || plot.deniesFlag(flag)) continue;
            for (TerasRegion other : RegionStore.all().values()) {
                if (other == plot || !other.getDimension().equals(plot.getDimension())) continue;
                if (other.getPriority() >= plot.getPriority()) continue;
                if (!other.deniesFlag(flag) || !other.contains(centre.getX(), y, centre.getZ())) continue;
                ctx.getSource().sendSuccess(() -> Component.literal("  ¡OJO! '" + other.getName()
                        + "' deniega '" + flag.key() + "' pero la parcela tiene más prioridad, así que "
                        + "ahí dentro vuelve a estar permitido. Si no quieres eso: "
                        + "/teras region flag " + plot.getName() + " " + flag.key() + " denegar"), false);
                break;
            }
        }
    }

    /** Withdraws the offer and the ownership row; the region itself and its ledger both survive. */
    private static int withdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireDatabase();
        String name = StringArgumentType.getString(ctx, "region");
        requirePlot(name);
        if (!PlotStore.unregister(name).ok()) throw ERROR_WRITE_FAILED.create("retirada");

        TerasRegion region = RegionStore.get(name);
        if (region != null) {
            region.setPurchasable(false);
            saveRegion(region);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("'" + name + "' ya no es una parcela; "
                + "vuelve a ser una región normal (el historial se conserva)"), true);
        // vender may have raised this; withdrawing cannot tell an auto-raise from a deliberate
        // one, so it says so instead of guessing.
        if (region != null && region.getPriority() != 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("  sigue con prioridad "
                    + region.getPriority() + "; cámbiala con /teras region prioridad " + name
                    + " 0 si ya no hace falta"), false);
        }
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireDatabase();
        String name = StringArgumentType.getString(ctx, "region");
        PlotOwnership plot = requirePlot(name);
        GameProfile profile = singleProfile(ctx);

        if (!PlotStore.setOwner(name, plot.dimension(), profile.getId(), null,
                PlotTransaction.Kind.ADMIN_GRANT, plot.owner(), 0).ok()) {
            throw ERROR_WRITE_FAILED.create("cambio de dueño");
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Parcela '" + name + "': dueño ahora "
                + profile.getName() + " (concesión de admin, sin coste)"), true);
        return 1;
    }

    private static int revoke(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireDatabase();
        String name = StringArgumentType.getString(ctx, "region");
        PlotOwnership plot = requirePlot(name);
        if (!plot.isOwned()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "La parcela '" + name + "' ya no tenía dueño"), false);
            return 0;
        }
        if (!PlotStore.setOwner(name, plot.dimension(), null, null,
                PlotTransaction.Kind.REVOKE, plot.owner(), 0).ok()) {
            throw ERROR_WRITE_FAILED.create("revocación");
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Parcela '" + name
                + "': dueño revocado y miembros borrados; vuelve a estar libre"), true);
        return 1;
    }

    private static int addMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireDatabase();
        String name = StringArgumentType.getString(ctx, "region");
        requirePlot(name);
        GameProfile profile = singleProfile(ctx);
        String addedBy = ctx.getSource().getTextName();
        if (!PlotStore.addMember(name, profile.getId(), addedBy).ok()) {
            throw ERROR_WRITE_FAILED.create("alta de miembro");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                profile.getName() + " ya puede construir en '" + name + "'"), true);
        return 1;
    }

    private static int removeMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireDatabase();
        String name = StringArgumentType.getString(ctx, "region");
        requirePlot(name);
        GameProfile profile = singleProfile(ctx);
        PlotStore.Result result = PlotStore.removeMember(name, profile.getId());
        if (result == PlotStore.Result.FAILED) throw ERROR_WRITE_FAILED.create("baja de miembro");
        if (result == PlotStore.Result.UNCHANGED) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    profile.getName() + " no era miembro de '" + name + "'"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                profile.getName() + " ya no puede construir en '" + name + "'"), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "region");
        PlotOwnership plot = requirePlot(name);
        TerasRegion region = RegionStore.get(name);
        var source = ctx.getSource();

        source.sendSuccess(() -> Component.literal("Parcela '" + name + "'"), false);
        source.sendSuccess(() -> Component.literal("  dimensión: " + plot.dimension()), false);
        if (region == null) {
            source.sendSuccess(() -> Component.literal(
                    "  ¡AVISO! no hay región con este nombre: la parcela no cubre nada"), false);
        } else {
            source.sendSuccess(() -> Component.literal("  prioridad: " + region.getPriority()
                    + (region.getPriority() == 0 ? " (¡la sombreará el pueblo que la contenga!)" : "")), false);
            source.sendSuccess(() -> Component.literal("  en venta: " + (region.isPurchasable()
                    ? "sí, por " + region.getPrice() : "no")), false);
        }
        if (plot.isOwned()) {
            String since = Instant.ofEpochMilli(plot.ownedSince())
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString();
            source.sendSuccess(() -> Component.literal("  dueño: " + describe(source.getServer(), plot.owner())
                    + " desde " + since), false);
        } else {
            source.sendSuccess(() -> Component.literal("  dueño: (libre)"), false);
        }
        if (plot.expiresAt() != null) {
            String until = Instant.ofEpochMilli(plot.expiresAt())
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString();
            source.sendSuccess(() -> Component.literal("  alquiler hasta " + until
                    + (plot.isExpired(System.currentTimeMillis()) ? " (CADUCADO)" : "")), false);
        }
        source.sendSuccess(() -> Component.literal("  miembros: " + (plot.members().isEmpty()
                ? "(ninguno)"
                : String.join(", ", plot.members().stream()
                        .map(uuid -> describe(source.getServer(), uuid)).toList()))), false);

        List<PlotTransaction> ledger = PlotStore.transactionsFor(name);
        if (ledger.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  historial: (vacío)"), false);
        } else {
            source.sendSuccess(() -> Component.literal("  historial (" + ledger.size() + "):"), false);
            for (PlotTransaction tx : ledger.stream().limit(5).toList()) {
                String date = Instant.ofEpochMilli(tx.createdAt())
                        .atZone(ZoneId.systemDefault()).toLocalDate().toString();
                String line = "    " + date + " " + (tx.kind() == null ? "?" : tx.kind().key())
                        + (tx.price() > 0 ? " por " + tx.price() : "")
                        + (tx.buyer() != null ? " a " + describe(source.getServer(), tx.buyer()) : "")
                        + (tx.isConfirmed() ? "" : " [sin confirmar en el backend]");
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        Collection<PlotOwnership> plots = PlotStore.all().values();
        if (plots.isEmpty()) {
            if (!PlotStore.isAvailable()) {
                source.sendSuccess(() -> Component.literal(
                        "No hay parcelas cargadas: la base de datos no está disponible"), false);
                return 0;
            }
            source.sendSuccess(() -> Component.literal("No hay parcelas"), false);
            explainHowToCreateOne(ctx);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(plots.size() + " parcela(s):"), false);
        for (PlotOwnership plot : plots) {
            TerasRegion region = RegionStore.get(plot.regionName());
            String state = plot.isOwned()
                    ? "de " + describe(source.getServer(), plot.owner())
                    : region != null && region.isPurchasable()
                            ? "en venta por " + region.getPrice()
                            : "libre, sin oferta";
            String line = "- " + plot.regionName() + ": " + state
                    + (plot.members().isEmpty() ? "" : " (+" + plot.members().size() + " miembros)");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return plots.size();
    }

    /**
     * Says the one thing the rest of the system trains admins not to expect: unlike {@code pueblo_}
     * and {@code carretera_}, the {@code parcela_} prefix carries no meaning. A plot is a region
     * with an ownership row, so a region called {@code parcela_x} stays an ordinary region until
     * {@code vender} creates that row — which is exactly the confusion this listing runs into.
     */
    private static void explainHowToCreateOne(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("Una región NO es una parcela por llamarse "
                + "'parcela_*': hay que ponerla en venta."), false);
        source.sendSuccess(() -> Component.literal(
                "  /teras parcela vender <región> <precio>"), false);

        List<String> candidates = RegionStore.all().values().stream()
                .filter(region -> !PlotStore.isPlot(region.getName()))
                .filter(region -> !region.isTown() && !region.isRoad())
                .map(TerasRegion::getName)
                .limit(10)
                .toList();
        if (!candidates.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Regiones que aún no son parcelas: "
                    + String.join(", ", candidates)), false);
        }
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        PlotStore.reload();
        int count = PlotStore.all().size();
        ctx.getSource().sendSuccess(() -> Component.literal(PlotStore.isAvailable()
                ? "Parcelas recargadas: " + count
                : "No se pudo abrir la base de datos; revisa el log"), true);
        return count;
    }

    private static void requireDatabase() throws CommandSyntaxException {
        if (!PlotStore.isAvailable()) throw ERROR_NO_DATABASE.create();
    }

    /** Region geometry is durable on return; a refused write must not look like success. */
    private static void saveRegion(TerasRegion region) throws CommandSyntaxException {
        if (!RegionStore.put(region)) throw ERROR_WRITE_FAILED.create("región");
    }

    private static TerasRegion requireRegion(String name) throws CommandSyntaxException {
        TerasRegion region = RegionStore.get(name);
        if (region == null) throw ERROR_UNKNOWN_REGION.create(name);
        return region;
    }

    private static PlotOwnership requirePlot(String name) throws CommandSyntaxException {
        PlotOwnership plot = PlotStore.get(name);
        if (plot == null) throw ERROR_NOT_A_PLOT.create(name);
        return plot;
    }

    /** A plot has exactly one owner, so a selector resolving to several players is an error. */
    private static GameProfile singleProfile(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "jugador");
        if (profiles.isEmpty()) throw ERROR_NO_PLAYER.create();
        if (profiles.size() > 1) throw ERROR_AMBIGUOUS_PLAYER.create();
        return profiles.iterator().next();
    }

    /** A player's name if the server still remembers the uuid, else the uuid itself. */
    private static String describe(MinecraftServer server, UUID uuid) {
        if (uuid == null) return "(nadie)";
        if (server == null) return uuid.toString();
        Optional<GameProfile> profile = server.getProfileCache() == null
                ? Optional.empty()
                : server.getProfileCache().get(uuid);
        return profile.map(GameProfile::getName).orElse(uuid.toString());
    }
}
