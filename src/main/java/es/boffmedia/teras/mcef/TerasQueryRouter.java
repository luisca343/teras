package es.boffmedia.teras.mcef;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.ServerConfig;
import es.boffmedia.teras.util.QueryHelper;
import es.boffmedia.teras.util.UrlOrigin;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

/**
 * Bridges {@code window.mcefQuery({request, onSuccess, onFailure})} calls made by the
 * SmartRotom web app into Java, reproducing the montoyo {@code IJSQueryHandler} contract
 * on top of JCEF's message router — without modifying MCEF.
 *
 * <p>The router is installed on MCEF's <b>shared</b> {@code CefClient}, so it is offered every query
 * from every browser in the game, including other mods'. {@link #isTrusted} is therefore the trust
 * boundary: a query is answered only when it came from a browser this mod created <i>and</i> a frame
 * still on the server's home site. The permission checks downstream do not substitute for it — they
 * pass because the <i>player</i> is privileged, not the page.</p>
 */
public class TerasQueryRouter extends CefMessageRouterHandlerAdapter {

    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                           String request, boolean persistent, CefQueryCallback callback) {
        if (!isTrusted(browser, frame)) {
            // Refused, not ignored: returning false would offer the query to the next handler on the
            // shared client.
            callback.failure(403, "Query refused: untrusted origin");
            return true;
        }
        try {
            return QueryHelper.handleQuery(browser, queryId, request, persistent, JsQueryCallback.of(callback));
        } catch (Exception e) {
            Teras.LOGGER.error("Error handling JS query: {}", request, e);
            callback.failure(-1, "Internal error: " + e.getMessage());
            return true;
        }
    }

    /**
     * True when this query may act on the player's behalf. The frame is checked as well as the browser
     * because cross-origin iframes get their own {@code window.mcefQuery} — the browser being ours says
     * nothing about what runs in a subframe.
     */
    private static boolean isTrusted(CefBrowser browser, CefFrame frame) {
        if (!TerasMCEF.isTerasBrowser(browser)) {
            Teras.LOGGER.warn("Refused a SmartRotom JS query from a browser Teras does not own");
            return false;
        }
        String home = ServerConfig.getHome();
        String frameUrl = frame != null ? frame.getURL() : null;
        // Fall back to the browser's own URL only when the frame cannot say: an unknown origin must
        // never pass by default.
        if (frameUrl == null || frameUrl.isBlank()) {
            frameUrl = browser.getURL();
        }
        if (!UrlOrigin.sameSite(home, frameUrl)) {
            Teras.LOGGER.warn("Refused a SmartRotom JS query from '{}' — outside the server's home site '{}'",
                    frameUrl, home);
            return false;
        }
        return true;
    }

    @Override
    public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
        Teras.LOGGER.info("JS query {} canceled", queryId);
    }
}
