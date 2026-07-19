package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client on join: the subset of {@code config/teras/config.yml} the client needs.
 *
 * <p>The config file is the <b>server's</b> configuration. A client connected to a remote server has
 * its own copy of the file (for the world it hosts in single-player), and that copy must never leak
 * into a multiplayer session — the SmartRotom has to point at the site the server it's on says to,
 * not at whatever the player has on disk. So the client keeps no opinion of its own: it holds only
 * what arrives here (see {@code client.ServerConfig}), and single-player is not a special case —
 * the integrated server sends this too.</p>
 *
 * <p>Only {@code home} travels: the rest of the config ({@code apiToken}, {@code httpToken}, the
 * bind, …) is server-side secrets and server-side behaviour that a client has no use for, and the
 * server {@code id} already reaches the web through {@code getUserData}'s {@code world} field.</p>
 */
public record ServerConfigPayload(String home) implements CustomPacketPayload {
    public static final Type<ServerConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "server_config"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ServerConfigPayload::home,
                    ServerConfigPayload::new);

    @Override
    public Type<ServerConfigPayload> type() {
        return TYPE;
    }
}
