package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client -> server: redeem what this player is owed from {@code source}, optionally narrowed to
 * {@code ids}. The server asks the backend what that is, grants it, and replies with a
 * {@link McefResponsePayload} echoing {@code requestId}.
 *
 * <p><b>It carries a source and row ids, never an item list.</b> That is the entire point. 1.16.5's
 * {@code SMessageDarCaja} took the items from the client and granted them, so a modified client could
 * mint anything; the backend is the authority now. The player is read off the connection, so the page
 * cannot claim for anyone else, and {@code ids} only <i>selects</i> among that player's own rows — it
 * cannot describe a reward. Mine sends an empty {@code ids} (claim the whole source); arcade names the
 * specific rows it is claiming.</p>
 */
public record DarCajaPayload(long requestId, String source, List<Integer> ids)
        implements CustomPacketPayload {
    public static final Type<DarCajaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dar_caja"));

    public static final StreamCodec<FriendlyByteBuf, DarCajaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, DarCajaPayload::requestId,
                    ByteBufCodecs.STRING_UTF8, DarCajaPayload::source,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), DarCajaPayload::ids,
                    DarCajaPayload::new);

    @Override
    public Type<DarCajaPayload> type() {
        return TYPE;
    }
}
