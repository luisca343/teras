package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: draw a road route from start to end on the JourneyMap fullscreen map. Port of
 * the 1.16.5 {@code CMessageFindPath}. Triggered by {@code /teras region ruta} (and eventually the
 * backend, through whatever server-side feature wants navigation).
 */
public record RegionRoutePayload(int startX, int startZ, int endX, int endZ)
        implements CustomPacketPayload {
    public static final Type<RegionRoutePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "region_route"));

    public static final StreamCodec<FriendlyByteBuf, RegionRoutePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RegionRoutePayload::startX,
                    ByteBufCodecs.VAR_INT, RegionRoutePayload::startZ,
                    ByteBufCodecs.VAR_INT, RegionRoutePayload::endX,
                    ByteBufCodecs.VAR_INT, RegionRoutePayload::endZ,
                    RegionRoutePayload::new);

    @Override
    public Type<RegionRoutePayload> type() {
        return TYPE;
    }
}
