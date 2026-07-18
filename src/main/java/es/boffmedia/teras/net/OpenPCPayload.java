package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: open the sender's Pokémon PC. The server replies with a {@link McefResponsePayload}
 * echoing {@code requestId}. Port of the 1.16.5 {@code SMessageEncenderPC}.
 *
 * <p>Carries no uuid, unlike its 1.16.5 original: the PC opened is always the connection's own, so a
 * client cannot ask for someone else's storage.</p>
 */
public record OpenPCPayload(long requestId) implements CustomPacketPayload {
    public static final Type<OpenPCPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "open_pc"));

    public static final StreamCodec<FriendlyByteBuf, OpenPCPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, OpenPCPayload::requestId,
                    OpenPCPayload::new);

    @Override
    public Type<OpenPCPayload> type() {
        return TYPE;
    }
}
