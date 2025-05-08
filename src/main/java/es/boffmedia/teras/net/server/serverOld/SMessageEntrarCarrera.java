package es.boffmedia.teras.net.server.serverOld;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import es.boffmedia.teras.util.objects._old.karts.EntrarCarrera;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class SMessageEntrarCarrera implements Runnable{
    private String str;
    private ServerPlayerEntity player;

    public SMessageEntrarCarrera(String str){
        this.str = str;
    }
    
    @Override
    public void run() {
        Gson gson = new Gson();
        EntrarCarrera datos = gson.fromJson(str, EntrarCarrera.class);

    }

    public static SMessageEntrarCarrera decode(PacketBuffer buf) {
        SMessageEntrarCarrera message = new SMessageEntrarCarrera(buf.toString(Charsets.UTF_8));
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
