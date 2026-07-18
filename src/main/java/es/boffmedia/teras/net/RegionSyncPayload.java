package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the FULL region catalog as the web-shaped JSON array
 * ({@link es.boffmedia.teras.region.RegionJson}). Sent on login and rebroadcast after every
 * mutation — full-list because the data is tiny and idempotent replacement removes delta-ordering
 * concerns. The client draws JourneyMap town overlays and road routes from it.
 *
 * <p>1.16.5 got the same array from the backend ({@code GET /regions}) over HTTP at login; now the
 * server of record is the mod, so it travels in-band.</p>
 */
public record RegionSyncPayload(String json) implements CustomPacketPayload {
    public static final Type<RegionSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "region_sync"));

    /** 1 MiB cap, not the 32 KiB {@code STRING_UTF8} default: hundreds of polygons must fit. */
    public static final StreamCodec<FriendlyByteBuf, RegionSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(1_048_576), RegionSyncPayload::json,
                    RegionSyncPayload::new);

    @Override
    public Type<RegionSyncPayload> type() {
        return TYPE;
    }
}
