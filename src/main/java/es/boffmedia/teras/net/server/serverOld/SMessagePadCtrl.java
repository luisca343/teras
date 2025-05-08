package es.boffmedia.teras.net.server.serverOld;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class SMessagePadCtrl implements Runnable {

    private int id;
    private String url;
    private ServerPlayerEntity player;

    public SMessagePadCtrl() {
    }

    public SMessagePadCtrl(String url) {
        id = -1;
        this.url = url;
    }

    public SMessagePadCtrl(int id, String url) {
        this.id = id;
        this.url = url;
    }

    private boolean matchesMinePadID(ItemStack is) {
        return is.getItem() == ItemInit.SMARTROTOM.get() && is.getTag() != null && is.getTag().contains("PadID") && is.getTag().getInt("PadID") == id;
    }

    @Override
    public void run() {
        if(id < 0) {
            ItemStack is = player.getItemInHand(Hand.MAIN_HAND);

            if(is.getItem() == ItemInit.SMARTROTOM.get()) {
                if(url.isEmpty())
                    is.setTag(null); //Shutdown
                else {
                    if(is.getTag() == null)
                        is.setTag(new CompoundNBT());

                    if(!is.getTag().contains("PadID"))
                        is.getTag().putInt("PadID", Teras.getNextAvailablePadID());

                    is.getTag().putString("PadURL", Teras.applyBlacklist(url));
                }
            }
        } else {
            NonNullList<ItemStack> inv = player.inventory.items;
            ItemStack target = null;

            for(int i = 0; i < 9; i++) {
                if(matchesMinePadID(inv.get(i))) {
                    target = inv.get(i);
                    break;
                }
            }

            if(target == null && matchesMinePadID(player.inventory.offhand.get(0)))
                target = player.inventory.offhand.get(0);

            if(target != null)
                target.getTag().putString("PadURL", Teras.applyBlacklist(url));
        }
    }

    public static SMessagePadCtrl decode(PacketBuffer buf) {
        SMessagePadCtrl message = new SMessagePadCtrl();
        message.id = buf.readInt();
        message.url = buf.readUtf();
        return message;
    }

    public void encode(PacketBuffer buf) {
        buf.writeInt(id);
        buf.writeUtf(url);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork(this);
        contextSupplier.get().setPacketHandled(true);
    }

}
