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
            return cached.body();
        }

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
            try (InputStream in = con.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                body = br.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            Teras.LOGGER.error("Error fetching {}", urlString, e);
            return null;
        }

        TEXT_CACHE.put(urlString, new CacheEntry(body, now + CACHE_TTL_MS));
        return body;
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
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(CONNECT_TIMEOUT_MS);
                con.setReadTimeout(READ_TIMEOUT_MS);
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("User-Agent", "Teras-SmartRotom");
                con.setRequestProperty("Content-Type", "application/json");
                String token = TerasConfig.getApiToken();
                if (token != null && !token.isEmpty()) {
                    con.setRequestProperty("Authorization", "Bearer " + token);
                }
                try (OutputStream os = con.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
                int code = con.getResponseCode();
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

    /** Fail-closed HTTPS policy shared with SmartRotom (see {@link TerasConfig#isRequireHttps()}). */
    private static boolean isTransportAllowed(URL url) {
        if (TerasConfig.isRequireHttps() && !"https".equalsIgnoreCase(url.getProtocol())) {
            Teras.LOGGER.error("Request refused: requireHttps is enabled but URL is not HTTPS ({})", url);
            return false;
        }
        return true;
    }
}
