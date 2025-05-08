package es.boffmedia.teras.net.server;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import es.boffmedia.teras.util.ChestCreationHelper;
import es.boffmedia.teras.util.objects._old.mina.DarCaja;
import es.boffmedia.teras.util.objects._old.ObjetoMC;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class SMessageDarCaja implements Runnable {
    private String str;
    private ServerPlayerEntity player;

    public SMessageDarCaja(String str) {
        this.str = str;
    }
    
    @Override
    public void run() {
        Gson gson = new Gson();
        DarCaja darObjetos = gson.fromJson(str, DarCaja.class);
        ArrayList<ObjetoMC> objetos = darObjetos.getObjetos();
        
        // Use the helper class to create and give chests to the player
        ChestCreationHelper.createAndGiveChests(player, objetos);
    }

    public static SMessageDarCaja decode(PacketBuffer buf) {
        SMessageDarCaja message = new SMessageDarCaja(buf.toString(Charsets.UTF_8));
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