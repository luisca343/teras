package es.boffmedia.teras.net.client.clientOld;


import com.google.common.base.Charsets;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects._old.misiones.DatosNPC;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import java.util.Map;
import java.util.function.Supplier;


public class CMessageVerVideo implements Runnable{
    private String str;
    private ServerPlayerEntity player;
    Map<String, DatosNPC> datosNpc;

    public CMessageVerVideo(String str){
        this.str = str;
    }

    @Override
    public void run() {
        Teras.PROXY.verVideo(str);
    }

    public static CMessageVerVideo decode(PacketBuffer buf) {
        CMessageVerVideo message = new CMessageVerVideo(buf.toString(Charsets.UTF_8));
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
