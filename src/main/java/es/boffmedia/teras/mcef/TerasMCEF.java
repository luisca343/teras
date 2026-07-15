package es.boffmedia.teras.mcef;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import es.boffmedia.teras.Teras;
import org.cef.browser.CefMessageRouter;

/**
 * Client-side MCEF integration hub for SmartRotom.
 *
 * <p>Responsibilities that the montoyo {@code net.montoyo.mcef.api.API} used to provide, rebuilt
 * on CinemaMod MCEF:</p>
 * <ul>
 *   <li>Registers the {@code window.mcefQuery} / {@code window.mcefQueryCancel} JS bridge via a
 *       JCEF {@link CefMessageRouter} added to MCEF's shared {@code CefClient}.</li>
 *   <li>Creates and owns the SmartRotom browser instance ({@link MCEFBrowser}).</li>
 *   <li>Runs JS in the page ({@code executeJavaScript}, replacing montoyo's {@code runJS}).</li>
 * </ul>
 *
 * <p>The JS bridge function names ({@link #JS_QUERY_FN}/{@link #JS_CANCEL_FN}) are reproduced
 * from the montoyo {@code mcef-1.1.0.jar} the 1.16.5 mod shipped with, so the SmartRotom website's
 * {@code window.mcefQuery(...)} calls resolve exactly as before.</p>
 */
public final class TerasMCEF {
    private TerasMCEF() {}

    public static final String JS_QUERY_FN = "mcefQuery";
    // Verified against the shipped mcef-1.1.0.jar: montoyo overrode only the query function to
    // "mcefQuery" and left the cancel function at JCEF's default "cefQueryCancel".
    public static final String JS_CANCEL_FN = "cefQueryCancel";

    private static volatile boolean routerRegistered = false;
    private static MCEFBrowser browser;

    /** Call once during client setup. Registers the JS bridge as soon as MCEF is initialized. */
    public static void init() {
        if (MCEF.isInitialized()) {
            registerRouter();
        } else {
            // Fires during MCEF.initialize(); if MCEF is already up we handled it above.
            MCEF.scheduleForInit(success -> {
                if (success) registerRouter();
                else Teras.LOGGER.error("MCEF failed to initialize; SmartRotom browser disabled");
            });
        }
    }

    private static synchronized void registerRouter() {
        if (routerRegistered) return;
        try {
            CefMessageRouter router = CefMessageRouter.create(
                    new CefMessageRouter.CefMessageRouterConfig(JS_QUERY_FN, JS_CANCEL_FN));
            router.addHandler(new TerasQueryRouter(), true);
            MCEF.getClient().getHandle().addMessageRouter(router);
            routerRegistered = true;
            Teras.LOGGER.info("SmartRotom JS bridge registered ({} / {})", JS_QUERY_FN, JS_CANCEL_FN);
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to register SmartRotom JS bridge", e);
        }
    }

    public static boolean isReady() {
        return MCEF.isInitialized();
    }

    /**
     * Returns the shared SmartRotom browser, creating it (transparent) at {@code url} on first use.
     * Returns {@code null} if MCEF is not yet initialized.
     */
    public static MCEFBrowser getOrCreateBrowser(String url) {
        if (browser == null) {
            if (!MCEF.isInitialized()) {
                Teras.LOGGER.warn("SmartRotom requested before MCEF init");
                return null;
            }
            browser = MCEF.createBrowser(url, true);
        }
        return browser;
    }

    public static MCEFBrowser getBrowser() {
        return browser;
    }

    /** Java -> JS. Replacement for montoyo {@code IBrowser.runJS}. */
    public static void runJS(String js) {
        MCEFBrowser b = browser;
        if (b != null) {
            b.executeJavaScript(js, b.getURL(), 0);
        } else {
            Teras.LOGGER.warn("runJS called with no active SmartRotom browser: {}", js);
        }
    }

    public static void closeBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }
}
