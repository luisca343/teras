package es.boffmedia.teras.net.client.clientOld;


import com.google.common.base.Charsets;
import es.boffmedia.teras.util.objects._old.misiones.DatosNPC;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;


public class CMessagePrepararNavegador implements Runnable{
    private String str;
    private ServerPlayerEntity player;
    Map<String, DatosNPC> datosNpc;

    public CMessagePrepararNavegador(String str){
        this.str = str;
    }

    @Override
    public void run() {
        //Teras.PROXY.prepararNavegador();
    }

    public static CMessagePrepararNavegador decode(PacketBuffer buf) {
        CMessagePrepararNavegador message = new CMessagePrepararNavegador(buf.toString(Charsets.UTF_8));
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
