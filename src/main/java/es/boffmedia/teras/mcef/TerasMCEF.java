package es.boffmedia.teras.mcef;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import es.boffmedia.teras.Teras;
import org.cef.browser.CefMessageRouter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side MCEF integration hub for SmartRotom.
 *
 * <p>Responsibilities that the montoyo {@code net.montoyo.mcef.api.API} used to provide, rebuilt
 * on CinemaMod MCEF:</p>
 * <ul>
 *   <li>Registers the {@code window.mcefQuery} / {@code window.mcefQueryCancel} JS bridge via a
 *       JCEF {@link CefMessageRouter} added to MCEF's shared {@code CefClient}.</li>
 *   <li>Creates and owns <b>one {@link MCEFBrowser} per SmartRotom item</b>, keyed by the item's
 *       {@code smartrotom_id} {@link UUID} — so each individual item shows its own page (restoring
 *       the 1.16.5 per-item pad behaviour). The client lifecycle GC in
 *       {@code es.boffmedia.teras.client.ClientEvents} creates/closes these as items enter/leave the
 *       hotbar.</li>
 *   <li>Runs JS in a page ({@code executeJavaScript}, replacing montoyo's {@code runJS}).</li>
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

    /** One browser per SmartRotom item, keyed by its {@code smartrotom_id}. */
    private static final Map<UUID, MCEFBrowser> BROWSERS = new ConcurrentHashMap<>();
    // Off-screen render size for a freshly created browser; the full-screen path resizes to the
    // window when opened. In-hand rendering maps UVs 0..1, so the exact size doesn't matter there.
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

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

    /** Returns the browser for the given SmartRotom item id, or {@code null} if none is live. */
    public static MCEFBrowser getBrowser(UUID id) {
        return id == null ? null : BROWSERS.get(id);
    }

    /**
     * Returns the browser for {@code id}, creating it (transparent, at {@code url}) on first use.
     * Returns {@code null} if {@code id} is null or MCEF is not yet initialized.
     */
    public static MCEFBrowser getOrCreateBrowser(UUID id, String url) {
        if (id == null) return null;
        MCEFBrowser existing = BROWSERS.get(id);
        if (existing != null) return existing;
        if (!MCEF.isInitialized()) {
            Teras.LOGGER.warn("SmartRotom browser requested before MCEF init (id={})", id);
            return null;
        }
        MCEFBrowser created = MCEF.createBrowser(url, true);
        created.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        BROWSERS.put(id, created);
        Teras.LOGGER.info("Created SmartRotom browser for id={} ({} live)", id, BROWSERS.size());
        return created;
    }

    /** Ids of all currently live browsers (snapshot backed by the live map's key set). */
    public static Set<UUID> activeBrowserIds() {
        return BROWSERS.keySet();
    }

    /** Closes and forgets the browser for a single item id (no-op if none). */
    public static void closeBrowser(UUID id) {
        MCEFBrowser removed = (id == null) ? null : BROWSERS.remove(id);
        if (removed != null) {
            removed.close();
            Teras.LOGGER.info("Closed SmartRotom browser for id={} ({} live)", id, BROWSERS.size());
        }
    }

    /** Closes every live browser (e.g. on disconnect). */
    public static void closeAll() {
        if (BROWSERS.isEmpty()) return;
        for (MCEFBrowser b : BROWSERS.values()) {
            b.close();
        }
        BROWSERS.clear();
        Teras.LOGGER.info("Closed all SmartRotom browsers");
    }

    /** Java -> JS for a specific item's browser. Replacement for montoyo {@code IBrowser.runJS}. */
    public static void runJS(UUID id, String js) {
        MCEFBrowser b = getBrowser(id);
        if (b != null) {
            b.executeJavaScript(js, b.getURL(), 0);
        } else {
            Teras.LOGGER.warn("runJS called with no active SmartRotom browser (id={}): {}", id, js);
        }
    }
}
