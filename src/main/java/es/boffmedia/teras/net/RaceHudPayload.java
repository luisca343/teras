package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: what the receiving player's race HUD should show.
 *
 * <p>One consolidated payload rather than a packet per field: the whole HUD is refreshed on a timer
 * anyway, and a single record means the client can never render a half-updated mix of an old lap
 * count with a new position. Replaces the 1.16.5 {@code CMessageRacePositionChange}, which carried
 * only the position and left the lap counter to chat messages.</p>
 *
 * <p>The client is told, never asked: every value here is computed server-side from the kart's own
 * position, so nothing about a race can be influenced by editing the client.</p>
 *
 * @param phase      ordinal of {@code RaceCore.Phase}; the client only distinguishes counting-down,
 *                   running and hidden
 * @param countdown  3, 2, 1 during the countdown, 0 for "GO", -1 when not counting down
 * @param position   current standing, 1-based; 0 when unknown
 * @param lap        lap being driven, 1-based
 * @param elapsedMs  time since the start
 * @param bestLapMs  the player's best lap so far, or -1
 * @param wrongWay   whether they are currently driving the circuit backwards
 */
public record RaceHudPayload(int phase,
                             int countdown,
                             int position,
                             int totalRacers,
                             int lap,
                             int totalLaps,
                             int elapsedMs,
                             int bestLapMs,
                             boolean wrongWay) implements CustomPacketPayload {

    public static final Type<RaceHudPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "race_hud"));

    /** Phase ordinal that means "show nothing", matching {@code RaceCore.Phase.FINISHED}. */
    public static final int PHASE_HIDDEN = 3;

    /**
     * Written out by hand rather than with {@code StreamCodec.composite}, which only composes six
     * components; this payload carries nine. Field order is the contract — keep the reads and writes
     * in step.
     *
     * <p>Signed varints throughout, because {@code countdown} and {@code bestLapMs} both use -1 to
     * mean "not applicable" and an unsigned varint would encode that as five bytes.</p>
     */
    public static final StreamCodec<FriendlyByteBuf, RaceHudPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.phase());
                        buffer.writeVarInt(payload.countdown());
                        buffer.writeVarInt(payload.position());
                        buffer.writeVarInt(payload.totalRacers());
                        buffer.writeVarInt(payload.lap());
                        buffer.writeVarInt(payload.totalLaps());
                        buffer.writeVarInt(payload.elapsedMs());
                        buffer.writeVarInt(payload.bestLapMs());
                        buffer.writeBoolean(payload.wrongWay());
                    },
                    buffer -> new RaceHudPayload(
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readBoolean()));

    /** Clears the HUD — sent once when a player's race ends for any reason. */
    public static RaceHudPayload hidden() {
        return new RaceHudPayload(PHASE_HIDDEN, -1, 0, 0, 0, 0, 0, -1, false);
    }

    @Override
    public Type<RaceHudPayload> type() {
        return TYPE;
    }
}
