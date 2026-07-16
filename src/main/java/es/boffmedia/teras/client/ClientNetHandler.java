package es.boffmedia.teras.client;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import es.boffmedia.teras.net.McefResponsePayload;
import es.boffmedia.teras.net.ServerConfigPayload;
import es.boffmedia.teras.util.QueryHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only network handling. Kept separate from {@link es.boffmedia.teras.net.TerasNet} (which is
 * loaded on both sides) so its client-only dependencies (JCEF via {@link JsQueryCallback}) are never
 * classloaded on a dedicated server.
 */
public final class ClientNetHandler {
    private ClientNetHandler() {}

    /** Resolves the originating {@code mcefQuery} callback (matched by request id) with the JSON response. */
    public static void onMcefResponse(McefResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            JsQueryCallback cb = QueryHelper.takePending(payload.requestId());
            if (cb != null) {
                cb.success(payload.json());
            } else {
                Teras.LOGGER.warn("MCEF response for unknown/expired request id {}: {}",
                        payload.requestId(), payload.json());
            }
        });
    }

    /** Stores the config of the server we just joined; see {@link ServerConfig}. */
    public static void onServerConfig(ServerConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ServerConfig.accept(payload));
    }
}
