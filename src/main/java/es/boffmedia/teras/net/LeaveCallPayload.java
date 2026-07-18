package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: remove the sender from any SimpleVoiceChat group (leave a ChatApp call). The server
 * replies with a {@link McefResponsePayload} echoing {@code requestId}. Port of the 1.16.5
 * {@code SMessageFinalizarLlamada}.
 *
 * <p>Carries no call data: the participant comes from the connection, and leaving clears whatever group
 * they are in, so nothing else is needed.</p>
 */
public record LeaveCallPayload(long requestId) implements CustomPacketPayload {
    public static final Type<LeaveCallPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "leave_call"));

    public static final StreamCodec<FriendlyByteBuf, LeaveCallPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, LeaveCallPayload::requestId,
                    LeaveCallPayload::new);

    @Override
    public Type<LeaveCallPayload> type() {
        return TYPE;
    }
}
