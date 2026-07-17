package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: a JSON response that resolves the client's pending {@code mcefQuery} callback.
 * Port of the 1.16.5 {@code CMessageMCEFResponse}. The generic async-reply mechanism reused by any
 * query that needs a server round-trip.
 *
 * <p>{@code requestId} echoes the id the client generated for the originating request, so the
 * response routes back to the exact callback that issued it — several async queries (e.g.
 * {@code getUserData} and {@code getSpawns}) can be in flight at once without colliding. (1.16.5
 * avoided collisions by using separate static callbacks per query; the id map is the general fix.)</p>
 *
 * <p>{@code ok} carries success/failure explicitly, rather than the client sniffing it out of
 * {@code json}: {@link es.boffmedia.teras.client.ClientNetHandler} resolves the JS promise's
 * {@code onSuccess} when true and {@code onFailure} when false. This is what stops a failed grant from
 * reading as a successful one — a server-side error must surface as a rejected {@code mcefQuery}, not
 * an {@code {status:"error"}} body the page has to remember to inspect. Build them through
 * {@link #ok(long, String)} / {@link #error(long, String)}.</p>
 */
public record McefResponsePayload(long requestId, boolean ok, String json) implements CustomPacketPayload {
    public static final Type<McefResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "mcef_response"));

    public static final StreamCodec<FriendlyByteBuf, McefResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, McefResponsePayload::requestId,
                    ByteBufCodecs.BOOL, McefResponsePayload::ok,
                    ByteBufCodecs.STRING_UTF8, McefResponsePayload::json,
                    McefResponsePayload::new);

    /** A successful reply — resolves the page's {@code onSuccess} with {@code json}. */
    public static McefResponsePayload ok(long requestId, String json) {
        return new McefResponsePayload(requestId, true, json);
    }

    /** A failed reply — rejects the page's promise via {@code onFailure}, carrying {@code json} as the reason. */
    public static McefResponsePayload error(long requestId, String json) {
        return new McefResponsePayload(requestId, false, json);
    }

    @Override
    public Type<McefResponsePayload> type() {
        return TYPE;
    }
}
