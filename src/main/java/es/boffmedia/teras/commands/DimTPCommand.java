package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class DimTPCommand {
    public DimTPCommand(CommandDispatcher<CommandSource> dispatcher) {
        // Kamina es puto
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("dimtp")
                .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("dim", StringArgumentType.string())
                .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                .then(Commands.argument("z", IntegerArgumentType.integer())
                    .executes((command) -> {
                    ServerPlayerEntity player = (ServerPlayerEntity) EntityArgument.getPlayer(command, "player");
                    int x = IntegerArgumentType.getInteger(command, "x");
                    int y = IntegerArgumentType.getInteger(command, "y");
                    int z = IntegerArgumentType.getInteger(command, "z");

                    String dim = StringArgumentType.getString(command, "dim");
                    RegistryKey<World> world = RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(dim));

                    try {
                         ServerWorld serverWorld = player.getServer().getLevel(world);

                         player.teleportTo(serverWorld, x, y, z, player.yRot, player.xRot);
                         return 1;

                    } catch (Exception e) {
                         MessageHelper.enviarMensaje(player, "No existe la dimensión " + dim);
                         e.printStackTrace();
                    }
                       return 0;
                }))))));


        dispatcher.register(literalBuilder);

    }



}
