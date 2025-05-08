package es.boffmedia.teras.net.server;

import com.google.common.base.Charsets;
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
    private String str;
    private ServerPlayerEntity player;

    public SMessageEncenderPC(String str){
        this.str = str;
    }
    
    @Override
    public void run() {
        try{
            UUID uuid = UUID.fromString(str);
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
        SMessageEncenderPC message = new SMessageEncenderPC(buf.toString(Charsets.UTF_8));
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
