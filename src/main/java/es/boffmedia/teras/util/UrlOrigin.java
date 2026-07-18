package es.boffmedia.teras.util;

import java.net.URI;
import java.util.Locale;

/**
 * Host comparison for the SmartRotom trust boundary. Common to both sides: the server warns about a
 * misconfigured {@code home}, the client enforces it on the MCEF browser.
 *
 * <p>Matching is on the parsed <b>host</b>, never the URL string — a substring test
 * ({@code url.contains("smartrotom")}) accepts {@code https://evil.example/smartrotom}.</p>
 */
public final class UrlOrigin {
    private UrlOrigin() {}

    /**
     * The lowercase host of {@code url}, or {@code null} if it is not an absolute URL with one.
     * {@code null} is not a host and never matches anything.
     */
    public static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null || host.isBlank() ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * True when {@code url} is served by the same site as {@code home}: an exact host match, or a
     * subdomain of it. The subdomain rule matches a {@code .}-prefixed suffix, so {@code notteras.es}
     * does not match {@code teras.es}. Two unparseable URLs are never a match.
     */
    public static boolean sameSite(String home, String url) {
        String homeHost = hostOf(home);
        String urlHost = hostOf(url);
        if (homeHost == null || urlHost == null) {
            return false;
        }
        return urlHost.equals(homeHost) || urlHost.endsWith("." + homeHost);
    }
}
