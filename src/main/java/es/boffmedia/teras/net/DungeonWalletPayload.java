package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server → client: the party's shared dungeon purse, drawn under the minimap. Sent to every member
 * on every change, because the wallet is common — one player's purchase moves everyone's counter,
 * and a stale HUD would have half the party pricing against money that is already gone.
 *
 * <p>Afflictions ride this packet rather than one of their own. They change on the same events the
 * purse does — the curse room pays you coins for taking one and charges coins to shed one — so a
 * second payload would be two packets that always travel together and could disagree if either were
 * dropped.</p>
 *
 * @param active      false hides the counters (run ended / left the dungeon)
 * @param coins       coins the party is carrying
 * @param charges     wall-breaker charges in shared stock
 * @param afflictions what this player is living with, the party's and their own, in the order
 *                    taken. Display names rather than ids: the HUD draws them, and nothing on the
 *                    client needs to know what one means
 */
public record DungeonWalletPayload(boolean active, int coins, int charges,
                                   List<String> afflictions) implements CustomPacketPayload {

    /** Wire guard. Afflictions are uncapped by design, so the packet needs its own ceiling. */
    private static final int MAX_AFFLICTIONS = 32;

    public static final Type<DungeonWalletPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_wallet"));

    public DungeonWalletPayload(boolean active, int coins, int charges) {
        this(active, coins, charges, List.of());
    }

    public static DungeonWalletPayload hidden() {
        return new DungeonWalletPayload(false, 0, 0, List.of());
    }

    public static final StreamCodec<FriendlyByteBuf, DungeonWalletPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBoolean(payload.active());
                        buffer.writeVarInt(payload.coins());
                        buffer.writeVarInt(payload.charges());
                        List<String> names = payload.afflictions();
                        int count = Math.min(names.size(), MAX_AFFLICTIONS);
                        buffer.writeVarInt(count);
                        for (int i = 0; i < count; i++) {
                            buffer.writeUtf(names.get(i), 64);
                        }
                    },
                    buffer -> {
                        boolean active = buffer.readBoolean();
                        int coins = buffer.readVarInt();
                        int charges = buffer.readVarInt();
                        int count = Math.min(buffer.readVarInt(), MAX_AFFLICTIONS);
                        List<String> names = new java.util.ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            names.add(buffer.readUtf(64));
                        }
                        return new DungeonWalletPayload(active, coins, charges, List.copyOf(names));
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
