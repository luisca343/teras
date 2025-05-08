package es.boffmedia.teras.net.client;


import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.RouteCreator;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageFindPath implements Runnable{
    private final RouteCreator.Point startPoint;
    private final RouteCreator.Point endPoint;
    public CMessageFindPath(RouteCreator.Point startPoint, RouteCreator.Point endPoint) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
    }

    public static void encode(CMessageFindPath message, PacketBuffer buffer) {
        buffer.writeInt(message.startPoint.getX());
        buffer.writeInt(message.startPoint.getZ());
        buffer.writeInt(message.endPoint.getX());
        buffer.writeInt(message.endPoint.getZ());
    }

    public static CMessageFindPath decode(PacketBuffer buffer) {
        RouteCreator.Point startPoint = new RouteCreator.Point(buffer.readInt(), buffer.readInt());
        RouteCreator.Point endPoint = new RouteCreator.Point(buffer.readInt(), buffer.readInt());
        return new CMessageFindPath(startPoint, endPoint);
    }


    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }

    @Override
    public void run() {
        Teras.getLogger().info("Finding path from " + startPoint + " to " + endPoint);
        RouteCreator.createRoute(startPoint, endPoint);
    }
}
