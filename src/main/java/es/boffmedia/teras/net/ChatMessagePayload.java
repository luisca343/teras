package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: a JSON string {@code {"message": "..."}} the server broadcasts as a system
 * message (OP-gated). Port of the 1.16.5 {@code SMessageChatMessage}.
 */
public record ChatMessagePayload(String json) implements CustomPacketPayload {
    public static final Type<ChatMessagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "chat_message"));

    public static final StreamCodec<FriendlyByteBuf, ChatMessagePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ChatMessagePayload::json, ChatMessagePayload::new);

    @Override
    public Type<ChatMessagePayload> type() {
        return TYPE;
    }
}
