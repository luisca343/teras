package es.boffmedia.teras.net.video;

import es.boffmedia.teras.tileentity.FrameBlockEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class UploadVideoUpdateMessage implements IMessage<UploadVideoUpdateMessage> {

    private BlockPos blockPos;
    private String url;
    private int volume;
    private boolean loop;
    private boolean isPlaying;
    private boolean reset;

    private int x;

    private int y;

    private int posX;

    private int posY;

    private int canal;

    public UploadVideoUpdateMessage() {}


    public UploadVideoUpdateMessage(BlockPos blockPos, String url, int volume, boolean loop, boolean isPlaying, boolean reset, int tempX, int tempY, int posX, int posY, int canal) {
        this.blockPos = blockPos;
        this.url = url;
        this.volume = volume;
        this.loop = loop;
        this.isPlaying = isPlaying;
        this.reset = reset;
        this.x = tempX;
        this.y = tempY;

        this.posX = posX;
        this.posY = posY;
        this.canal = canal;


    }


    @Override
    public void encode(UploadVideoUpdateMessage message, PacketBuffer buffer) {
        buffer.writeBlockPos(message.blockPos);
        buffer.writeUtf(message.url);
        buffer.writeInt(message.volume);
        buffer.writeBoolean(message.loop);
        buffer.writeBoolean(message.isPlaying);
        buffer.writeBoolean(message.reset);
        buffer.writeInt(message.x);
        buffer.writeInt(message.y);
        buffer.writeInt(message.posX);
        buffer.writeInt(message.posY);

        buffer.writeInt(message.canal);

    }

    @Override
    public UploadVideoUpdateMessage decode(PacketBuffer buffer) {
        return new UploadVideoUpdateMessage(buffer.readBlockPos(), buffer.readUtf(4096), buffer.readInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    @Override
    public void handle(UploadVideoUpdateMessage message, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();

            if (player == null) return;
            if (player.level.getBlockEntity(message.blockPos) instanceof FrameBlockEntity) {
                FrameBlockEntity frameBlockEntity = (FrameBlockEntity) player.level.getBlockEntity(message.blockPos);

                ScreenManager.addScreen(message.blockPos, message.canal);

                if (frameBlockEntity == null) return;

                frameBlockEntity.setBeingUsed(new UUID(0, 0));
                if (message.volume == -1) // NO UPDATE
                    return;

                frameBlockEntity.setUrl(message.url);
                frameBlockEntity.setVolume(message.volume);
                frameBlockEntity.setLoop(message.loop);
                frameBlockEntity.setPlaying(message.isPlaying);
                frameBlockEntity.setSizeX(message.x);
                frameBlockEntity.setSizeY(message.y);
                frameBlockEntity.setPosX(message.posX);
                frameBlockEntity.setPosY(message.posY);
                frameBlockEntity.setCanal(message.canal);

                frameBlockEntity.notifyPlayer();

                if (message.reset)
                    frameBlockEntity.setTick(0);
            }
        });
    }
}