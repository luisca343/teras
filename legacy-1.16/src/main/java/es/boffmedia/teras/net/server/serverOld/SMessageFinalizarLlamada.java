package es.boffmedia.teras.net.server.serverOld;

import com.google.gson.Gson;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import es.boffmedia.teras.TerasVoicechatPlugin;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageMCEFResponse;
import es.boffmedia.teras.util.objects.SmartRotomResponse;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.function.Supplier;

public class SMessageFinalizarLlamada implements Runnable{
    private String str;
    private ServerPlayerEntity player;

    public SMessageFinalizarLlamada(String str){
        this.str = str;
    }
    
    @Override
    public void run() {
        VoicechatServerApi SERVER_API = TerasVoicechatPlugin.SERVER_API;
        VoicechatConnection conn = SERVER_API.getConnectionOf(player.getUUID());

        conn.setGroup(null);

        SmartRotomResponse response = new SmartRotomResponse();
        response.setStatus(200);
        response.setMessage("Llamada finalizada");

        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageMCEFResponse(new Gson().toJson(response)));

    }

    public static SMessageFinalizarLlamada decode(PacketBuffer buf) {
        return new SMessageFinalizarLlamada(buf.readUtf(256));
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
