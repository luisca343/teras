package es.boffmedia.teras.util.net;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.TerasConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shared HTTP text fetch with a short-lived cache. Safety: connect/read timeouts; an HTTPS-only policy
 * gated by {@link TerasConfig#isRequireHttps()} (fail-closed); and an identifier whitelist closing the
 * path-injection vector before an id is interpolated into a URL.
 */
public final class HttpText {
    private HttpText() {}

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /** How long a fetched body stays fresh; repeated battles reuse it instead of re-downloading. */
    private static final long CACHE_TTL_MS = 60_000L;

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
            Teras.LOGGER.info("[Teras HTTP] --> GET {} (cache hit, {} bytes)", urlString, cached.body().length());
            return cached.body();
        }
        Teras.LOGGER.info("[Teras HTTP] --> GET {}", urlString);

        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            Teras.LOGGER.error("Malformed URL: {}", urlString, e);
            return null;
        }
        if (!isTransportAllowed(url)) {
            return null;
        }

        String body;
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(CONNECT_TIMEOUT_MS);
            con.setReadTimeout(READ_TIMEOUT_MS);
            con.addRequestProperty("User-Agent", "Mozilla/4.0");
            int code = con.getResponseCode();
            try (InputStream in = con.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                body = br.lines().collect(Collectors.joining("\n"));
            }
            Teras.LOGGER.info("[Teras HTTP] <-- {} GET {}\n         response: {}", code, urlString, body);
        } catch (IOException e) {
            Teras.LOGGER.error("Error fetching {}", urlString, e);
            return null;
        }

        TEXT_CACHE.put(urlString, new CacheEntry(body, now + CACHE_TTL_MS));
        return body;
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
        if (urlString == null) {
            return null;
        }
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            Teras.LOGGER.error("Malformed GET URL: {}", urlString, e);
            return null;
        }
        if (!isTransportAllowed(url)) {
            return null;
        }
        String token = TerasConfig.getApiToken();
        Teras.LOGGER.info("[Teras HTTP] --> GET {} (auth={})",
                urlString, (token != null && !token.isEmpty()) ? "bearer" : "none");
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(CONNECT_TIMEOUT_MS);
            con.setReadTimeout(READ_TIMEOUT_MS);
            con.setRequestProperty("User-Agent", "Teras-SmartRotom");
            con.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                con.setRequestProperty("Authorization", "Bearer " + token);
            }
            int code = con.getResponseCode();
            String body = readBody(code < 400 ? con.getInputStream() : con.getErrorStream());
            Teras.LOGGER.info("[Teras HTTP] <-- {} GET {}\n         response: {}", code, urlString, body);
            if (code >= 400) {
                Teras.LOGGER.warn("GET {} -> HTTP {}", urlString, code);
                return null;
            }
            return body;
        } catch (IOException e) {
            Teras.LOGGER.error("Error GETting {}: {}", urlString, e.getMessage());
            return null;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    /** Fire-and-forget JSON {@code POST} on {@link Teras#EXECUTOR}, with the bearer token and HTTPS
     *  policy. Failures are logged, not thrown. */
    public static void postJson(String urlString, String jsonBody) {
        if (urlString == null || jsonBody == null) {
            return;
        }
        Teras.EXECUTOR.execute(() -> {
            URL url;
            try {
                url = new URL(urlString);
            } catch (MalformedURLException e) {
                Teras.LOGGER.error("Malformed POST URL: {}", urlString, e);
                return;
            }
            if (!isTransportAllowed(url)) {
                return;
            }
            String token = TerasConfig.getApiToken();
            // Debug: full request line + body so the exact payload sent to SmartRotom is visible.
            Teras.LOGGER.info("[Teras HTTP] --> POST {} (auth={}, {} bytes)\n         body: {}",
                    urlString, (token != null && !token.isEmpty()) ? "bearer" : "none",
                    jsonBody.getBytes(StandardCharsets.UTF_8).length, jsonBody);
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(CONNECT_TIMEOUT_MS);
                con.setReadTimeout(READ_TIMEOUT_MS);
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("User-Agent", "Teras-SmartRotom");
                con.setRequestProperty("Content-Type", "application/json");
                if (token != null && !token.isEmpty()) {
                    con.setRequestProperty("Authorization", "Bearer " + token);
                }
                try (OutputStream os = con.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
                int code = con.getResponseCode();
                String response = readBody(code < 400 ? con.getInputStream() : con.getErrorStream());
                Teras.LOGGER.info("[Teras HTTP] <-- {} POST {}\n         response: {}",
                        code, urlString, response);
                if (code >= 400) {
                    Teras.LOGGER.warn("POST {} -> HTTP {}", urlString, code);
                }
            } catch (IOException e) {
                Teras.LOGGER.error("Error POSTing to {}: {}", urlString, e.getMessage());
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        });
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
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            Teras.LOGGER.error("Malformed POST URL: {}", urlString, e);
            return null;
        }
        if (!isTransportAllowed(url)) {
            return null;
        }
        String token = TerasConfig.getApiToken();
        Teras.LOGGER.info("[Teras HTTP] --> POST {} (auth={}, {} bytes)\n         body: {}",
                urlString, (token != null && !token.isEmpty()) ? "bearer" : "none",
                jsonBody.getBytes(StandardCharsets.UTF_8).length, jsonBody);
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(CONNECT_TIMEOUT_MS);
            con.setReadTimeout(READ_TIMEOUT_MS);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("User-Agent", "Teras-SmartRotom");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                con.setRequestProperty("Authorization", "Bearer " + token);
            }
            try (OutputStream os = con.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            String body = readBody(code < 400 ? con.getInputStream() : con.getErrorStream());
            Teras.LOGGER.info("[Teras HTTP] <-- {} POST {}\n         response: {}", code, urlString, body);
            if (code >= 400) {
                Teras.LOGGER.warn("POST {} -> HTTP {}", urlString, code);
                return null;
            }
            return body;
        } catch (IOException e) {
            Teras.LOGGER.error("Error POSTing to {}: {}", urlString, e.getMessage());
            return null;
        } finally {
            if (con != null) {
                con.disconnect();
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
