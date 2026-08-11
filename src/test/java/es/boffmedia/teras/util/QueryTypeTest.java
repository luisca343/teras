package es.boffmedia.teras.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression tests for the {@code mcefQuery} name matching.
 *
 * <p>The bug these exist for: the SmartRotom web sends camelCase (`{"query":"getMisiones"}`), while
 * the dispatch did {@code QueryType.valueOf(type.toUpperCase())} — {@code "GETMISIONES"} never equals
 * {@code GET_MISIONES}, so <b>every multi-word query</b> failed as "Unknown query type" before the
 * switch. 1.16.5 had the identical code, so this was inherited, not introduced by the port.</p>
 */
class QueryTypeTest {

    @Test
    void resolvesTheCamelCaseTheWebActuallySends() {
        // Exactly what was observed in the log: {"query":"getMisiones"} -> Unknown query type.
        assertEquals(QueryType.GET_MISIONES, QueryType.fromQuery("getMisiones"));
        assertEquals(QueryType.GET_USER_DATA, QueryType.fromQuery("getUserData"));
        assertEquals(QueryType.GET_SPAWNS, QueryType.fromQuery("getSpawns"));
        assertEquals(QueryType.GET_PLAYERS, QueryType.fromQuery("getPlayers"));
        assertEquals(QueryType.CHAT_MESSAGE, QueryType.fromQuery("chatMessage"));
        assertEquals(QueryType.ADD_WAYPOINT, QueryType.fromQuery("addWaypoint"));
        assertEquals(QueryType.OPEN_PC, QueryType.fromQuery("openPC"));
        assertEquals(QueryType.DAR_CAJA, QueryType.fromQuery("darCaja"));
        assertEquals(QueryType.SET_CALL, QueryType.fromQuery("setCall"));
        assertEquals(QueryType.LEAVE_CALL, QueryType.fromQuery("leaveCall"));
        assertEquals(QueryType.TAKE_SCREENSHOT, QueryType.fromQuery("takeScreenshot"));
        assertEquals(QueryType.GET_ZOOM_LEVEL, QueryType.fromQuery("getZoomLevel"));
        assertEquals(QueryType.SET_ZOOM_LEVEL, QueryType.fromQuery("setZoomLevel"));
        assertEquals(QueryType.GET_WAYPOINTS, QueryType.fromQuery("getWaypoints"));
        assertEquals(QueryType.MC_JOIN_SERVER, QueryType.fromQuery("mcJoinServer"));
    }

    @Test
    void everyConstantIsReachableFromItsCamelCaseName() {
        // Guards against a future constant being added that the web can't address.
        for (QueryType type : QueryType.values()) {
            String camel = toCamel(type.name());
            assertEquals(type, QueryType.fromQuery(camel),
                    () -> "no camelCase route to " + type + " (tried '" + camel + "')");
        }
    }

    @Test
    void stillAcceptsSnakeCaseAndUpperCase() {
        // The old contract must keep working — some callers may already send these.
        assertEquals(QueryType.GET_MISIONES, QueryType.fromQuery("GET_MISIONES"));
        assertEquals(QueryType.GET_MISIONES, QueryType.fromQuery("get_misiones"));
        assertEquals(QueryType.GET_MISIONES, QueryType.fromQuery("Get_Misiones"));
        assertEquals(QueryType.GET_MISIONES, QueryType.fromQuery("GETMISIONES"));
    }

    @Test
    void rejectsUnknownQueries() {
        assertNull(QueryType.fromQuery("nope"));
        assertNull(QueryType.fromQuery(""));
        assertNull(QueryType.fromQuery(null));
        assertNull(QueryType.fromQuery("getMisionesExtra"));
    }

    @Test
    void isNotBrokenByTheTurkishLocale() {
        // A default-locale toUpperCase() maps 'i' -> 'İ' under tr-TR, which would break getMisiones
        // only on Turkish machines. QueryType normalizes with Locale.ROOT.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertNotNull(QueryType.fromQuery("getMisiones"));
            assertEquals(QueryType.GET_MISIONES, QueryType.fromQuery("getMisiones"));
        } finally {
            Locale.setDefault(original);
        }
    }

    /** GET_MISIONES -> getMisiones */
    private static String toCamel(String constant) {
        String[] parts = constant.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }
}
