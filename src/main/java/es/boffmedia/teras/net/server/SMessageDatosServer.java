package es.boffmedia.teras.net.server;

import com.google.common.base.Charsets;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageDatosServer;
import es.boffmedia.teras.util.objects._old.serverdata.TerasConfig;
import es.boffmedia.teras.util.file.FileHelper;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import java.util.function.Supplier;

public class SMessageDatosServer implements Runnable{
    private String str;
    private ServerPlayerEntity player;

    public SMessageDatosServer(String str){
        this.str = str;
    }

    @Override
    public void run() {
        TerasConfig terasConfig = FileHelper.getConfig();
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageDatosServer(terasConfig.getId()));
    }

    public static SMessageDatosServer decode(PacketBuffer buf) {
        SMessageDatosServer message = new SMessageDatosServer(buf.toString(Charsets.UTF_8));
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
