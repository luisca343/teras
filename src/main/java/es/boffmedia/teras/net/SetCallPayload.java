package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: place the sender in the SimpleVoiceChat group for {@code chatId} (start/join a
 * ChatApp call). The server replies with a {@link McefResponsePayload} echoing {@code requestId}.
 * Port of the 1.16.5 {@code SMessageIniciarLlamada}.
 *
 * <p>Only {@code chatId} crosses the wire — the group is keyed on it, and the participant is taken from
 * the connection, never the page. 1.16.5 sent the whole {@code CallData} (users + a client-supplied
 * caller UUID) and joined "that caller's current group", which is exactly the client-trust the port
 * drops. The {@code caller}/{@code users} the web still sends are ignored server-side.</p>
 */
public record SetCallPayload(long requestId, String chatId) implements CustomPacketPayload {
    public static final Type<SetCallPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "set_call"));

    public static final StreamCodec<FriendlyByteBuf, SetCallPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, SetCallPayload::requestId,
                    ByteBufCodecs.STRING_UTF8, SetCallPayload::chatId,
                    SetCallPayload::new);

    @Override
    public Type<SetCallPayload> type() {
        return TYPE;
    }
}
