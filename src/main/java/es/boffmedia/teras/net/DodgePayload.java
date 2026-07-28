package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "I pressed esquiva, and I was holding this direction."
 *
 * <p>Carries only the two movement impulses, because a direction is the one thing the server cannot
 * work out for itself — it sees where a player is looking, which does not distinguish backing away
 * from advancing. Distance is not negotiable: {@code Dodge} normalizes and clamps, so a crafted
 * packet chooses where a roll goes and never how far.</p>
 *
 * <p>Whether the roll happens at all is decided entirely server-side — in a run, enabled, off
 * cooldown — so this is a request, not an instruction.</p>
 */
public record DodgePayload(float forward, float left) implements CustomPacketPayload {
    public static final Type<DodgePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dodge"));

    public static final StreamCodec<FriendlyByteBuf, DodgePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, DodgePayload::forward,
                    ByteBufCodecs.FLOAT, DodgePayload::left,
                    DodgePayload::new);

    @Override
    public Type<DodgePayload> type() {
        return TYPE;
    }
}
