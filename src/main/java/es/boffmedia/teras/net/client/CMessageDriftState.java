package es.boffmedia.teras.net.client;

import es.boffmedia.teras.Teras;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageDriftState {
    private final boolean isDrifting;
    private final boolean driftRight;
    private final float vehicleYaw;

    public CMessageDriftState(boolean isDrifting, boolean driftRight, float vehicleYaw) {
        this.isDrifting = isDrifting;
        this.driftRight = driftRight;
        this.vehicleYaw = vehicleYaw;
    }

    public static void encode(CMessageDriftState message, PacketBuffer buffer) {
        buffer.writeBoolean(message.isDrifting);
        buffer.writeBoolean(message.driftRight);
        buffer.writeFloat(message.vehicleYaw);
    }

    public static CMessageDriftState decode(PacketBuffer buffer) {
        return new CMessageDriftState(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readFloat()
        );
    }

    public static void handle(CMessageDriftState message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Make sure we're on the server side
            if (context.getDirection().getReceptionSide().isServer()) {
                Teras.raceManager.handleDriftInput(
                        context.getSender(),
                        message.isDrifting,
                        message.driftRight,
                        message.vehicleYaw
                );
            }
        });
        context.setPacketHandled(true);
    }
}