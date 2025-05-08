package es.boffmedia.teras.net.client.clientOld;


import com.google.common.base.Charsets;
import com.google.gson.Gson;
import es.boffmedia.teras.util.objects._old.WayPoint;
import journeymap.client.waypoint.Waypoint;
import journeymap.client.waypoint.WaypointStore;
import journeymap.common.helper.DimensionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Collection;
import java.util.function.Supplier;

public class CMessageWaypoints implements Runnable{
    private String str;

    public CMessageWaypoints(String str){
        this.str = str;
    }

    @Override
    public void run() {
        Gson gson = new Gson();
        WayPoint data = gson.fromJson(str, WayPoint.class);
        ClientPlayerEntity player = Minecraft.getInstance().player;

        Collection<Waypoint> waypoints = WaypointStore.INSTANCE.getAll();
        if(!waypoints.stream().anyMatch(waypoint -> waypoint.getName().equals(data.getName()))){
            
            Waypoint wp = data.getWaypoint(DimensionHelper.getDimKeyName(Minecraft.getInstance().player.level.dimension()));
            WaypointStore.INSTANCE.add(wp);
            player.displayClientMessage(new StringTextComponent("Waypoint " + wp.getName() + " added"), true);
        }
        else{
            player.displayClientMessage(new StringTextComponent("Waypoint " + data.getName() + " already exists"), true);
        }
    }

    public static CMessageWaypoints decode(PacketBuffer buf) {
        CMessageWaypoints message = new CMessageWaypoints(buf.toString(Charsets.UTF_8));
        return message;
    }

    public void encode(PacketBuffer buf) {
        buf.writeCharSequence(str, Charsets.UTF_8);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }

}
