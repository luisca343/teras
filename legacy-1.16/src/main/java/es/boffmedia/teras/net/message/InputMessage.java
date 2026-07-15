package es.boffmedia.teras.net.message;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Util;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class InputMessage {
    public int key;
    public InputMessage(){

    }

    public InputMessage(int key){
        this.key = key;
    }

    public static void encode(InputMessage message, PacketBuffer buffer){
        buffer.writeInt(message.key);
    }

    public static InputMessage decode(PacketBuffer buffer){
        return new InputMessage(buffer.readInt());
    }

    public static void handle(InputMessage message, Supplier<NetworkEvent.Context> contextSupplier){
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayerEntity player = context.getSender();
            player.addItem(new ItemStack(Items.DIAMOND,1));
            StringTextComponent msg = new StringTextComponent("tsdert");

            player.sendMessage(msg, ChatType.SYSTEM, Util.NIL_UUID);
            World world = player.level;

        });
        context.setPacketHandled(true);
    }
}
