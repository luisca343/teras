package es.boffmedia.teras.net.server.serverOld;

import com.google.common.base.Charsets;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.clientOld.CMessageGetPC;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.montoyo.mcef.api.IJSQueryCallback;

import java.util.function.Supplier;

public class SMessageGetPC implements Runnable{
    private String str;
    private ServerPlayerEntity player;
    private IJSQueryCallback callback;

    public SMessageGetPC(String str){
        this.str = str;
    }

    @Override
    public void run() {
        PlayerPartyStorage storage = StorageProxy.getParty(player);
        PCStorage pc = StorageProxy.getPCForPlayer(player);

        CompoundNBT nbt = new CompoundNBT();
        pc.writeToNBT(nbt);
        
        Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageGetPC(nbt.toString()));
    }

    public static SMessageGetPC decode(PacketBuffer buf) {
        SMessageGetPC message = new SMessageGetPC(buf.toString(Charsets.UTF_8));
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
