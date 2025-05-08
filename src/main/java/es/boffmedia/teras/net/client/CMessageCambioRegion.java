package es.boffmedia.teras.net.client;


import com.google.common.base.Charsets;
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
        CMessageCambioRegion message = new CMessageCambioRegion(buf.toString(Charsets.UTF_8));
        return message;
    }

    public void encode(PacketBuffer buf) {
        buf.writeCharSequence(str, Charsets.UTF_8);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }

}
