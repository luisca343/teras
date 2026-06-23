package es.boffmedia.teras.net.server;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.comm.packetHandlers.OpenScreenPacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.pc.ClientChangeOpenPCPacket;
import com.pixelmonmod.pixelmon.enums.EnumGuiScreen;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.data.PersistentDataFields;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Util;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class SMessageChatMessage implements Runnable{
    /** Hard cap on the inbound payload to avoid memory-amplification DoS. */
    private static final int MAX_LEN = 4096;
    /** Permission level required to broadcast a server-wide system message (2 = OP/gamemaster). */
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    private String str;
    private ServerPlayerEntity player;

    public SMessageChatMessage(String str){
        this.str = str;
    }

    @Override
    public void run() {
        if (player == null) {
            return;
        }
        // AUTHORITY CHECK: only privileged players may broadcast a server-wide system message.
        // Without this, any modded client could spoof server announcements / spam every player.
        if (!player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            Teras.getLogger().warn("Player " + player.getGameProfile().getName()
                    + " attempted to broadcast a chat message without permission");
            return;
        }
        try {
            JsonObject jsonObject = new Gson().fromJson(str, JsonObject.class);
            String message = jsonObject.get("message").getAsString();
            player.getServer().getPlayerList().broadcastMessage(new StringTextComponent(message), ChatType.SYSTEM, Util.NIL_UUID);
        } catch (Exception e) {
            player.sendMessage(new StringTextComponent("Error parsing message"), Util.NIL_UUID);
            Teras.getLogger().error("Error parsing message: " + e.getMessage());
        }
    }

    public static SMessageChatMessage decode(PacketBuffer buf) {
        return new SMessageChatMessage(buf.readUtf(MAX_LEN));
    }

    public void encode(PacketBuffer buf) {
        buf.writeUtf(str, MAX_LEN);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}
