package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server → client: this player's resolved combat sheet, for the panel on the left.
 *
 * <p>Values arrive in {@code Stat.values()} order and are read positionally, so the client never
 * needs to know the names — which keeps a stat being added or renamed a server-side change. A list
 * whose length disagrees with the client's enum is discarded rather than partially read: half a
 * sheet displayed confidently is worse than no sheet.</p>
 *
 * <p>An <b>empty</b> list is the "you are not in a run" signal, and is what hides the panel. Sending
 * it explicitly rather than simply stopping means leaving a dungeon clears the display instead of
 * freezing the last thing it saw.</p>
 */
public record CombatStatsPayload(List<Float> values) implements CustomPacketPayload {
    public static final Type<CombatStatsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "combat_stats"));

    public static final StreamCodec<FriendlyByteBuf, CombatStatsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), CombatStatsPayload::values,
                    CombatStatsPayload::new);

    /** The signal that there is nothing to show. */
    public static CombatStatsPayload hidden() {
        return new CombatStatsPayload(List.of());
    }

    @Override
    public Type<CombatStatsPayload> type() {
        return TYPE;
    }
}
