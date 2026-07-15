package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: a JSON response that resolves the client's pending {@code mcefQuery} callback.
 * Port of the 1.16.5 {@code CMessageMCEFResponse} (which called
 * {@code ClientProxy.callbackMCEF.success(json)}). The generic async-reply mechanism reused by any
 * query that needs a server round-trip.
 */
public record McefResponsePayload(String json) implements CustomPacketPayload {
    public static final Type<McefResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "mcef_response"));

    public static final StreamCodec<FriendlyByteBuf, McefResponsePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, McefResponsePayload::json, McefResponsePayload::new);

    @Override
    public Type<McefResponsePayload> type() {
        return TYPE;
    }
}
