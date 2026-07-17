package es.boffmedia.teras.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code query} values {@code window.mcefQuery({query:"..."})} accepts, split out of
 * {@code QueryHelper} so the name matching can be unit-tested without a Minecraft or CEF runtime.
 *
 * <p><b>Why {@link #fromQuery} exists.</b> 1.16.5 (and this port, initially) resolved the query with
 * {@code valueOf(type.toUpperCase())}. That only ever matches a caller sending
 * {@code SCREAMING_SNAKE_CASE} — the SmartRotom web sends camelCase (`getMisiones`), which uppercases
 * to {@code GETMISIONES} and never equals {@code GET_MISIONES}, so every multi-word query died as
 * "Unknown query type" before reaching the switch. Matching now ignores both case and underscores, so
 * {@code getMisiones}, {@code get_misiones} and {@code GET_MISIONES} all resolve.</p>
 */
enum QueryType {
    ADD_WAYPOINT,
    GET_USER_DATA,
    GET_WAYPOINTS,
    OPEN_PC,
    GET_SPAWNS,
    SET_CALL,
    LEAVE_CALL,
    CHAT_MESSAGE,
    GET_PLAYERS,
    GET_MISIONES,
    DAR_CAJA,
    TAKE_SCREENSHOT,
    GET_ZOOM_LEVEL,
    SET_ZOOM_LEVEL,
    GET_FLASHLIGHT,
    SET_FLASHLIGHT;

    /** Normalized constant name -> constant. Built once; the enum is fixed at class-load. */
    private static final Map<String, QueryType> BY_NORMALIZED_NAME = new HashMap<>();

    static {
        for (QueryType type : values()) {
            BY_NORMALIZED_NAME.put(normalize(type.name()), type);
        }
    }

    /** The type for a {@code query} string, or {@code null} if it names no known query. */
    static QueryType fromQuery(String query) {
        if (query == null) {
            return null;
        }
        return BY_NORMALIZED_NAME.get(normalize(query));
    }

    /**
     * Case- and underscore-insensitive key. {@link Locale#ROOT} matters: under a Turkish locale the
     * default {@code toUpperCase()} maps {@code i} to {@code İ}, which would break {@code getMisiones}
     * on exactly the machines least likely to be testing it.
     */
    private static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT).replace("_", "");
    }
}
