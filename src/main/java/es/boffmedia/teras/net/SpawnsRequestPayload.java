package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: request the Pokémon spawns available around the player right now. The server
 * scans the player's Pixelmon spawner and replies with a {@link McefResponsePayload} echoing
 * {@code requestId} and carrying a JSON array of {@code PokedexSpawnChance}
 * (dex/species/form/palette/rarity/percentage), which resolves the pending JS callback. Port of the
 * 1.16.5 {@code SMessageCheckSpawns} request half.
 */
public record SpawnsRequestPayload(long requestId) implements CustomPacketPayload {
    public static final Type<SpawnsRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "spawns_request"));

    public static final StreamCodec<FriendlyByteBuf, SpawnsRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, SpawnsRequestPayload::requestId,
                    SpawnsRequestPayload::new);

    @Override
    public Type<SpawnsRequestPayload> type() {
        return TYPE;
    }
}
