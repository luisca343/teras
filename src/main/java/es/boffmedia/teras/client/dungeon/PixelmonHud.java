package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;

import java.lang.reflect.Field;

/**
 * Puts Pixelmon's party overlay away for the length of a run, and gives it back afterwards.
 *
 * <h2>Why this cannot be done the way the other mods' HUDs are</h2>
 *
 * <p>{@link DungeonHudSuppressor} cancels {@code RenderGuiLayerEvent} by namespace, which handles
 * anything drawn as a registered GUI layer. Pixelmon's party overlay is <b>not</b> one:
 * {@code PixelmonOverlayScreen.onRenderGameOverlay} listens to {@code RenderGuiEvent.Pre} — verified in
 * the 9.3.16 bytecode — so no layer ever carries its name and the suppressor could never see it. And
 * {@code RenderGuiEvent.Pre} must not simply be cancelled: that is the event for the <i>whole</i>
 * HUD, so cancelling it takes the hotbar, hearts and chat with it.</p>
 *
 * <h2>What is used instead</h2>
 *
 * <p>Pixelmon holds the overlay's mode in a {@code public static OverlayScreenState guiState}, and that
 * enum has a {@code NONE} constant — it is the same field its own minimise keybind cycles, so this is
 * the mod's own supported way of not drawing the party. We set it to {@code NONE} on the way in and put
 * the <b>player's own previous value</b> back on the way out, because somebody who plays minimised must
 * not come out of a dungeon with the overlay maximised.</p>
 *
 * <h2>Why reflection</h2>
 *
 * <p>Pixelmon is {@code compileOnly} and absent from most servers this mod runs on. Naming the class
 * would make it a load-time requirement, "which is exactly backwards" — the same reasoning
 * {@code ParkourLimits} records. Reflection keeps it optional: every failure path here leaves the
 * overlay exactly as it was and logs once, because a Pokémon HUD that stayed visible is a cosmetic
 * disappointment and a crash on a server without Pixelmon is not.</p>
 */
public final class PixelmonHud {
    private PixelmonHud() {}

    private static final String OVERLAY = "com.pixelmonmod.pixelmon.client.gui.PixelmonOverlayScreen";
    private static final String STATE = "com.pixelmonmod.pixelmon.api.screens.OverlayScreenState";

    /** Resolved once. Null when Pixelmon is absent, or when it has moved this field. */
    private static Field guiState;
    private static Object none;
    private static boolean resolved;
    private static boolean warned;

    /** What the player had before we touched it, so it can be handed back. */
    private static Object restoreTo;
    private static boolean hidden;

    /** True while the party overlay is put away by us. */
    public static boolean isHidden() {
        return hidden;
    }

    /** Puts the party overlay away, remembering what it was. Idempotent. */
    public static void hide() {
        if (hidden || !resolve()) {
            return;
        }
        try {
            Object current = guiState.get(null);
            if (current == none) {
                // Already off, by their own keybind or their own config. Nothing to restore, and
                // nothing to do — claiming we hid it would hand them a maximised overlay on the way
                // out of a state they chose.
                return;
            }
            restoreTo = current;
            guiState.set(null, none);
            hidden = true;
        } catch (Throwable t) {
            warnOnce(t);
        }
    }

    /** Gives the overlay back exactly as it was found. Idempotent. */
    public static void restore() {
        if (!hidden) {
            return;
        }
        hidden = false;
        try {
            if (guiState != null && restoreTo != null) {
                guiState.set(null, restoreTo);
            }
        } catch (Throwable t) {
            warnOnce(t);
        } finally {
            restoreTo = null;
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return guiState != null && none != null;
        }
        resolved = true;
        try {
            Class<?> overlay = Class.forName(OVERLAY);
            Class<?> state = Class.forName(STATE);
            guiState = overlay.getField("guiState");
            none = state.getField("NONE").get(null);
        } catch (ClassNotFoundException absent) {
            // The normal case on a server without Pixelmon. Not a problem and not worth a line.
            guiState = null;
            none = null;
        } catch (Throwable t) {
            guiState = null;
            none = null;
            warnOnce(t);
        }
        return guiState != null && none != null;
    }

    private static void warnOnce(Throwable t) {
        if (warned) {
            return;
        }
        warned = true;
        Teras.LOGGER.warn("Dungeons: could not hide Pixelmon's party overlay ({}). Its HUD will stay"
                + " visible inside runs; nothing else is affected.", t.toString());
    }
}
