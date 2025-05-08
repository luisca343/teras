package es.boffmedia.teras.net.client.clientOld;


import com.google.common.base.Charsets;
import es.boffmedia.teras.event.wungill.CarreraEventsClient;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageCambioPosicion implements Runnable{
    private String str;
    private ServerPlayerEntity player;

    public CMessageCambioPosicion(String str){
        this.str = str;
    }

    @Override
    public void run() {
        CarreraEventsClient.actualizarPosicion(Integer.parseInt(str));
    }

    public static CMessageCambioPosicion decode(PacketBuffer buf) {
        CMessageCambioPosicion message = new CMessageCambioPosicion(buf.toString(Charsets.UTF_8));
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
