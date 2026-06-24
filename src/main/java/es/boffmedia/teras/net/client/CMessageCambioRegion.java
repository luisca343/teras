package es.boffmedia.teras.net.client;


import es.boffmedia.teras.event.wungill.RegionEventsClient;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageCambioRegion implements Runnable{
    private String str;
    private ServerPlayerEntity player;

    public CMessageCambioRegion(String str){
        this.str = str;
    }

    @Override
    public void run() {
        RegionEventsClient.renderizarCartel(str, 2);
    }

    public static CMessageCambioRegion decode(PacketBuffer buf) {
        return new CMessageCambioRegion(buf.readUtf(256));
    }

    public void encode(PacketBuffer buf) {
        buf.writeUtf(str, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }

}
