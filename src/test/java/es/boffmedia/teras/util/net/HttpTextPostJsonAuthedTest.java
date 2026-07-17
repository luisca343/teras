package es.boffmedia.teras.util.net;

import com.sun.net.httpserver.HttpServer;
import es.boffmedia.teras.util.TerasConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link HttpText#postJsonAuthed} against a loopback server. Unlike the other net tests this one
 * does touch a socket — the behaviour under test <i>is</i> the transport, and there is no pure
 * function hiding inside it.
 *
 * <p>What makes these worth the socket: darCaja grants items on this method's return value, and its
 * contract is that <b>only</b> a 2xx body is truthy. Every other outcome must come back {@code null},
 * because a grant cannot tell "already claimed" from "backend down" and must give nothing rather than
 * guess. A future refactor that returned an error body instead of {@code null} would turn a failed
 * claim into a free one — the same envelope trap that made the backend's own {@code rotomPOST} treat
 * {@code {success:false}} as success.</p>
 */
class HttpTextPostJsonAuthedTest {

    private HttpServer server;
    private String baseUrl;
    private String originalToken;

    @BeforeEach
    void startServer() throws IOException, ReflectiveOperationException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        originalToken = TerasConfig.getApiToken();
    }

    @AfterEach
    void stopServer() throws ReflectiveOperationException {
        server.stop(0);
        setApiToken(originalToken);
    }

    /** {@code apiToken} is loaded from disk in production and has no setter; a test must reach in. */
    private static void setApiToken(String token) throws ReflectiveOperationException {
        Field field = TerasConfig.class.getDeclaredField("apiToken");
        field.setAccessible(true);
        field.set(null, token);
    }

    private void respondWith(int status, String body, AtomicReference<String> capturedBody,
                             AtomicReference<String> capturedAuth, AtomicReference<String> capturedMethod) {
        server.createContext("/claim", exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try (InputStream in = exchange.getRequestBody()) {
                capturedBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
    }

    @Test
    void returnsBodyAndSendsBearerOnSuccess() throws ReflectiveOperationException {
        setApiToken("s3cret-token");
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        respondWith(200, "{\"objetos\":[{\"id\":\"minecraft:stone\",\"cantidad\":2}]}", body, auth, method);

        String response = HttpText.postJsonAuthed(baseUrl + "/claim", "{\"source\":\"mine\"}");

        assertEquals("{\"objetos\":[{\"id\":\"minecraft:stone\",\"cantidad\":2}]}", response);
        assertEquals("POST", method.get());
        assertEquals("{\"source\":\"mine\"}", body.get());
        assertEquals("Bearer s3cret-token", auth.get());
    }

    @Test
    void omitsAuthorizationWhenNoTokenIsConfigured() throws ReflectiveOperationException {
        setApiToken("");
        AtomicReference<String> auth = new AtomicReference<>("sentinel");
        respondWith(200, "{}", new AtomicReference<>(), auth, new AtomicReference<>());

        HttpText.postJsonAuthed(baseUrl + "/claim", "{}");

        assertNull(auth.get(), "an empty token must send no Authorization header, not 'Bearer '");
    }

    /** A 4xx carries a body; returning it would let a caller mistake a refusal for a grant. */
    @Test
    void returnsNullOnClientError() throws ReflectiveOperationException {
        setApiToken("t");
        respondWith(403, "{\"objetos\":[{\"id\":\"minecraft:diamond\",\"cantidad\":64}]}",
                new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());

        assertNull(HttpText.postJsonAuthed(baseUrl + "/claim", "{}"));
    }

    @Test
    void returnsNullOnServerError() throws ReflectiveOperationException {
        setApiToken("t");
        respondWith(500, "internal error", new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>());

        assertNull(HttpText.postJsonAuthed(baseUrl + "/claim", "{}"));
    }

    @Test
    void returnsNullWhenTheHostIsUnreachable() {
        server.stop(0);
        assertNull(HttpText.postJsonAuthed(baseUrl + "/claim", "{}"));
    }

    @Test
    void returnsNullOnMalformedUrlOrNullArguments() {
        assertNull(HttpText.postJsonAuthed("not a url", "{}"));
        assertNull(HttpText.postJsonAuthed(null, "{}"));
        assertNull(HttpText.postJsonAuthed(baseUrl + "/claim", null));
    }
}
