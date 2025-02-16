package es.boffmedia.teras.net.client;

import es.boffmedia.teras.Teras;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageDriftState {
    private final boolean isDrifting;
    private final boolean driftRight;

    public CMessageDriftState(boolean isDrifting, boolean driftRight) {
        this.isDrifting = isDrifting;
        this.driftRight = driftRight;
    }

    public static void encode(CMessageDriftState message, PacketBuffer buffer) {
        buffer.writeBoolean(message.isDrifting);
        buffer.writeBoolean(message.driftRight);
    }

    public static CMessageDriftState decode(PacketBuffer buffer) {
        return new CMessageDriftState(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(CMessageDriftState message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Make sure we're on the server side
            if (context.getDirection().getReceptionSide().isServer()) {
                Teras.raceManager.handleDriftInput(
                        context.getSender(),
                        message.isDrifting,
                        message.driftRight
                );
            }
        });
        context.setPacketHandled(true);
    }
}