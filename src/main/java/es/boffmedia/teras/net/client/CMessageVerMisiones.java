package es.boffmedia.teras.net.client;


import com.google.common.base.Charsets;
import com.google.gson.Gson;
import es.boffmedia.teras.client.ClientProxy;
import es.boffmedia.teras.util.objects._old.misiones.QuestData;
import es.boffmedia.teras.util.objects._old.misiones.DatosNPC;
import es.boffmedia.teras.util.objects._old.misiones.Mision;
import es.boffmedia.teras.util.objects._old.misiones.MisionesJugador;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Comparator.comparing;

public class CMessageVerMisiones implements Runnable{
    private String str;
    private ServerPlayerEntity player;
    Map<String, DatosNPC> datosNpc;

    public CMessageVerMisiones(String str){
        this.str = str;
    }

    @Override
    public void run() {
        Gson gson = new Gson();
        MisionesJugador misionesJugador = gson.fromJson(str, MisionesJugador.class);
        //actualizar(misiones, misionesNuevas);

        Collections.sort(misionesJugador.getMisiones(), comparing(QuestData::getId));
        String res = gson.toJson(misionesJugador);
        ClientProxy.callbackMisiones.success(res);

    }

    public static CMessageVerMisiones decode(PacketBuffer buf) {
        CMessageVerMisiones message = new CMessageVerMisiones(buf.toString(Charsets.UTF_8));
        return message;
    }

    public void actualizar(ArrayList<Mision> listaAnterior, ArrayList<Mision> lista){
        for (Mision mision : listaAnterior) {
            if(datosNpc.containsKey(mision.getNombreNPC())){
                mision.setSkin(datosNpc.get(mision.getNombreNPC()).getSkin());
                mision.setX(datosNpc.get(mision.getNombreNPC()).getX());
                mision.setY(datosNpc.get(mision.getNombreNPC()).getY());
                mision.setZ(datosNpc.get(mision.getNombreNPC()).getZ());
            }
            lista.add(mision);
        }
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
