package es.boffmedia.teras.plot;

import es.boffmedia.teras.plot.model.PlotOwnership;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cache only ever matters when the database is already unreachable, so a bug here surfaces
 * exactly when nothing else is working. Hence tests for the degenerate inputs rather than just the
 * happy round trip.
 */
class PlotSnapshotCacheTest {

    private static final String DIM = "minecraft:overworld";

    @TempDir
    Path directory;

    private Path cache() {
        return directory.resolve("plots-cache.json");
    }

    @Test
    void roundTripsOwnershipAndMembers() {
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        Map<String, PlotOwnership> plots = Map.of(
                "parcela_a", new PlotOwnership("parcela_a", DIM, owner, 1000L, null, Set.of(friend)),
                "parcela_b", new PlotOwnership("parcela_b", DIM, null, 0L, 5000L, Set.of()));

        PlotSnapshotCache.write(cache(), plots);
        Map<String, PlotOwnership> read = PlotSnapshotCache.read(cache());

        assertEquals(2, read.size());
        PlotOwnership a = read.get("parcela_a");
        assertEquals(owner, a.owner());
        assertEquals(1000L, a.ownedSince());
        assertNull(a.expiresAt());
        assertTrue(a.allows(friend));
        assertTrue(a.allows(owner));
        assertFalse(a.allows(UUID.randomUUID()));

        PlotOwnership b = read.get("parcela_b");
        assertFalse(b.isOwned());
        assertEquals(5000L, b.expiresAt());
        assertTrue(b.isExpired(6000L));
    }

    @Test
    void missingCacheReadsEmptyRatherThanThrowing() {
        assertTrue(PlotSnapshotCache.read(cache()).isEmpty());
    }

    /** A corrupt cache must degrade to "no cache", never take the server down on startup. */
    @Test
    void corruptCacheReadsEmpty() throws Exception {
        Files.writeString(cache(), "{ this is not json");
        assertTrue(PlotSnapshotCache.read(cache()).isEmpty());
    }

    @Test
    void nullEntriesAreDroppedRatherThanPoisoningTheSnapshot() throws Exception {
        Files.writeString(cache(), "{\"parcela_a\": null, \"parcela_b\": "
                + "{\"regionName\":\"parcela_b\",\"dimension\":\"" + DIM + "\",\"ownedSince\":0}}");
        Map<String, PlotOwnership> read = PlotSnapshotCache.read(cache());
        assertEquals(Set.of("parcela_b"), read.keySet());
        // Absent members must come back as an empty set, not null — the resolver calls allows() on it.
        assertFalse(read.get("parcela_b").allows(UUID.randomUUID()));
    }

    @Test
    void writeReplacesPreviousContents() {
        PlotSnapshotCache.write(cache(), Map.of(
                "parcela_a", new PlotOwnership("parcela_a", DIM, UUID.randomUUID(), 1L, null, Set.of())));
        PlotSnapshotCache.write(cache(), Map.of());
        assertTrue(PlotSnapshotCache.read(cache()).isEmpty());
    }

    /** The temp file must not be left behind next to the real one. */
    @Test
    void writeLeavesNoTempFile() throws Exception {
        PlotSnapshotCache.write(cache(), Map.of(
                "parcela_a", new PlotOwnership("parcela_a", DIM, null, 0L, null, Set.of())));
        try (var entries = Files.list(directory)) {
            assertEquals(Set.of("plots-cache.json"),
                    entries.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
    }
}
