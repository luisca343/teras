package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: sparkle this entity, it is a shiny you have just laid eyes on. Sent only to the
 * player who spotted it, because the whole rule — range, line of sight, how recently they last saw
 * it — is per player.
 *
 * <p>Carries an entity id and nothing else. The client already has the entity (it is rendering it),
 * and the server has already decided this player may know: sending a position instead would let a
 * client draw the cue somewhere the entity is not, and sending species or palette would tell it
 * something it has no use for.</p>
 *
 * @param entityId  the shiny's network id
 * @param particles stars to draw, from the server's config so one setting governs every client
 * @param volume    chime volume, 0 to draw the stars silently
 */
public record ShinySparklePayload(int entityId, int particles, float volume)
        implements CustomPacketPayload {

    public static final Type<ShinySparklePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "shiny_sparkle"));

    public static final StreamCodec<FriendlyByteBuf, ShinySparklePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.entityId());
                        buffer.writeVarInt(payload.particles());
                        buffer.writeFloat(payload.volume());
                    },
                    buffer -> new ShinySparklePayload(
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
