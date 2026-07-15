package es.boffmedia.teras.net.client;


import com.google.common.base.Charsets;
import es.boffmedia.teras.event.wungill.CarreraEventsClient;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageRacePositionChange implements Runnable{
    private int position;
    private ServerPlayerEntity player;

    public CMessageRacePositionChange(int position){
        this.position = position;
    }

    @Override
    public void run() {
        CarreraEventsClient.actualizarPosicion(position);
    }

    public static CMessageRacePositionChange decode(PacketBuffer buf) {
        CMessageRacePositionChange message = new CMessageRacePositionChange(buf.readInt());
        return message;
    }

    public void encode(PacketBuffer buf) {
        buf.writeInt(position);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }

}
