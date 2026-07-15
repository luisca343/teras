package es.boffmedia.teras.net.server;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.ChestCreationHelper;
import es.boffmedia.teras.model.world.DarCaja;
import es.boffmedia.teras.model.world.ObjetoMC;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class SMessageDarCaja implements Runnable {
    /** Hard cap on the inbound payload to avoid memory-amplification DoS. */
    private static final int MAX_LEN = 32768;

    private String str;
    private ServerPlayerEntity player;

    public SMessageDarCaja(String str) {
        this.str = str;
    }

    @Override
    public void run() {
        if (player == null) {
            return;
        }
        try {
            DarCaja darObjetos = Teras.GSON.fromJson(str, DarCaja.class);
            if (darObjetos == null) {
                return;
            }
            ArrayList<ObjetoMC> objetos = darObjetos.getObjetos();
            if (objetos == null || objetos.isEmpty()) {
                return;
            }
            Teras.getLogger().info("[audit] SMessageDarCaja: granting {} item(s) to {}",
                    objetos.size(), player.getGameProfile().getName());
            ChestCreationHelper.createAndGiveChests(player, objetos);
        } catch (Exception e) {
            Teras.getLogger().error("Error handling SMessageDarCaja from "
                    + player.getGameProfile().getName() + ": " + e.getMessage());
        }
    }

    public static SMessageDarCaja decode(PacketBuffer buf) {
        return new SMessageDarCaja(buf.readUtf(MAX_LEN));
    }

    public void encode(PacketBuffer buf) {
        buf.writeUtf(str == null ? "" : str, MAX_LEN);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}
