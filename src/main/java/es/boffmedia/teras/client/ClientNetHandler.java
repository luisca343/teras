package es.boffmedia.teras.client;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import es.boffmedia.teras.net.McefResponsePayload;
import es.boffmedia.teras.util.QueryHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only network handling. Kept separate from {@link es.boffmedia.teras.net.TerasNet} (which is
 * loaded on both sides) so its client-only dependencies (JCEF via {@link JsQueryCallback}) are never
 * classloaded on a dedicated server.
 */
public final class ClientNetHandler {
    private ClientNetHandler() {}

    /** Resolves the pending {@code mcefQuery} callback with the server's JSON response. */
    public static void onMcefResponse(McefResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            JsQueryCallback cb = QueryHelper.pendingCallback;
            if (cb != null) {
                cb.success(payload.json());
                QueryHelper.pendingCallback = null;
            } else {
                Teras.LOGGER.warn("Received MCEF response with no pending callback: {}", payload.json());
            }
        });
    }
}
