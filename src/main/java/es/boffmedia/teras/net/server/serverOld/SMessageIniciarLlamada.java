package es.boffmedia.teras.net.server.serverOld;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageMCEFResponse;
import es.boffmedia.teras.util.objects.SmartRotomResponse;
import es.boffmedia.teras.util.objects._old.chatapp.CallData;
import es.boffmedia.teras.util.voicechat.CallManager;
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
        Gson gson = new Gson();
        CallData datosLlamada = gson.fromJson(str, CallData.class);
        System.out.println("Iniciando llamada. Miembros: " + datosLlamada.getUsers());
        if (player == null) {
            System.err.println("SMessageIniciarLlamada.run: player is null, aborting call start.");
            return;
        }

        String callId = datosLlamada == null ? null : datosLlamada.getCallId();
        if (callId == null || callId.isEmpty()) {
            System.err.println("SMessageIniciarLlamada.run: callId is null or empty, aborting call start.");
            return;
        }

        // Add current player to the call
        CallManager.joinCall(callId, player.getUUID());
        
        // Add all other participants to the call
        for (CallData.User user : datosLlamada.getUsers()) {
            try {
                UUID userUUID = UUID.fromString(user.getUuid());
                CallManager.joinCall(callId, userUUID);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid UUID for user: " + user.getUuid());
            }
        }

        SmartRotomResponse response = new SmartRotomResponse();
        response.setStatus(201);
        response.setMessage("Llamada iniciada - Puedes escuchar a los participantes y también a personas cercanas");
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
