package es.boffmedia.teras.region.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.GpsPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * {@code /gps <x> <z>} starts a live GPS toward the given coordinates; the route follows the road
 * network and updates as the player moves. {@code /gps stop} ends it. The actual routing/drawing
 * happens on the player's client; this command only flips it on or off via {@link GpsPayload}.
 *
 * <p>Ported from the 1.16.5 {@code GpsCommand}, permission level and all.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class GpsCommand {
    private GpsCommand() {}

    private static final int PERMISSION_LEVEL = 3;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gps")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("stop")
                        .executes(GpsCommand::stop))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(GpsCommand::start))));
    }

    private static int start(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
        ServerPlayer player = command.getSource().getPlayerOrException();
        int x = IntegerArgumentType.getInteger(command, "x");
        int z = IntegerArgumentType.getInteger(command, "z");
        PacketDistributor.sendToPlayer(player, new GpsPayload(x, z, true));
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
        ServerPlayer player = command.getSource().getPlayerOrException();
        PacketDistributor.sendToPlayer(player, new GpsPayload(0, 0, false));
        return 1;
    }
}
