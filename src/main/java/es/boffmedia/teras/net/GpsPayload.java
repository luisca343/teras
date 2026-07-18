package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: turns the live GPS on or off for the receiving player. Port of the 1.16.5
 * {@code CMessageGps}.
 *
 * <p>Only the destination travels over the wire; the start point is always the player's current
 * position, recomputed client-side as they move.</p>
 */
public record GpsPayload(int x, int z, boolean active) implements CustomPacketPayload {
    public static final Type<GpsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "gps"));

    public static final StreamCodec<FriendlyByteBuf, GpsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, GpsPayload::x,
                    ByteBufCodecs.INT, GpsPayload::z,
                    ByteBufCodecs.BOOL, GpsPayload::active,
                    GpsPayload::new);

    @Override
    public Type<GpsPayload> type() {
        return TYPE;
    }
}
