package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "I scanned the Pokémon that is entity {@code entityId}; register it as seen."
 *
 * <p>Carries an entity id, never dex/form/palette: the server reads the species off its own copy of the
 * entity, so a crafted packet cannot register a species the player never found. Validated in
 * {@code TerasNet.handleDexRegister}.</p>
 */
public record DexRegisterPayload(int entityId) implements CustomPacketPayload {
    public static final Type<DexRegisterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dex_register"));

    public static final StreamCodec<FriendlyByteBuf, DexRegisterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DexRegisterPayload::entityId,
                    DexRegisterPayload::new);

    @Override
    public Type<DexRegisterPayload> type() {
        return TYPE;
    }
}
