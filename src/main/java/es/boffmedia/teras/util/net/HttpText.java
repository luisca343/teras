package es.boffmedia.teras.util.net;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.TerasConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shared HTTP text fetch with a short-lived cache. Safety: connect/read timeouts; an HTTPS-only policy
 * gated by {@link TerasConfig#isRequireHttps()} (fail-closed); and an identifier whitelist closing the
 * path-injection vector before an id is interpolated into a URL.
 *
 * <p>Request and response <b>bodies</b> log at {@code debug}. They carry player balances and grant
 * contents, and these routes run at volume on a live server.</p>
 */
public final class HttpText {
    private HttpText() {}

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /** How long a fetched body stays fresh; repeated battles reuse it instead of re-downloading. */
    private static final long CACHE_TTL_MS = 60_000L;

    /** Expired entries are swept when the cache passes this size, so it cannot grow for a server's lifetime. */
    private static final int CACHE_SWEEP_THRESHOLD = 64;

    /** Safe shape for an id interpolated into a URL: no {@code ../}, encoded slashes, or query. */
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");

    private static final ConcurrentHashMap<String, CacheEntry> TEXT_CACHE = new ConcurrentHashMap<>();

    private record CacheEntry(String body, long expiresAt) {}

    /** True when {@code id} is safe to interpolate into a config/team URL. */
    public static boolean isValidIdentifier(String id) {
        return id != null && VALID_IDENTIFIER.matcher(id).matches();
    }

    /**
     * Fetches {@code urlString}'s body as text, served from a short-lived cache when still fresh.
     * Returns {@code null} on any transport/IO/policy failure. The cached value is an immutable
     * string, so callers rebuild their own mutable objects each time.
     */
    public static String fetchCached(String urlString) {
        if (urlString == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        CacheEntry cached = TEXT_CACHE.get(urlString);
        if (cached != null && cached.expiresAt() > now) {
            Teras.LOGGER.debug("[Teras HTTP] --> GET {} (cache hit, {} bytes)", urlString, cached.body().length());
            return cached.body();
        }
        // Unauthenticated and cacheable: static remote config, not live player state.
        Response response = execute("GET", urlString, null, false);
        if (!response.ok()) {
            return null;
        }
        sweepExpired(now);
        TEXT_CACHE.put(urlString, new CacheEntry(response.body(), now + CACHE_TTL_MS));
        return response.body();
    }

    /**
     * Authenticated {@code GET}, <b>uncached</b>, returning the body or {@code null} on any
     * transport/policy failure or a {@code >= 400} status.
     *
     * <p>Separate from {@link #fetchCached} on both counts deliberately: that method caches for
     * {@link #CACHE_TTL_MS} and sends no credentials, which suits static remote config but not
     * authenticated live state like a balance, where a stale read is a wrong answer.</p>
     *
     * <p><b>Blocks the calling thread.</b> Callers must already be off the server thread.</p>
     */
    public static String getAuthed(String urlString) {
        return execute("GET", urlString, null, true).bodyOrNull();
    }

    /** Fire-and-forget JSON {@code POST} on {@link Teras#EXECUTOR}, with the bearer token and HTTPS
     *  policy. Failures are logged, not thrown. */
    public static void postJson(String urlString, String jsonBody) {
        if (urlString == null || jsonBody == null) {
            return;
        }
        Teras.EXECUTOR.execute(() -> execute("POST", urlString, jsonBody, true));
    }

    /**
     * Authenticated JSON {@code POST} returning the response body, or {@code null} on any
     * transport/policy failure or a {@code >= 400} status.
     *
     * <p>{@link #postJson} already sends the same bearer token, so the name marks the family rather
     * than the difference: this one <i>waits</i> and hands back the body. That is what a grant needs —
     * it must be told what to give — and what fire-and-forget cannot express.</p>
     *
     * <p>Returning {@code null} for every failure is deliberate. A caller that grants items cannot
     * distinguish "already claimed" from "backend down" and must not try: no body means give nothing.</p>
     *
     * <p><b>Blocks the calling thread.</b> Callers must already be off the server thread.</p>
     */
    public static String postJsonAuthed(String urlString, String jsonBody) {
        if (urlString == null || jsonBody == null) {
            return null;
        }
        return execute("POST", urlString, jsonBody, true).bodyOrNull();
    }

    /** A completed exchange. {@code ok} is false for any transport, policy or {@code >= 400} failure. */
    private record Response(boolean ok, String body) {
        String bodyOrNull() {
            return ok ? body : null;
        }
    }

    private static final Response FAILED = new Response(false, null);

    /**
     * The one request path: URL parse, HTTPS policy, headers, optional body, status handling and
     * logging. The four public methods differ only in method, auth, body and what they do with the
     * result, so keeping one implementation is what stops those policies drifting apart.
     */
    private static Response execute(String method, String urlString, String jsonBody, boolean authed) {
        if (urlString == null) {
            return FAILED;
        }
        URL url;
        try {
            // URI.create().toURL() rather than new URL(String), deprecated since Java 20.
            url = URI.create(urlString).toURL();
        } catch (IllegalArgumentException | IOException e) {
            Teras.LOGGER.error("Malformed {} URL: {}", method, urlString, e);
            return FAILED;
        }
        if (!isTransportAllowed(url)) {
            return FAILED;
        }

        String token = authed ? TerasConfig.getApiToken() : null;
        boolean hasToken = token != null && !token.isEmpty();
        if (jsonBody == null) {
            Teras.LOGGER.info("[Teras HTTP] --> {} {} (auth={})", method, urlString, hasToken ? "bearer" : "none");
        } else {
            Teras.LOGGER.info("[Teras HTTP] --> {} {} (auth={}, {} bytes)", method, urlString,
                    hasToken ? "bearer" : "none", jsonBody.getBytes(StandardCharsets.UTF_8).length);
            Teras.LOGGER.debug("[Teras HTTP]     body: {}", jsonBody);
        }

        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(CONNECT_TIMEOUT_MS);
            con.setReadTimeout(READ_TIMEOUT_MS);
            con.setRequestMethod(method);
            con.setRequestProperty("User-Agent", authed ? "Teras-SmartRotom" : "Mozilla/4.0");
            if (authed) {
                // Not on the unauthenticated path: that one fetches Showdown team pastes as plain
                // text, and a JSON-only Accept invites a 406 from a server that honours it.
                con.setRequestProperty("Accept", "application/json");
            }
            if (hasToken) {
                con.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (jsonBody != null) {
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = con.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = con.getResponseCode();
            String body = readBody(code < 400 ? con.getInputStream() : con.getErrorStream());
            Teras.LOGGER.info("[Teras HTTP] <-- {} {} {}", code, method, urlString);
            Teras.LOGGER.debug("[Teras HTTP]     response: {}", body);
            if (code >= 400) {
                Teras.LOGGER.warn("{} {} -> HTTP {}", method, urlString, code);
                return FAILED;
            }
            return new Response(true, body);
        } catch (IOException e) {
            Teras.LOGGER.error("Error on {} {}: {}", method, urlString, e.getMessage());
            return FAILED;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    /** Drops entries whose TTL has passed; logical expiry alone would leave them in the map forever. */
    private static void sweepExpired(long now) {
        if (TEXT_CACHE.size() < CACHE_SWEEP_THRESHOLD) {
            return;
        }
        Iterator<Map.Entry<String, CacheEntry>> it = TEXT_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt() <= now) {
                it.remove();
            }
        }
    }

    /** Reads a response/error stream to a UTF-8 string for debug logging; {@code ""} on any failure. */
    private static String readBody(InputStream in) {
        if (in == null) {
            return "";
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "";
        }
    }

    /** Fail-closed HTTPS policy shared with SmartRotom (see {@link TerasConfig#isRequireHttps()}). */
    private static boolean isTransportAllowed(URL url) {
        if (TerasConfig.isRequireHttps() && !"https".equalsIgnoreCase(url.getProtocol())) {
            Teras.LOGGER.error("Request refused: requireHttps is enabled but URL is not HTTPS ({})", url);
            return false;
        }
        return true;
    }
}
