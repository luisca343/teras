package es.boffmedia.teras.client;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.ServerConfigPayload;

/**
 * The config of the server this client is connected to, as sent by {@link ServerConfigPayload} at
 * join. Client-side mirror only — the authoritative copy is {@code config/teras/config.yml} on the
 * server, and this class deliberately has no fallback to the local file: on a server the local one is
 * not ours to read.
 *
 * <p>Empty before the join packet lands and again after {@link #clear()} on disconnect, so a browser
 * is never opened against a stale server's home.</p>
 */
public final class ServerConfig {
    private ServerConfig() {}

    private static String home = "";

    public static void accept(ServerConfigPayload payload) {
        home = payload.home() == null ? "" : payload.home();
        Teras.LOGGER.info("Received server config (home={})", home);
    }

    public static void clear() {
        home = "";
    }

    /** True once the server has told us its config; nothing SmartRotom-related can open before then. */
    public static boolean isSynced() {
        return !home.isBlank();
    }

    public static String getHome() {
        return home;
    }
}
