package es.boffmedia.teras.mcef;

import es.boffmedia.teras.Teras;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JS callbacks awaiting a server round-trip, keyed by the request id echoed back in
 * {@code McefResponsePayload}. Lets several async queries be in flight without their responses
 * colliding — 1.16.5's per-query static callbacks could not.
 *
 * <p>These entries are session state with a lifecycle: a registered callback is a JS promise nothing
 * else will ever settle, so {@link #clear()} must run on disconnect. Registered from CEF threads and
 * resolved from the client thread, hence the concurrent map.</p>
 */
public final class PendingQueries {
    private PendingQueries() {}

    private static final Map<Long, JsQueryCallback> PENDING = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();

    /** Registers {@code callback} for a new request and returns its id (echoed in the request payload). */
    public static long register(JsQueryCallback callback) {
        long id = NEXT_REQUEST_ID.incrementAndGet();
        PENDING.put(id, callback);
        return id;
    }

    /** Resolves and removes the callback for {@code requestId}, or {@code null} if none is waiting. */
    public static JsQueryCallback take(long requestId) {
        return PENDING.remove(requestId);
    }

    /**
     * Rejects and forgets every waiting callback — call when the connection that would have answered
     * them is gone. Rejecting rather than dropping: the page can retry a failed claim, but can do
     * nothing with a promise that never settles.
     */
    public static void clear() {
        if (PENDING.isEmpty()) {
            return;
        }
        int count = PENDING.size();
        for (Long id : PENDING.keySet()) {
            JsQueryCallback callback = PENDING.remove(id);
            if (callback != null) {
                try {
                    callback.failure(0, "Disconnected before the server replied");
                } catch (Exception e) {
                    Teras.LOGGER.warn("Failed rejecting pending query {}: {}", id, e.toString());
                }
            }
        }
        Teras.LOGGER.info("Rejected {} SmartRotom quer{} still awaiting a server reply",
                count, count == 1 ? "y" : "ies");
    }
}
