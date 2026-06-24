package es.boffmedia.teras.net.video;

import es.boffmedia.teras.client.ClientHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class FrameVideoMessage implements IMessage<FrameVideoMessage> {

    private BlockPos pos;
    private boolean playing;
    private int tick;

    private int sizeX;

    private int sizeY;

    private int posX;

    private int posY;

    private int canal;

    private String url;

    public FrameVideoMessage() {}

    public FrameVideoMessage(BlockPos pos, boolean playing, int tick, int sizeX, int sizeY, int posX, int posY, int canal, String url) {
        this.pos = pos;
        this.playing = playing;
        this.tick = tick;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.posX = posX;
        this.posY = posY;
        this.canal = canal;
        this.url = url;


    }

    @Override
    public void encode(FrameVideoMessage message, PacketBuffer buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeBoolean(message.playing);
        buffer.writeInt(message.tick);
        buffer.writeInt(message.sizeX);
        buffer.writeInt(message.sizeY);
        buffer.writeInt(message.posX);
        buffer.writeInt(message.posY);
        buffer.writeInt(message.canal);
        buffer.writeUtf(message.url);

    }

    @Override
    public FrameVideoMessage decode(PacketBuffer buffer) {
        return new FrameVideoMessage(buffer.readBlockPos(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readUtf(4096));
    }
    @Override
    public void handle(FrameVideoMessage message, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> ClientHandler.manageVideo(message.pos, message.playing, message.tick, message.sizeX, message.sizeY, message.posX, message.posY, message.canal, message.url));
        supplier.get().setPacketHandled(true);
    }
}