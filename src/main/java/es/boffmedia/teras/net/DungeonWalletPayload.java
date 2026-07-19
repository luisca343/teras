package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the party's shared dungeon purse, drawn under the minimap. Sent to every member
 * on every change, because the wallet is common — one player's purchase moves everyone's counter,
 * and a stale HUD would have half the party pricing against money that is already gone.
 *
 * @param active  false hides the counters (run ended / left the dungeon)
 * @param coins   coins the party is carrying
 * @param charges wall-breaker charges in shared stock
 */
public record DungeonWalletPayload(boolean active, int coins, int charges)
        implements CustomPacketPayload {

    public static final Type<DungeonWalletPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_wallet"));

    public static DungeonWalletPayload hidden() {
        return new DungeonWalletPayload(false, 0, 0);
    }

    public static final StreamCodec<FriendlyByteBuf, DungeonWalletPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBoolean(payload.active());
                        buffer.writeVarInt(payload.coins());
                        buffer.writeVarInt(payload.charges());
                    },
                    buffer -> new DungeonWalletPayload(
                            buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
