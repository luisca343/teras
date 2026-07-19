package es.boffmedia.teras.plot;

import es.boffmedia.teras.plot.model.PlotOwnership;
import es.boffmedia.teras.plot.model.PlotTransaction;
import es.boffmedia.teras.plot.sql.TerasDatabase;
import es.boffmedia.teras.region.RegionDatabase;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotDatabaseTest {

    private static final String DIM = "minecraft:overworld";
    private static final String PLOT = "parcela_mizu_01";

    @TempDir
    Path directory;

    private PlotDatabase database;
    private RegionDatabase regions;

    @BeforeEach
    void openDatabase() throws SQLException {
        TerasDatabase shared = TerasDatabase.openSqlite(directory.resolve("teras.db"));
        database = new PlotDatabase(shared);
        regions = new RegionDatabase(shared);
        // A plot is a region that has an ownership row, and the foreign key now enforces that
        // ordering: the ground has to exist before anyone can own it.
        regions.put(square(PLOT));
        regions.put(square("parcela_mizu_02"));
    }

    private static TerasRegion square(String name) {
        return TerasRegion.polygon(name, DIM, List.of(
                new RegionPoint(0, 0), new RegionPoint(10, 0),
                new RegionPoint(10, 10), new RegionPoint(0, 10)), null, null);
    }

    @AfterEach
    void closeDatabase() {
        if (database != null) database.close();
    }

    @Test
    void emptyDatabaseHasNoPlots() throws SQLException {
        assertTrue(database.loadAll().isEmpty());
    }

    @Test
    void registerCreatesAnUnownedPlot() throws SQLException {
        database.register(PLOT, DIM);
        PlotOwnership plot = database.loadAll().get(PLOT);
        assertNotNull(plot);
        assertFalse(plot.isOwned());
        assertNull(plot.owner());
        assertEquals(DIM, plot.dimension());
        assertTrue(plot.members().isEmpty());
    }

    /** Re-listing a sold plot must not repossess it. */
    @Test
    void registerKeepsExistingOwner() throws SQLException {
        UUID owner = UUID.randomUUID();
        database.register(PLOT, DIM);
        database.setOwner(PLOT, DIM, owner, null, PlotTransaction.Kind.ADMIN_GRANT, null, 0, 1000L);
        database.register(PLOT, DIM);

        assertEquals(owner, database.loadAll().get(PLOT).owner());
    }

    @Test
    void setOwnerRecordsOwnershipAndLedgerTogether() throws SQLException {
        UUID owner = UUID.randomUUID();
        database.setOwner(PLOT, DIM, owner, null, PlotTransaction.Kind.PURCHASE, null, 25000, 1234L);

        PlotOwnership plot = database.loadAll().get(PLOT);
        assertEquals(owner, plot.owner());
        assertEquals(1234L, plot.ownedSince());
        assertNull(plot.expiresAt());

        List<PlotTransaction> ledger = database.transactionsFor(PLOT);
        assertEquals(1, ledger.size());
        assertEquals(PlotTransaction.Kind.PURCHASE, ledger.get(0).kind());
        assertEquals(owner, ledger.get(0).buyer());
        assertEquals(25000, ledger.get(0).price());
        assertFalse(ledger.get(0).isConfirmed());
    }

    @Test
    void clearingOwnerDropsMembersAndLogsRevoke() throws SQLException {
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        database.setOwner(PLOT, DIM, owner, null, PlotTransaction.Kind.PURCHASE, null, 25000, 1000L);
        database.addMember(PLOT, friend, "Luisca", 1100L);
        assertEquals(Set.of(friend), database.loadAll().get(PLOT).members());

        database.setOwner(PLOT, DIM, null, null, PlotTransaction.Kind.REVOKE, owner, 0, 1200L);

        PlotOwnership plot = database.loadAll().get(PLOT);
        assertFalse(plot.isOwned());
        assertTrue(plot.members().isEmpty(), "members are the old owner's guests, not the plot's");
        assertFalse(plot.allows(friend));
        assertEquals(2, database.transactionsFor(PLOT).size());
    }

    @Test
    void membersRoundTripAndDeduplicate() throws SQLException {
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        database.setOwner(PLOT, DIM, owner, null, PlotTransaction.Kind.PURCHASE, null, 1, 1000L);
        database.addMember(PLOT, friend, "Luisca", 1100L);
        database.addMember(PLOT, friend, "Otro", 1200L);

        PlotOwnership plot = database.loadAll().get(PLOT);
        assertEquals(1, plot.members().size());
        assertTrue(plot.allows(friend));
        assertTrue(plot.allows(owner));
        assertFalse(plot.allows(UUID.randomUUID()));

        assertTrue(database.removeMember(PLOT, friend));
        assertFalse(database.removeMember(PLOT, friend));
        assertTrue(database.loadAll().get(PLOT).members().isEmpty());
    }

    /** The CASCADE only fires if PRAGMA foreign_keys is on, which is easy to lose. */
    @Test
    void unregisterCascadesMembersButKeepsLedger() throws SQLException {
        UUID owner = UUID.randomUUID();
        database.setOwner(PLOT, DIM, owner, null, PlotTransaction.Kind.PURCHASE, null, 25000, 1000L);
        database.addMember(PLOT, UUID.randomUUID(), "Luisca", 1100L);

        assertTrue(database.unregister(PLOT));
        assertFalse(database.unregister(PLOT));
        assertTrue(database.loadAll().isEmpty());
        assertEquals(1, database.transactionsFor(PLOT).size(),
                "the ledger is append-only; delisting must not erase history");

        // A member row surviving its plot would resurrect build rights on the next re-listing.
        database.register(PLOT, DIM);
        assertTrue(database.loadAll().get(PLOT).members().isEmpty());
    }

    @Test
    void pendingTransactionsAreTheUnconfirmedOnes() throws SQLException {
        database.setOwner(PLOT, DIM, UUID.randomUUID(), null,
                PlotTransaction.Kind.PURCHASE, null, 25000, 1000L);
        database.setOwner("parcela_mizu_02", DIM, UUID.randomUUID(), null,
                PlotTransaction.Kind.PURCHASE, null, 30000, 1100L);

        List<PlotTransaction> pending = database.pendingTransactions();
        assertEquals(2, pending.size());

        database.confirmTransaction(pending.get(0).id(), "starbank-tx-1");
        assertEquals(1, database.pendingTransactions().size());
        assertTrue(database.transactionsFor(PLOT).get(0).isConfirmed());
    }

    @Test
    void expiresAtRoundTripsAndDrivesExpiry() throws SQLException {
        UUID owner = UUID.randomUUID();
        database.setOwner(PLOT, DIM, owner, 5000L, PlotTransaction.Kind.PURCHASE, null, 1, 1000L);

        PlotOwnership plot = database.loadAll().get(PLOT);
        assertEquals(5000L, plot.expiresAt());
        assertFalse(plot.isExpired(4999L));
        assertTrue(plot.isExpired(5000L));
    }

    @Test
    void freeholdNeverExpires() throws SQLException {
        database.setOwner(PLOT, DIM, UUID.randomUUID(), null,
                PlotTransaction.Kind.PURCHASE, null, 1, 1000L);
        assertFalse(database.loadAll().get(PLOT).isExpired(Long.MAX_VALUE));
    }

    @Test
    void ledgerRejectsUnknownKind() throws SQLException {
        // The CHECK constraint is the last line of defence for the reconciliation contract.
        database.register(PLOT, DIM);
        assertThrows(SQLException.class, () -> {
            try (var connection = java.sql.DriverManager.getConnection(
                    "jdbc:sqlite:" + directory.resolve("teras.db").toAbsolutePath());
                 var statement = connection.prepareStatement(
                         "INSERT INTO teras_plot_transaction (region_name, kind, price, created_at) "
                                 + "VALUES (?, 'donation', 0, 0)")) {
                statement.setString(1, PLOT);
                statement.executeUpdate();
            }
        });
    }

    @Test
    void reopeningKeepsDataAndSchemaVersion() throws SQLException {
        UUID owner = UUID.randomUUID();
        database.setOwner(PLOT, DIM, owner, null, PlotTransaction.Kind.PURCHASE, null, 25000, 1000L);
        database.close();

        database = new PlotDatabase(TerasDatabase.openSqlite(directory.resolve("teras.db")));
        assertEquals(owner, database.loadAll().get(PLOT).owner());
        assertEquals(1, database.transactionsFor(PLOT).size());
    }
}
