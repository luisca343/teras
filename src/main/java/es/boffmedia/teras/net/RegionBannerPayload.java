package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: flash the region-enter cartel. Port of the 1.16.5 {@code CMessageCambioRegion},
 * fired by {@link es.boffmedia.teras.region.RegionBannerSender} when the tracker sees the player
 * enter a region — on 1.16.5 this trigger lived outside the mod (WorldGuard called an unwired stub).
 *
 * <p>{@code banner} is the cartel texture id ({@code textures/carteles/<banner>.png});
 * {@code holdSeconds} is how long the cartel stays fully visible between the slide transitions.</p>
 */
public record RegionBannerPayload(String banner, int holdSeconds) implements CustomPacketPayload {
    public static final Type<RegionBannerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "region_banner"));

    public static final StreamCodec<FriendlyByteBuf, RegionBannerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, RegionBannerPayload::banner,
                    ByteBufCodecs.VAR_INT, RegionBannerPayload::holdSeconds,
                    RegionBannerPayload::new);

    @Override
    public Type<RegionBannerPayload> type() {
        return TYPE;
    }
}
