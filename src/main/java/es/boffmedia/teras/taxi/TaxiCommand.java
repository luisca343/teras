package es.boffmedia.teras.taxi;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /teras taxi} — authoring the SmartRotom taxi's destinations.
 *
 * <p>Stops are created by standing where the stop belongs, the way regions and kart circuits are
 * authored: a coordinate typed into a file is a coordinate nobody has stood on. The admin's facing
 * is stored with it, so passengers arrive looking the way the author was looking.</p>
 *
 * <p>Overworld only, and refused outright somewhere a passenger could not stand — the alternative is
 * an admin creating a stop that looks fine in chat and drops its first passenger into a wall.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class TaxiCommand {
    private TaxiCommand() {}

    private static final int PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("teras")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("taxi")
                        .then(Commands.literal("crear")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(TaxiCommand::create)))
                        .then(Commands.literal("listar").executes(TaxiCommand::list))
                        .then(Commands.literal("borrar")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(TaxiCommand::delete)))
                        .then(Commands.literal("ir")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> travel(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("jugador", EntityArgument.player())
                                                .executes(ctx -> travel(ctx,
                                                        EntityArgument.getPlayer(ctx, "jugador"))))))
                        .then(Commands.literal("recargar").executes(TaxiCommand::reload))));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");

        String problem = TaxiStop.idProblem(id);
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal("Id inválido: " + problem));
            return 0;
        }
        if (admin.level() != admin.server.overworld()) {
            // The web prices every fare from the player's live overworld x/z, so a stop anywhere else
            // would be sold at a distance that is not the distance travelled.
            ctx.getSource().sendFailure(Component.literal(
                    "Las paradas de taxi sólo pueden estar en el overworld."));
            return 0;
        }

        TaxiStop stop = new TaxiStop(id, admin.getX(), admin.getY(), admin.getZ(),
                admin.getYRot(), admin.getXRot());
        if (!TaxiTeleport.isSafeToCreate(admin.server.overworld(), stop)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Aquí no cabe un pasajero de pie (o el suelo no es sólido). "
                            + "Colócate donde quieras que aparezca la gente."));
            return 0;
        }

        boolean isNew = TaxiStore.put(stop);
        ctx.getSource().sendSuccess(() -> Component.literal(
                (isNew ? "Parada creada: " : "Parada actualizada: ") + stop.id()
                        + String.format(" (%.1f, %.1f, %.1f)", stop.x(), stop.y(), stop.z())), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<TaxiStop> stops = TaxiStore.all();
        if (stops.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No hay paradas. Colócate en una y usa /teras taxi crear <id>."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("Paradas de taxi (" + stops.size() + "):");
        for (TaxiStop stop : stops) {
            sb.append(String.format("%n  %s  (%.0f, %.0f, %.0f)",
                    stop.id(), stop.x(), stop.y(), stop.z()));
        }
        String message = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return stops.size();
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        if (!TaxiStore.remove(id)) {
            ctx.getSource().sendFailure(Component.literal("No existe la parada '" + id + "'."));
            return 0;
        }
        // Worth saying out loud: the web caches the stop list, so a passenger may still be looking at
        // a button for this stop for a few seconds. The route answers 404 for it, which is the case
        // the backend refunds.
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Parada borrada: " + id + ". Los viajes en curso hacia ella fallarán."), true);
        return 1;
    }

    /** The admin's own check that a stop is sane — and the in-game half of "mover a un jugador". */
    private static int travel(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        String id = StringArgumentType.getString(ctx, "id");
        TaxiStop stop = TaxiStore.find(id);
        if (stop == null) {
            ctx.getSource().sendFailure(Component.literal("No existe la parada '" + id + "'."));
            return 0;
        }
        TaxiTeleport.Result result = TaxiTeleport.travel(target, stop);
        if (result != TaxiTeleport.Result.OK) {
            ctx.getSource().sendFailure(Component.literal(switch (result) {
                case UNSAFE -> "La parada '" + id + "' ya no es segura: nadie cabe de pie ahí.";
                case BUSY -> target.getGameProfile().getName() + " está en una mazmorra.";
                default -> "No se pudo mover a " + target.getGameProfile().getName() + ".";
            }));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                target.getGameProfile().getName() + " → " + stop.id()), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        TaxiStore.reload();
        int count = TaxiStore.all().size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "taxi.json recargado: " + count + " parada(s)."), true);
        return count;
    }
}
