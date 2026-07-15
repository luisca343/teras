package es.boffmedia.teras.net.both;

import es.boffmedia.teras.Teras;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageCarTest implements Runnable{
    private String test;
    private ServerPlayerEntity player;

    public MessageCarTest(String str){
        this.test = str;
    }

    @Override
    public void run() {
        Teras.raceManager.hitCar(player);

    }

    public static MessageCarTest decode(PacketBuffer buf) {
        return new MessageCarTest(buf.readUtf(256));
    }

    public void encode(PacketBuffer buf) {
        buf.writeUtf(test, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}
