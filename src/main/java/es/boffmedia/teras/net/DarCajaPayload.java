package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: redeem whatever this player is owed from {@code source}. The server asks the
 * backend what that is, grants it, and replies with a {@link McefResponsePayload} echoing
 * {@code requestId}.
 *
 * <p><b>It carries a source, never an item list.</b> That is the entire point. 1.16.5's
 * {@code SMessageDarCaja} took the items from the client and granted them, so a modified client could
 * mint anything; the backend is the authority now. The player is read off the connection, so the page
 * cannot claim for anyone else — between them, the page contributes nothing an attacker could forge
 * beyond <i>which ledger</i> to redeem.</p>
 */
public record DarCajaPayload(long requestId, String source) implements CustomPacketPayload {
    public static final Type<DarCajaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dar_caja"));

    public static final StreamCodec<FriendlyByteBuf, DarCajaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, DarCajaPayload::requestId,
                    ByteBufCodecs.STRING_UTF8, DarCajaPayload::source,
                    DarCajaPayload::new);

    @Override
    public Type<DarCajaPayload> type() {
        return TYPE;
    }
}
