package es.boffmedia.teras.client.camera;

import es.boffmedia.teras.mcef.TerasMCEF;

import java.util.Locale;
import java.util.UUID;

/**
 * Java → camera page notifications, for state the page cannot see changing: anything a keybind altered.
 *
 * <p>Each notification is emitted twice — as a {@code window.<hook>(…)} call and as a
 * {@code CustomEvent} — so the page can pick up whichever suits it; a bundled page has no reachable
 * globals to hang a hook on. Both are optional, hence the {@code typeof} guard: calling a missing
 * function would throw into the CEF console on every keypress. See {@code docs/CAMERA.md}.</p>
 */
final class CameraPage {
    private CameraPage() {}

    /**
     * Calls {@code window.<hook>(<args>)} if it exists, and dispatches {@code event} carrying
     * {@code detail}. {@code args} and {@code detail} are JS literals, not values to escape — every
     * caller passes numbers and booleans.
     */
    static void notify(UUID camera, String hook, String args, String event, String detail) {
        TerasMCEF.runJS(camera, String.format(Locale.ROOT,
                "(function(){if(typeof window.%s==='function')window.%s(%s);"
                        + "window.dispatchEvent(new CustomEvent('%s',{detail:%s}));})();",
                hook, hook, args, event, detail));
    }
}
