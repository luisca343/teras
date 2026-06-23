package es.boffmedia.teras.net.server.serverOld;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import es.boffmedia.teras.TerasVoicechatPlugin;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageMCEFResponse;
import es.boffmedia.teras.util.objects.SmartRotomResponse;
import es.boffmedia.teras.util.objects.legacy.chatapp.CallData;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public class SMessageIniciarLlamada implements Runnable{
    private String str;
    private ServerPlayerEntity player;

    public SMessageIniciarLlamada(String str){
        this.str = str;
    }
    
    @Override
    public void run() {
        VoicechatServerApi api = TerasVoicechatPlugin.SERVER_API;
        Gson gson = new Gson();
        CallData datosLlamada = gson.fromJson(str, CallData.class);
        System.out.println("Miembros: "+datosLlamada.getUsers());

        /*
        if(datosLlamada.getUsers().size() <= 1){
            SmartRotomResponse response = new SmartRotomResponse();
            response.setStatus(200);
            response.setError("No hay suficientes miembros para iniciar la llamada");

            Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageMCEFResponse(new Gson().toJson(response)));
        }*/

        String idLlamada = datosLlamada.getCaller();
        Group group;
        if(player.getStringUUID().equals(idLlamada)){
            group = api.createGroup(idLlamada, null);
        } else {
            VoicechatConnection conn = api.getConnectionOf(UUID.fromString(idLlamada));
            group = conn.getGroup();
        }

        VoicechatConnection conn = api.getConnectionOf(player.getUUID());
        conn.setGroup(group);

        SmartRotomResponse response = new SmartRotomResponse();
        response.setStatus(201);
        response.setMessage("Llamada iniciada");
        response.setData(datosLlamada.getUsers().toString());

        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageMCEFResponse(new Gson().toJson(response)));

    }

    public static SMessageIniciarLlamada decode(PacketBuffer buf) {
        SMessageIniciarLlamada message = new SMessageIniciarLlamada(buf.toString(Charsets.UTF_8));
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

