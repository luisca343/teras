package es.boffmedia.teras.net.client;


import es.boffmedia.teras.client.ClientProxy;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageMCEFResponse implements Runnable{
    private String json;
    private ServerPlayerEntity player;

    public CMessageMCEFResponse(String json){
        this.json = json;
    }

    @Override
    public void run() {
        ClientProxy.callbackMCEF.success(json);
    }

    public static CMessageMCEFResponse decode(PacketBuffer buf) {
        return new CMessageMCEFResponse(buf.readUtf(65536));
    }

    public void encode(PacketBuffer buf) {
        buf.writeUtf(json, 65536);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}
