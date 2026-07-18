package es.boffmedia.teras.client;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import es.boffmedia.teras.mcef.PendingQueries;
import es.boffmedia.teras.net.McefResponsePayload;
import es.boffmedia.teras.net.ServerConfigPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only network handling. Kept separate from {@link es.boffmedia.teras.net.TerasNet} (which is
 * loaded on both sides) so its client-only dependencies (JCEF via {@link JsQueryCallback}) are never
 * classloaded on a dedicated server.
 */
public final class ClientNetHandler {
    private ClientNetHandler() {}

    /**
     * Resolves the originating {@code mcefQuery} callback (matched by request id). A failed reply
     * ({@code ok == false}) rejects the JS promise via {@code failure}, so a server-side error surfaces
     * as a rejected query the page already handles — not a success carrying an error body, which is how
     * a failed grant used to read as a completed one.
     */
    public static void onMcefResponse(McefResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            JsQueryCallback cb = PendingQueries.take(payload.requestId());
            if (cb == null) {
                Teras.LOGGER.warn("MCEF response for unknown/expired request id {}: {}",
                        payload.requestId(), payload.json());
                return;
            }
            if (payload.ok()) {
                cb.success(payload.json());
            } else {
                cb.failure(SERVER_ERROR_CODE, payload.json());
            }
        });
    }

    /** Passed to {@code mcefQuery}'s {@code onFailure} for a server-side failure; the page reads the message, not the code. */
    private static final int SERVER_ERROR_CODE = 500;

    /** Stores the config of the server we just joined; see {@link ServerConfig}. */
    public static void onServerConfig(ServerConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ServerConfig.accept(payload));
    }
}
