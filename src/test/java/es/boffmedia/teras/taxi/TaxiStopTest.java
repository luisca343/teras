package es.boffmedia.teras.taxi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Stop ids: they travel to the web, come back, and end up in the player's visible ledger. */
class TaxiStopTest {

    @Test
    @DisplayName("ids are stored trimmed and lowercased, so the web's casing cannot miss")
    void normalizes() {
        assertEquals("carretera", new TaxiStop("  Carretera  ", 0, 0, 0, 0, 0).id());
        assertEquals("plaza_mayor", TaxiStop.normalizeId("PLAZA_Mayor"));
    }

    @Test
    @DisplayName("a null or empty id is refused with a reason")
    void emptyIsRefused() {
        assertNotNull(TaxiStop.idProblem(null));
        assertNotNull(TaxiStop.idProblem(""));
        assertNotNull(TaxiStop.idProblem("   "));
    }

    @Test
    @DisplayName("spaces and accents are refused rather than silently rewritten")
    void awkwardCharactersAreRefused() {
        // A silent rewrite would mean the stop an admin created is not the one they named.
        assertNotNull(TaxiStop.idProblem("plaza mayor"));
        assertNotNull(TaxiStop.idProblem("estación"));
        assertNotNull(TaxiStop.idProblem("plaza/mayor"));
    }

    @Test
    @DisplayName("letters, digits, dashes and underscores are fine")
    void ordinaryIdsPass() {
        assertNull(TaxiStop.idProblem("carretera"));
        assertNull(TaxiStop.idProblem("plaza_mayor"));
        assertNull(TaxiStop.idProblem("ruta-66"));
        assertNull(TaxiStop.idProblem("CARRETERA"));
    }

    @Test
    @DisplayName("an over-long id is refused — it has to be readable in a ledger row")
    void tooLongIsRefused() {
        assertNull(TaxiStop.idProblem("a".repeat(TaxiStop.MAX_ID_LENGTH)));
        assertNotNull(TaxiStop.idProblem("a".repeat(TaxiStop.MAX_ID_LENGTH + 1)));
    }

    @Test
    @DisplayName("the facing is kept, so passengers do not arrive looking at a wall")
    void keepsFacing() {
        TaxiStop stop = new TaxiStop("plaza", 10.5, 64.0, -3.5, 90F, -5F);

        assertEquals(90F, stop.yaw());
        assertEquals(-5F, stop.pitch());
        assertEquals(10.5, stop.x());
        assertEquals(64.0, stop.y());
        assertEquals(-3.5, stop.z());
    }
}
