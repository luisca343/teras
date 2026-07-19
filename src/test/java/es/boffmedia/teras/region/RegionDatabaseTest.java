package es.boffmedia.teras.region;

import es.boffmedia.teras.plot.PlotDatabase;
import es.boffmedia.teras.plot.model.PlotTransaction;
import es.boffmedia.teras.plot.sql.TerasDatabase;
import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionDatabaseTest {

    private static final String DIM = "minecraft:overworld";

    @TempDir
    Path directory;

    private TerasDatabase shared;
    private RegionDatabase regions;

    @BeforeEach
    void open() throws SQLException {
        shared = TerasDatabase.openSqlite(directory.resolve("teras.db"));
        regions = new RegionDatabase(shared);
    }

    private static TerasRegion polygon(String name) {
        return TerasRegion.polygon(name, DIM, List.of(
                new RegionPoint(0, 0), new RegionPoint(10, 0),
                new RegionPoint(10, 10), new RegionPoint(0, 10)), null, null);
    }

    @Test
    void emptyCatalogIsEmpty() throws SQLException {
        assertTrue(regions.isEmpty());
        assertTrue(regions.loadAll().isEmpty());
    }

    /** Every field has to survive the trip, including the ones only the map cares about. */
    @Test
    void polygonRoundTripsWholeRegion() throws SQLException {
        TerasRegion region = TerasRegion.polygon("pueblo_mizu", DIM, List.of(
                new RegionPoint(-50, -50), new RegionPoint(50, -50),
                new RegionPoint(50, 50), new RegionPoint(-50, 50)), 60, 120);
        region.setPriority(7);
        region.setPurchasable(true);
        region.setPrice(25000);
        region.setFillColor(0x00FF00);
        region.setStrokeColor(0x0000FF);
        region.setBanner("puerto_wingull");
        region.setCreatedBy("Luisca");
        region.setCreatedAt(1234L);
        region.setFlag(RegionFlag.BREAK, false);
        region.setFlag(RegionFlag.PVP, false);
        regions.put(region);

        TerasRegion read = regions.loadAll().get("pueblo_mizu");
        assertNotNull(read);
        assertEquals(TerasRegion.Shape.POLYGON, read.getShape());
        assertEquals(4, read.getPoints().size());
        assertEquals(60, read.getMinY());
        assertEquals(120, read.getMaxY());
        assertEquals(7, read.getPriority());
        assertTrue(read.isPurchasable());
        assertEquals(25000, read.getPrice());
        assertEquals(0x00FF00, read.getFillColor());
        assertEquals(0x0000FF, read.getStrokeColor());
        assertEquals("puerto_wingull", read.getBanner());
        assertEquals("Luisca", read.getCreatedBy());
        assertEquals(1234L, read.getCreatedAt());
        assertTrue(read.deniesFlag(RegionFlag.BREAK));
        assertTrue(read.deniesFlag(RegionFlag.PVP));
        assertFalse(read.deniesFlag(RegionFlag.BUILD));
        assertTrue(read.contains(0.0, 64.0, 0.0));
    }

    @Test
    void cuboidRoundTripsItsCorners() throws SQLException {
        TerasRegion region = TerasRegion.cuboid("pueblo_caja", DIM,
                new TerasRegion.Corner(0, 60, 0), new TerasRegion.Corner(10, 70, 10));
        regions.put(region);

        TerasRegion read = regions.loadAll().get("pueblo_caja");
        assertEquals(TerasRegion.Shape.CUBOID, read.getShape());
        assertEquals(0, read.getMin().getX());
        assertEquals(70, read.getMax().getY());
        assertTrue(read.contains(5.0, 65.0, 5.0));
        assertFalse(read.contains(5.0, 59.0, 5.0));
    }

    /** Winding order decides what "inside" means, so the point list must come back in order. */
    @Test
    void polygonPointOrderIsPreserved() throws SQLException {
        TerasRegion lShape = TerasRegion.polygon("pueblo_ele", DIM, List.of(
                new RegionPoint(0, 0), new RegionPoint(10, 0), new RegionPoint(10, 4),
                new RegionPoint(4, 4), new RegionPoint(4, 10), new RegionPoint(0, 10)), null, null);
        regions.put(lShape);

        TerasRegion read = regions.loadAll().get("pueblo_ele");
        assertEquals(List.of(0, 10, 10, 4, 4, 0),
                read.getPoints().stream().map(RegionPoint::getX).toList());
        // The concave corner only stays outside if the order survived.
        assertTrue(read.contains(2.0, 64.0, 8.0));
        assertFalse(read.contains(8.0, 64.0, 8.0));
    }

    @Test
    void regionWithNoFlagsReadsBackWithNone() throws SQLException {
        regions.put(polygon("carretera_norte"));
        TerasRegion read = regions.loadAll().get("carretera_norte");
        assertNull(read.getFlags());
        for (RegionFlag flag : RegionFlag.values()) {
            assertFalse(read.deniesFlag(flag));
        }
    }

    @Test
    void putReplacesAnExistingRegion() throws SQLException {
        regions.put(polygon("pueblo_mizu"));
        TerasRegion changed = polygon("pueblo_mizu");
        changed.setPriority(99);
        regions.put(changed);

        assertEquals(1, regions.loadAll().size());
        assertEquals(99, regions.loadAll().get("pueblo_mizu").getPriority());
    }

    @Test
    void replaceAllSwapsTheWholeCatalog() throws SQLException {
        regions.put(polygon("pueblo_a"));
        regions.put(polygon("pueblo_b"));
        regions.replaceAll(List.of(polygon("pueblo_c")));

        assertEquals(java.util.Set.of("pueblo_c"), regions.loadAll().keySet());
    }

    @Test
    void removeReportsWhetherItExisted() throws SQLException {
        regions.put(polygon("pueblo_mizu"));
        assertTrue(regions.remove("pueblo_mizu"));
        assertFalse(regions.remove("pueblo_mizu"));
        assertTrue(regions.loadAll().isEmpty());
    }

    // ---- The foreign key ----

    /** A plot cannot exist for a region that does not: that is the whole point of the key. */
    @Test
    void plotCannotReferenceAMissingRegion() {
        PlotDatabase plots = new PlotDatabase(shared);
        assertThrows(SQLException.class, () -> plots.register("parcela_fantasma", DIM));
    }

    /** Deleting the ground under an owner is refused rather than silently orphaning the plot. */
    @Test
    void regionThatIsAPlotCannotBeDeleted() throws SQLException {
        regions.put(polygon("parcela_mizu_01"));
        PlotDatabase plots = new PlotDatabase(shared);
        plots.setOwner("parcela_mizu_01", DIM, UUID.randomUUID(), null,
                PlotTransaction.Kind.ADMIN_GRANT, null, 0, 1000L);

        assertThrows(SQLException.class, () -> regions.remove("parcela_mizu_01"));
        assertNotNull(regions.loadAll().get("parcela_mizu_01"));
    }

    @Test
    void regionCanBeDeletedOnceThePlotIsRetired() throws SQLException {
        regions.put(polygon("parcela_mizu_01"));
        PlotDatabase plots = new PlotDatabase(shared);
        plots.setOwner("parcela_mizu_01", DIM, UUID.randomUUID(), null,
                PlotTransaction.Kind.ADMIN_GRANT, null, 0, 1000L);

        plots.unregister("parcela_mizu_01");
        assertTrue(regions.remove("parcela_mizu_01"));
    }

    // ---- Durability ----

    @Test
    void catalogSurvivesReopening() throws SQLException {
        regions.put(polygon("pueblo_mizu"));
        shared.close();

        shared = TerasDatabase.openSqlite(directory.resolve("teras.db"));
        Map<String, TerasRegion> read = new RegionDatabase(shared).loadAll();
        assertEquals(1, read.size());
        assertTrue(read.get("pueblo_mizu").contains(5.0, 64.0, 5.0));
    }

    /** Re-opening must not re-run v2 and rebuild the plot table under live data. */
    @Test
    void reopeningDoesNotRerunMigrations() throws SQLException {
        regions.put(polygon("parcela_mizu_01"));
        PlotDatabase plots = new PlotDatabase(shared);
        plots.setOwner("parcela_mizu_01", DIM, UUID.randomUUID(), null,
                PlotTransaction.Kind.PURCHASE, null, 25000, 1000L);
        shared.close();

        shared = TerasDatabase.openSqlite(directory.resolve("teras.db"));
        assertEquals(1, new PlotDatabase(shared).transactionsFor("parcela_mizu_01").size());
        assertNotNull(new PlotDatabase(shared).loadAll().get("parcela_mizu_01").owner());
    }

    /** One unreadable row costs that region, not the catalog — protection everywhere depends on it. */
    @Test
    void corruptRowIsSkippedNotFatal() throws Exception {
        regions.put(polygon("pueblo_bueno"));
        regions.put(polygon("pueblo_malo"));
        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("teras.db").toAbsolutePath());
             var statement = connection.prepareStatement(
                     "UPDATE teras_region SET geometry = 'not json' WHERE name = 'pueblo_malo'")) {
            statement.executeUpdate();
        }

        Map<String, TerasRegion> read = regions.loadAll();
        assertEquals(java.util.Set.of("pueblo_bueno"), read.keySet());
    }
}
