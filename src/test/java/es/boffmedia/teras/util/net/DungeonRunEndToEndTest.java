package es.boffmedia.teras.util.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import es.boffmedia.teras.dungeon.instance.DungeonRunResult;
import es.boffmedia.teras.util.TerasConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end cover for {@code POST /smartrotom/dungeons/run} — the call {@code /teras dungeon
 * terminar} ultimately makes, driven through the real {@link SmartRotomService#saveDungeonRun} and a
 * real socket.
 *
 * <p>{@link DungeonRunBodyTest} already pins the JSON field-by-field, and it passed throughout the
 * period the route was completely broken: the body was always right, and everything around it was
 * wrong. What went unnoticed was the composed <b>URL</b> ({@code apiURL} already ended in
 * {@code /smartrotom}, which every route appends again, so the server got
 * {@code /smartrotom/smartrotom/dungeons/run} and 404'd) and the two credentials the route sits
 * behind. So the loopback server here is not a stub that accepts anything — it re-implements the
 * three gates the NestJS API actually applies, in its order:</p>
 *
 * <ol>
 *   <li>routing — an unknown path is a 404, which is what the doubled prefix produced;</li>
 *   <li>{@code MinecraftMiddleware} — 403 unless the body's top-level {@code server} equals the
 *       backend's {@code MC_WORLD};</li>
 *   <li>{@code GameServerAuthGuard} — 401 unless the Bearer matches {@code TERAS_API_TOKEN}.</li>
 * </ol>
 *
 * <p>A test that skipped any of them would have gone green against a server that rejects the
 * request, which is precisely the failure being fixed.</p>
 */
class DungeonRunEndToEndTest {

    /** Stands in for the backend's {@code MC_WORLD}; the mod sends it as {@code server}. */
    private static final String WORLD = "1ee7e5f6-8e50-4b49-9ee6-b26cc1b5f365";
    /** Stands in for the backend's {@code TERAS_API_TOKEN}. */
    private static final String TOKEN = "server-token-under-test";
    private static final String ROUTE = "/smartrotom/dungeons/run";

    private HttpServer server;
    private String baseUrl;

    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>();
    private CountDownLatch handled;

    private String originalApiUrl;
    private String originalToken;
    private String originalId;

    @BeforeEach
    void startServer() throws IOException, ReflectiveOperationException {
        handled = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // "/" so a mis-composed path reaches the handler and is answered 404, exactly as the real
        // API does. Registering only ROUTE would make the JDK server 404 without recording the path,
        // and the recorded path is the whole point of this test.
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            method.set(exchange.getRequestMethod());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try (InputStream in = exchange.getRequestBody()) {
                body.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            int code = statusFor(path.get(), auth.get(), body.get());
            status.set(code);
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
            handled.countDown();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        originalApiUrl = TerasConfig.getApiUrl();
        originalToken = TerasConfig.getApiToken();
        originalId = TerasConfig.getId();
    }

    @AfterEach
    void stopServer() throws ReflectiveOperationException {
        server.stop(0);
        set("apiUrl", originalApiUrl);
        set("apiToken", originalToken);
        set("id", originalId);
    }

    /** The backend's gates, in the order Nest applies them. */
    private static int statusFor(String requestPath, String authorization, String requestBody) {
        if (!ROUTE.equals(requestPath)) {
            return 404;
        }
        JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();
        if (!json.has("server") || !WORLD.equals(json.get("server").getAsString())) {
            return 403;
        }
        if (!("Bearer " + TOKEN).equals(authorization)) {
            return 401;
        }
        return 200;
    }

    /** Config is loaded from disk in production and has no setters; a test must reach in. */
    private static void set(String field, String value) throws ReflectiveOperationException {
        Field f = TerasConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }

    private void configure(String apiUrl) throws ReflectiveOperationException {
        set("apiUrl", apiUrl);
        set("apiToken", TOKEN);
        set("id", WORLD);
    }

    private static DungeonRunResult result() {
        return new DungeonRunResult("semilla-1", 1, 4, 3, true, 725_000L,
                List.of("LABYRINTH"), 480, 320, 1600,
                List.of(new DungeonRunResult.Participant(
                                "11111111-1111-1111-1111-111111111111", "Ana", 2, false),
                        new DungeonRunResult.Participant(
                                "22222222-2222-2222-2222-222222222222", "Beto", 0, true)));
    }

    /** {@code saveDungeonRun} posts on {@code Teras.EXECUTOR}, so the assertions must wait for it. */
    private void awaitRequest() throws InterruptedException {
        assertTrue(handled.await(10, TimeUnit.SECONDS), "no request reached the server");
    }

    @Test
    void postsToTheRouteAndIsAccepted() throws Exception {
        configure(baseUrl);

        SmartRotomService.saveDungeonRun(result());
        awaitRequest();

        assertEquals("POST", method.get());
        assertEquals(ROUTE, path.get(), "the composed path must not double the /smartrotom prefix");
        assertEquals("Bearer " + TOKEN, auth.get());
        assertEquals(200, status.get(), "the backend's gates rejected the request");
    }

    /**
     * The reported bug, reproduced from the config value that caused it: a server whose config.yml
     * still carries the old default must end up on the right route anyway, because config files on
     * disk are never rewritten.
     */
    @Test
    void aLegacyApiUrlStillLandsOnTheRoute() throws Exception {
        configure(TerasConfig.normalizeApiUrl(baseUrl + "/smartrotom"));

        SmartRotomService.saveDungeonRun(result());
        awaitRequest();

        assertEquals(ROUTE, path.get());
        assertEquals(200, status.get());
    }

    /** Without the fix the same config produces the 404 the user saw. */
    @Test
    void theUnfixedLegacyApiUrlIs404() throws Exception {
        configure(baseUrl + "/smartrotom");

        SmartRotomService.saveDungeonRun(result());
        awaitRequest();

        assertEquals("/smartrotom/smartrotom/dungeons/run", path.get());
        assertEquals(404, status.get());
    }

    @Test
    void aMissingTokenIsRejected() throws Exception {
        configure(baseUrl);
        set("apiToken", "");

        SmartRotomService.saveDungeonRun(result());
        awaitRequest();

        assertEquals(401, status.get(), "the route is behind GameServerAuthGuard");
    }

    @Test
    void anIdThatIsNotTheBackendsWorldIsRejected() throws Exception {
        configure(baseUrl);
        set("id", "TBiuaMoU");

        SmartRotomService.saveDungeonRun(result());
        awaitRequest();

        assertEquals(403, status.get(), "MinecraftMiddleware 403s a server field that is not MC_WORLD");
    }

    /** The accepted body must still satisfy SaveDungeonRunDto, which validates every field. */
    @Test
    void theAcceptedBodySatisfiesTheBackendDto() throws Exception {
        configure(baseUrl);

        SmartRotomService.saveDungeonRun(result());
        awaitRequest();
        assertEquals(200, status.get());

        JsonObject json = JsonParser.parseString(body.get()).getAsJsonObject();
        for (String required : List.of("server", "semilla", "etapaInicial", "etapaFinal",
                "pisosSuperados", "completada", "duracionMs", "maldiciones", "monedasGanadas",
                "monedasGastadas", "monedasConvertidas", "fecha", "participantes")) {
            assertTrue(json.has(required), "SaveDungeonRunDto requires '" + required + "': " + json);
        }
        JsonObject first = json.getAsJsonArray("participantes").get(0).getAsJsonObject();
        for (String required : List.of("uuid", "nombre", "muertes", "abandono")) {
            assertTrue(first.has(required), "DungeonRunParticipantDto requires '" + required + "'");
        }
        assertNotNull(json.get("fecha").getAsLong());
    }
}
