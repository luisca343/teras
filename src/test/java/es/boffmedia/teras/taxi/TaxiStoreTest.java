package es.boffmedia.teras.taxi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Lookup and snapshot behaviour, driven through the test seam rather than the config file — writing
 * to disk needs {@code FMLPaths}, which needs a running game.
 */
class TaxiStoreTest {

    private static TaxiStop stop(String id) {
        return new TaxiStop(id, 1, 64, 2, 0F, 0F);
    }

    @BeforeEach
    void reset() {
        TaxiStore.setForTesting(List.of(stop("carretera"), stop("plaza_mayor")));
    }

    @Test
    @DisplayName("a stop is found by its id")
    void findsById() {
        assertNotNull(TaxiStore.find("carretera"));
        assertEquals("plaza_mayor", TaxiStore.find("plaza_mayor").id());
    }

    @Test
    @DisplayName("lookup normalizes, so the web's casing and stray spaces still match")
    void findIsForgiving() {
        assertNotNull(TaxiStore.find("CARRETERA"));
        assertNotNull(TaxiStore.find("  Carretera "));
    }

    @Test
    @DisplayName("an unknown id is null, not an exception — the route turns it into a 404")
    void unknownIsNull() {
        assertNull(TaxiStore.find("no-existe"));
        assertNull(TaxiStore.find(""));
        assertNull(TaxiStore.find(null));
    }

    @Test
    @DisplayName("the published list is immutable: an HTTP thread reads it while admins edit")
    void snapshotIsImmutable() {
        List<TaxiStop> snapshot = TaxiStore.all();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(stop("nueva")));
    }

    @Test
    @DisplayName("a snapshot taken before a change is unaffected by it")
    void snapshotsDoNotChangeUnderneathAReader() {
        List<TaxiStop> before = TaxiStore.all();
        TaxiStore.setForTesting(List.of(stop("otra")));

        assertEquals(2, before.size());
        assertEquals(1, TaxiStore.all().size());
    }

    @Test
    @DisplayName("stops keep their creation order — the page lists them as the admin made them")
    void keepsOrder() {
        assertEquals("carretera", TaxiStore.all().get(0).id());
        assertEquals("plaza_mayor", TaxiStore.all().get(1).id());
    }
}
