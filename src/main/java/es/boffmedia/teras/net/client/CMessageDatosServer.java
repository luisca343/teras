package es.boffmedia.teras.net.client;


import com.google.common.base.Charsets;
import com.google.gson.Gson;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.ClientProxy;
import es.boffmedia.teras.model.config.GetUserData;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CMessageDatosServer implements Runnable{
    private String idServer;
    private ServerPlayerEntity player;

    public CMessageDatosServer(String str){
        this.idServer = str;
    }

    @Override
    public void run() {
        //System.out.println("Recibido idServer: " + idServer);
        Teras.PROXY.setIdServidor(idServer);
        Gson gson = new Gson();
        String uuid = Minecraft.getInstance().player.getStringUUID();
        String nombre = Minecraft.getInstance().player.getName().getString();

        GetUserData userData = new GetUserData();
        userData.setWorld(idServer);
        userData.setUuid(uuid);
        userData.setUsername(nombre);
        userData.setX(Minecraft.getInstance().player.getX());
        userData.setY(Minecraft.getInstance().player.getY());
        userData.setZ(Minecraft.getInstance().player.getZ());

        String respuesta = gson.toJson(userData);

        //System.out.println("Enviando datos de usuario: " + respuesta);

        ClientProxy.callbackMisiones.success(respuesta);


        // Hacer el sistema de necesitar medallas para hacer misiones
        // Y el sistema de misiones que se activen en dÃ­as concretos
    }

    public static CMessageDatosServer decode(PacketBuffer buf) {
        CMessageDatosServer message = new CMessageDatosServer(buf.toString(Charsets.UTF_8));
        return message;
    }

    public void encode(PacketBuffer buf) {
        buf.writeCharSequence(idServer, Charsets.UTF_8);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}

