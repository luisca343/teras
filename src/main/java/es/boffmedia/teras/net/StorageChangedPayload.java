package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: "your Pokémon storage changed; refetch it." Carries nothing — the page re-reads
 * {@code /pc} and {@code /equipo} over HTTP, and a payload describing the change would be a second
 * source of truth to keep in step with those.
 */
public record StorageChangedPayload() implements CustomPacketPayload {

    public static final StorageChangedPayload INSTANCE = new StorageChangedPayload();

    public static final Type<StorageChangedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "storage_changed"));

    public static final StreamCodec<FriendlyByteBuf, StorageChangedPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<StorageChangedPayload> type() {
        return TYPE;
    }
}
