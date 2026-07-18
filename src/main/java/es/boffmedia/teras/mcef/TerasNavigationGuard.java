package es.boffmedia.teras.mcef;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.ServerConfig;
import es.boffmedia.teras.util.UrlOrigin;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;

/**
 * Confines a <b>Teras-owned</b> browser's <b>main frame</b> to the server's configured home site — the
 * enforcement half of the JS bridge's trust assumption. Without it a link or redirect could replace the
 * SmartRotom page with one that then holds a live {@code window.mcefQuery} handle.
 *
 * <p><b>Subframes are deliberately not blocked.</b> The page legitimately embeds third-party players
 * (Twitch in mewtwitch), and blocking them would buy nothing: {@link TerasQueryRouter} checks the
 * <i>frame</i> URL, so a cross-origin iframe cannot use the bridge whether or not it loaded. This
 * handler never saw subresources either — scripts, fetch and images from any origin have always been
 * allowed — so main-frame navigation is the only coherent line to draw here.</p>
 *
 * <p>Registered on MCEF's <b>shared</b> {@code CefClient}, so it also sees other mods' browsers; those
 * always pass through ({@link TerasMCEF#isTerasBrowser}).</p>
 */
final class TerasNavigationGuard extends CefRequestHandlerAdapter {

    @Override
    public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request,
                                  boolean userGesture, boolean isRedirect) {
        if (frame != null && !frame.isMain()) {
            return false;
        }
        // true cancels the navigation.
        return !isAllowed(browser, request == null ? null : request.getURL());
    }

    @Override
    public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String targetUrl,
                                    boolean userGesture) {
        // A SmartRotom browser has no tab to open into, so target=_blank / window.open is refused.
        return TerasMCEF.isTerasBrowser(browser);
    }

    /** True when {@code url} may be loaded in {@code browser}: anything not ours, or our own site. */
    private static boolean isAllowed(CefBrowser browser, String url) {
        if (!TerasMCEF.isTerasBrowser(browser)) {
            return true;
        }
        if (UrlOrigin.sameSite(ServerConfig.getHome(), url)) {
            return true;
        }
        Teras.LOGGER.warn("Blocked SmartRotom navigation to '{}' — outside the server's home site '{}'",
                url, ServerConfig.getHome());
        return false;
    }
}
