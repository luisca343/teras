package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: request this player's quest list. The server reads the CustomNPCs quest state and
 * replies with a {@link McefResponsePayload} echoing {@code requestId} and carrying a
 * {@code UserQuestData} JSON — the catalog and the player's progress already merged, since
 * single-player has no SmartRotom backend to merge them. That resolves the pending JS callback.
 *
 * <p>This is the in-game SmartRotom page's route to quests, and the only one that works with **no
 * configuration and no network** — single-player worlds and LAN servers, where the HTTP API
 * ({@code GET /quests/user/{uuid}}, off by default) has no backend to serve. Both routes call the same
 * {@code QuestService}; see {@code docs/HTTP_API.md}.</p>
 *
 * <p>1.16.5 had no working equivalent: its {@code SMessageVerMisiones} was unregistered, so the
 * request never reached the server. This is the request half rebuilt on the payload networking.</p>
 */
public record MisionesRequestPayload(long requestId) implements CustomPacketPayload {
    public static final Type<MisionesRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "misiones_request"));

    public static final StreamCodec<FriendlyByteBuf, MisionesRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, MisionesRequestPayload::requestId,
                    MisionesRequestPayload::new);

    @Override
    public Type<MisionesRequestPayload> type() {
        return TYPE;
    }
}
