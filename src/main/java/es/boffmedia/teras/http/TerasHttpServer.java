package es.boffmedia.teras.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.TerasConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A small read-only HTTP API exposed by the Minecraft server, for the SmartRotom backend to pull live
 * state that would otherwise go stale between pushes.
 *
 * <pre>GET /quests/user/{uuid}  ->  200 MisionesResponse JSON</pre>
 *
 * <p>This is the only place Teras <b>listens</b> — everything else (SmartRotomService/HttpText) is
 * outbound. It replaces the {@code getMisiones} mcef query, which round-tripped through the in-game
 * browser's JS bridge.</p>
 *
 * <h2>Security posture</h2>
 * Designed for <b>server-to-server</b> calls from the SmartRotom backend, not from a browser: there
 * are no CORS headers, so a page cannot read it cross-origin.
 * <ul>
 *   <li><b>Off by default</b> ({@code httpEnabled: false}) — a server that doesn't opt in never binds.</li>
 *   <li><b>Binds loopback by default</b> ({@code httpBind: "127.0.0.1"}). Widening it is an explicit
 *       config act and is logged as a warning.</li>
 *   <li><b>Never unauthenticated:</b> every request needs {@code Authorization: Bearer <httpToken>},
 *       compared in constant time. Enabling without a token mints one (see {@code TerasConfig});
 *       a blank token at startup refuses to bind rather than serving openly.</li>
 *   <li><b>Read-only.</b> Only GET is routed; anything else is a 405.</li>
 * </ul>
 * There is no TLS here — terminate it at a reverse proxy, or keep the bind private and tunnel.
 *
 * <h2>Threading</h2>
 * Handlers run on {@link Teras#EXECUTOR}, not the server thread, so reading quest state directly
 * would race the game loop. Every read hops onto the server thread via
 * {@link MinecraftServer#submit(java.util.function.Supplier)} and waits with a timeout, so a stalled
 * server yields a 503 instead of pinning an HTTP thread forever.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class TerasHttpServer {
    private TerasHttpServer() {}

    private static final String QUESTS_USER_PREFIX = "/quests/user/";
    private static final String BEARER_PREFIX = "Bearer ";

    /** How long a request waits for the server thread before giving up. */
    private static final long SERVER_THREAD_TIMEOUT_SECONDS = 5;
    /** Grace period for in-flight requests when the server is stopping. */
    private static final int SHUTDOWN_DELAY_SECONDS = 1;

    private static HttpServer server;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!TerasConfig.isHttpEnabled()) {
            return;
        }
        // Fail closed: an enabled-but-tokenless API would be an open read of player data.
        if (TerasConfig.getHttpToken().isBlank()) {
            Teras.LOGGER.error("httpEnabled is true but 'httpToken' is empty — refusing to start the "
                    + "Teras HTTP API. Set a token in config/teras/config.json.");
            return;
        }
        MinecraftServer mc = event.getServer();
        try {
            server = HttpServer.create(
                    new InetSocketAddress(TerasConfig.getHttpBind(), TerasConfig.getHttpPort()), 0);
            server.createContext("/quests/user/", exchange -> handleQuestsUser(exchange, mc));
            server.setExecutor(Teras.EXECUTOR);
            server.start();

            Teras.LOGGER.info("Teras HTTP API listening on {}:{} (GET /quests/user/{{uuid}})",
                    TerasConfig.getHttpBind(), TerasConfig.getHttpPort());
            if (TerasConfig.isHttpBindPublic()) {
                Teras.LOGGER.warn("Teras HTTP API is bound to '{}', which is reachable off this "
                        + "machine, and it serves plaintext HTTP. Restrict it by firewall and/or put a "
                        + "TLS-terminating proxy in front of it.", TerasConfig.getHttpBind());
            }
        } catch (IOException e) {
            Teras.LOGGER.error("Failed to start the Teras HTTP API on {}:{}",
                    TerasConfig.getHttpBind(), TerasConfig.getHttpPort(), e);
            server = null;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (server == null) {
            return;
        }
        server.stop(SHUTDOWN_DELAY_SECONDS);
        server = null;
        Teras.LOGGER.info("Teras HTTP API stopped");
    }

    // ---- Routing ----

    /** {@code GET /quests/user/{uuid}} — this player's quests, as {@code getMisiones} used to return. */
    private static void handleQuestsUser(HttpExchange exchange, MinecraftServer mc) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, error("Method not allowed"));
                return;
            }
            if (!isAuthorized(exchange)) {
                // No detail: an unauthenticated caller learns nothing about what exists here.
                respond(exchange, 401, error("Unauthorized"));
                return;
            }

            UUID uuid = parseUuidFromPath(exchange.getRequestURI().getPath());
            if (uuid == null) {
                respond(exchange, 400, error("Malformed player uuid"));
                return;
            }

            if (!es.boffmedia.teras.quests.QuestBridge.isAvailable()) {
                respond(exchange, 503, error("Quest system unavailable (CustomNPCs not installed)"));
                return;
            }

            String json = questsFor(mc, uuid);
            if (json == null) {
                // Quest state is read through CustomNPCs' PlayerWrapper, which needs a live entity —
                // so an offline player genuinely cannot be answered rather than returning stale data.
                respond(exchange, 404, error("Player not online"));
                return;
            }
            respond(exchange, 200, json);
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /**
     * Reads the player's quests <b>on the server thread</b>. Returns {@code null} when the player is
     * offline, and propagates nothing — a timeout surfaces as a 503 to the caller.
     */
    private static String questsFor(MinecraftServer mc, UUID uuid) {
        try {
            return mc.submit(() -> {
                ServerPlayer player = mc.getPlayerList().getPlayer(uuid);
                if (player == null) {
                    return null;
                }
                return es.boffmedia.teras.quests.QuestService.getMisionesJson(player);
            }).get(SERVER_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted reading quests", e);
        } catch (Exception e) {
            throw new IllegalStateException("Timed out reading quests from the server thread", e);
        }
    }

    // ---- Routing / auth primitives (package-private: unit-tested in TerasHttpServerTest) ----

    /**
     * The player uuid out of {@code /quests/user/{uuid}}, or {@code null} if the path isn't that shape
     * or the uuid is malformed. Rejects a trailing segment so {@code /quests/user/{uuid}/anything}
     * can't be mistaken for a bare uuid.
     */
    static UUID parseUuidFromPath(String path) {
        if (path == null || !path.startsWith(QUESTS_USER_PREFIX)) {
            return null;
        }
        String rawUuid = path.substring(QUESTS_USER_PREFIX.length());
        if (rawUuid.isEmpty() || rawUuid.contains("/")) {
            return null;
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isAuthorized(HttpExchange exchange) {
        return isTokenValid(exchange.getRequestHeaders().getFirst("Authorization"),
                TerasConfig.getHttpToken());
    }

    /**
     * Constant-time bearer check, so a wrong token can't be recovered by timing the comparison.
     * A blank {@code expected} always fails: the server refuses to start without a token, but this
     * makes the auth primitive itself fail closed rather than accepting {@code "Bearer "}.
     */
    static boolean isTokenValid(String authHeader, String expected) {
        if (authHeader == null || expected == null || expected.isBlank()) {
            return false;
        }
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] presented = authHeader.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expected.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Response helpers ----

    private static String error(String message) {
        return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = exchange.getResponseBody()) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            out.write(bytes);
        } catch (IOException e) {
            Teras.LOGGER.warn("Teras HTTP API: failed writing response: {}", e.toString());
        }
    }
}
