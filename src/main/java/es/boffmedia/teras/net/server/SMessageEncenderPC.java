package es.boffmedia.teras.net.server;

import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.comm.packetHandlers.OpenScreenPacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.pc.ClientChangeOpenPCPacket;
import com.pixelmonmod.pixelmon.enums.EnumGuiScreen;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.string.MessageHelper;
import es.boffmedia.teras.util.data.PersistentDataFields;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class SMessageEncenderPC implements Runnable{
    /** Hard cap on the inbound payload to avoid memory-amplification DoS. */
    private static final int MAX_LEN = 64;

    private String str;
    private ServerPlayerEntity player;

    public SMessageEncenderPC(String str){
        this.str = str;
    }

    @Override
    public void run() {
        if (player == null) {
            return;
        }
        try{
            // AUTHORITY: ignore the client-supplied UUID and always open the SENDER's own PC.
            // Trusting the client string would let a player open another player's PC storage.
            UUID uuid = player.getUUID();
            NetworkHelper.sendPacket(new ClientChangeOpenPCPacket(uuid), player);
            if(!player.getPersistentData().getBoolean(PersistentDataFields.FB_ACTIVO.label)){
                OpenScreenPacket.open(player, EnumGuiScreen.PC, new int[0]);
            } else {
                MessageHelper.enviarMensaje(player, "No puedes abrir el PC en el Frente Batalla");
            }
        }catch(Exception e){
            player.sendMessage(new StringTextComponent("Ha ocurrido un error"), UUID.randomUUID());
            Teras.getLogger().error("Error al abrir el PC: " + e.getMessage());
        }
    }

    public static SMessageEncenderPC decode(PacketBuffer buf) {
        return new SMessageEncenderPC(buf.readUtf(MAX_LEN));
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
