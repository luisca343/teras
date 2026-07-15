package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: request the player's authoritative data. The server replies with a
 * {@link McefResponsePayload} that resolves the pending JS callback. Port of the 1.16.5
 * {@code SMessageDatosServer} request half.
 */
public record UserDataRequestPayload() implements CustomPacketPayload {
    public static final UserDataRequestPayload INSTANCE = new UserDataRequestPayload();

    public static final Type<UserDataRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "user_data_request"));

    public static final StreamCodec<FriendlyByteBuf, UserDataRequestPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<UserDataRequestPayload> type() {
        return TYPE;
    }
}
