package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageGps;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.PacketDistributor;

/**
 * {@code /gps <x> <z>} starts a live GPS toward the given coordinates; the route
 * follows the road network and updates as the player moves. {@code /gps stop}
 * ends it. The actual routing/drawing happens on the player's client; this
 * command only flips it on or off via {@link CMessageGps}.
 */
public class GpsCommand {

    public GpsCommand(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("gps")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("stop")
                        .executes(this::stop))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(this::start))));
    }

    private int start(CommandContext<CommandSource> command) throws CommandSyntaxException {
        ServerPlayerEntity player = command.getSource().getPlayerOrException();
        int x = IntegerArgumentType.getInteger(command, "x");
        int z = IntegerArgumentType.getInteger(command, "z");
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageGps(x, z, true));
        return 1;
    }

    private int stop(CommandContext<CommandSource> command) throws CommandSyntaxException {
        ServerPlayerEntity player = command.getSource().getPlayerOrException();
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageGps(0, 0, false));
        return 1;
    }
}
