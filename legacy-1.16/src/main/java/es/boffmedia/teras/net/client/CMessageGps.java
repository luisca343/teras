package es.boffmedia.teras.net.client;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client packet that turns the live GPS on or off for the receiving
 * player. Only the destination travels over the wire; the start point is always
 * the player's current position, recomputed client-side each tick.
 */
public class CMessageGps {
    private final int x;
    private final int z;
    private final boolean active;

    public CMessageGps(int x, int z, boolean active) {
        this.x = x;
        this.z = z;
        this.active = active;
    }

    public static void encode(CMessageGps message, PacketBuffer buffer) {
        buffer.writeInt(message.x);
        buffer.writeInt(message.z);
        buffer.writeBoolean(message.active);
    }

    public static CMessageGps decode(PacketBuffer buffer) {
        return new CMessageGps(buffer.readInt(), buffer.readInt(), buffer.readBoolean());
    }

    public static void handle(CMessageGps message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Run on the client thread, and only load the client GPS class when on a client.
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (message.active) {
                es.boffmedia.teras.client.gps.GpsClient.INSTANCE.start(message.x, message.z);
            } else {
                es.boffmedia.teras.client.gps.GpsClient.INSTANCE.stop();
            }
        }));
        context.setPacketHandled(true);
    }
}
