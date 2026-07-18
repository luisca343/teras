package es.boffmedia.teras.karts.editor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which circuit each admin is editing, and whether they want it drawn in the world.
 *
 * <p>Deliberately transient: a session is a UI convenience ("the track I am working on"), not data
 * — the circuit itself is saved to {@code circuitos.json} after every change, so losing sessions on
 * restart costs an admin one {@code /karts circuito editar} and nothing else.</p>
 */
public final class TrackEditorSessions {
    private TrackEditorSessions() {}

    private static final Map<UUID, String> EDITING = new ConcurrentHashMap<>();
    private static final Map<UUID, String> VISUALISING = new ConcurrentHashMap<>();

    public static void edit(UUID player, String trackName) {
        EDITING.put(player, trackName);
    }

    /** The circuit this admin is editing, or null. */
    public static String editing(UUID player) {
        return EDITING.get(player);
    }

    public static void stopEditing(UUID player) {
        EDITING.remove(player);
        VISUALISING.remove(player);
    }

    /** Starts drawing {@code trackName} for this admin until they turn it off or log out. */
    public static void showTrack(UUID player, String trackName) {
        VISUALISING.put(player, trackName);
    }

    public static void hideTrack(UUID player) {
        VISUALISING.remove(player);
    }

    public static boolean isVisualising(UUID player) {
        return VISUALISING.containsKey(player);
    }

    /** Player→circuit for everyone currently being shown a track. */
    public static Map<UUID, String> visualising() {
        return Map.copyOf(VISUALISING);
    }

    /** Called on logout so a disconnected admin leaves nothing behind. */
    public static void clear(UUID player) {
        EDITING.remove(player);
        VISUALISING.remove(player);
    }
}
