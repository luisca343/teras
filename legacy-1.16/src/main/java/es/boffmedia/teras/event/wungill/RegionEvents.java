package es.boffmedia.teras.event.wungill;

import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageCambioRegion;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.UUID;


public class RegionEvents {

    public static void onEnter(UUID uuid, String str){
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageCambioRegion(str));
    }

    public static  void onExit(UUID uuid, String str){
        // ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        // Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageCambioRegion(str));
    }


}
