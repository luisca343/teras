package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: a live playback command for the frame at {@code pos}. Distinct from
 * {@link FrameConfigPayload} (which edits the frame) — these drive the shared cinema clock, so the
 * server re-anchors playback and every viewer follows. {@code arg} is the target milliseconds for
 * {@link #SEEK}, ignored otherwise.
 */
public record FramePlaybackPayload(BlockPos pos, byte action, long arg) implements CustomPacketPayload {

    public static final byte PLAY = 0;
    public static final byte PAUSE = 1;
    public static final byte STOP = 2;
    public static final byte SEEK = 3;

    public static final Type<FramePlaybackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "frame_playback"));

    public static final StreamCodec<FriendlyByteBuf, FramePlaybackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, FramePlaybackPayload::pos,
                    ByteBufCodecs.BYTE, FramePlaybackPayload::action,
                    ByteBufCodecs.VAR_LONG, FramePlaybackPayload::arg,
                    FramePlaybackPayload::new);

    @Override
    public Type<FramePlaybackPayload> type() {
        return TYPE;
    }
}
