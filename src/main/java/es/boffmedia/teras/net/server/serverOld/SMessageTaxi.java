package es.boffmedia.teras.net.server.serverOld;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import es.boffmedia.teras.util.objects._old.Taxi;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.montoyo.mcef.api.IJSQueryCallback;

import java.util.function.Supplier;

public class SMessageTaxi implements Runnable{
    private String str;
    private ServerPlayerEntity player;
    private IJSQueryCallback callback;

    public SMessageTaxi(String str){
        this.str = str;
    }

    @Override
    public void run() {
        Gson gson = new Gson();
        Taxi taxi = gson.fromJson(str, Taxi.class);

        player.teleportTo(taxi.getX(), taxi.getY(), taxi.getZ());
    }

    public static SMessageTaxi decode(PacketBuffer buf) {
        SMessageTaxi message = new SMessageTaxi(buf.toString(Charsets.UTF_8));
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
