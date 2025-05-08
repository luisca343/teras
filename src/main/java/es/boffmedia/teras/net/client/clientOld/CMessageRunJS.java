package es.boffmedia.teras.net.client.clientOld;


import com.google.common.base.Charsets;
import es.boffmedia.teras.Teras;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageRunJS implements Runnable{
    private String str;
    private PlayerEntity player;

    public CMessageRunJS(String str){
        this.str = str;
    }

    @Override
    public void run() {
        Teras.PROXY.runJS(str);
    }

    public static CMessageRunJS decode(PacketBuffer buf) {
        CMessageRunJS message = new CMessageRunJS(buf.toString(Charsets.UTF_8));
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
