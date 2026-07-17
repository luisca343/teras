package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: apply this configuration to the picture frame at {@code pos}. The client screen
 * always sends the full field set (seeded from the frame's current synced state), so a partial edit
 * never resets the rest. The server validates permission and proximity before writing; it then
 * re-syncs to every watcher through the block entity's update packet.
 *
 * <p>Too many fields for {@code StreamCodec.composite}, so the codec is written by hand. The url is
 * length-capped to keep a crafted packet from allocating an unbounded string.</p>
 */
public record FrameConfigPayload(BlockPos pos, String url, float minX, float minY, float maxX, float maxY,
                                 float rotation, boolean flipX, boolean flipY, boolean bothSides,
                                 float brightness, float alpha, int renderDistance,
                                 float volume, float minAudioDistance, float maxAudioDistance,
                                 boolean loop, boolean playing, boolean muted, boolean lit, boolean showFrame,
                                 byte anchorH, byte anchorV) implements CustomPacketPayload {

    public static final int MAX_URL_LENGTH = 1024;

    public static final Type<FrameConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "frame_config"));

    public static final StreamCodec<FriendlyByteBuf, FrameConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeUtf(p.url(), MAX_URL_LENGTH);
                buf.writeFloat(p.minX());
                buf.writeFloat(p.minY());
                buf.writeFloat(p.maxX());
                buf.writeFloat(p.maxY());
                buf.writeFloat(p.rotation());
                buf.writeBoolean(p.flipX());
                buf.writeBoolean(p.flipY());
                buf.writeBoolean(p.bothSides());
                buf.writeFloat(p.brightness());
                buf.writeFloat(p.alpha());
                buf.writeVarInt(p.renderDistance());
                buf.writeFloat(p.volume());
                buf.writeFloat(p.minAudioDistance());
                buf.writeFloat(p.maxAudioDistance());
                buf.writeBoolean(p.loop());
                buf.writeBoolean(p.playing());
                buf.writeBoolean(p.muted());
                buf.writeBoolean(p.lit());
                buf.writeBoolean(p.showFrame());
                buf.writeByte(p.anchorH());
                buf.writeByte(p.anchorV());
            },
            buf -> new FrameConfigPayload(
                    buf.readBlockPos(),
                    buf.readUtf(MAX_URL_LENGTH),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readByte(), buf.readByte()));

    @Override
    public Type<FrameConfigPayload> type() {
        return TYPE;
    }
}
