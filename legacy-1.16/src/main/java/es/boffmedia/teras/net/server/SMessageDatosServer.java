package es.boffmedia.teras.net.server;

import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageDatosServer;
import es.boffmedia.teras.model.config.TerasConfig;
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
        return new SMessageDatosServer(buf.readUtf(256));
    }

    public void encode(PacketBuffer buf) {
        buf.writeUtf(str, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}

