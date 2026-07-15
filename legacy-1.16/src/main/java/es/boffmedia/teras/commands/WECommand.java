package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.ForgePlayer;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.function.RegionMaskingFilter;
import com.sk89q.worldedit.function.biome.BiomeReplace;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.biome.BiomeType;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;

public class WECommand {

    public WECommand(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("wefix")
                .requires((commandSource -> commandSource.hasPermission(3)))
                .then(cambiarBioma())
                //.then(regenerar())
                ;

        dispatcher.register(literalBuilder);

    }

    private ArgumentBuilder<CommandSource,?> regenerar() {
        return Commands.literal("regenerar")
                .executes((command) -> {
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    SessionManager manager = WorldEdit.getInstance().getSessionManager();
                    ForgePlayer adaptedPlayer = ForgeAdapter.adaptPlayer(player);
                    EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(adaptedPlayer.getWorld(), -1);
                    LocalSession session = manager.get(adaptedPlayer);

                    long seed = player.getLevel().getSeed();

                    Mask mask = editSession.getMask();
                    World world = adaptedPlayer.getWorld();

                    Region region;

                    try {
                        region = session.getSelection(world);
                    } catch (IncompleteRegionException e) {
                        throw new RuntimeException(e);
                    }


                    boolean success;
                    try {
                        session.setMask((Mask)null);
                        RegenOptions options = RegenOptions.builder().seed(seed).regenBiomes(true).build();
                        success = world.regenerate(region, editSession, options);
                    } finally {
                        session.setMask(mask);
                    }

                    if (success) {
                        MessageHelper.enviarMensaje(player, "Regenerado");
                    } else {
                        MessageHelper.enviarMensaje(player, "Algo ha petado y no se ha regenerado correctamente :(");
                    }
                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource,?> cambiarBioma() {
        return Commands.literal("cambiarBioma")
                .then(Commands.argument("bioma", StringArgumentType.string())
                        .executes((command) -> {

                            ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                            SessionManager manager = WorldEdit.getInstance().getSessionManager();
                            ForgePlayer adaptedPlayer = ForgeAdapter.adaptPlayer(player);
                            EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(adaptedPlayer.getWorld(), -1);
                            LocalSession session = manager.get(adaptedPlayer);

                            String bioma = StringArgumentType.getString(command, "bioma");


                            BiomeType target = BiomeType.REGISTRY.get(bioma);

                            World world = adaptedPlayer.getWorld();
                            Mask mask = editSession.getMask();
                            Region region;

                            try {
                                region = session.getSelection(world);
                            } catch (IncompleteRegionException e) {
                                throw new RuntimeException(e);
                            }




                            RegionFunction replace = new BiomeReplace(editSession, target);
                            if (mask != null) {
                                replace = new RegionMaskingFilter(mask, (RegionFunction)replace);
                            }

                            RegionVisitor visitor = new RegionVisitor((Region)region, (RegionFunction)replace);
                            try {
                                Operations.completeLegacy(visitor);
                            } catch (MaxChangedBlocksException e) {
                                throw new RuntimeException(e);
                            }

                            MessageHelper.enviarMensaje(player, "Bioma cambiado");
                            return 1;
                        }));

    }
}
