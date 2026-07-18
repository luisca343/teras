package es.boffmedia.teras.region;

import com.google.gson.Gson;
import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerasRegionTest {

    private static final String DIM = "minecraft:overworld";
    private static final Gson GSON = new Gson();

    private static TerasRegion square() {
        return TerasRegion.polygon("pueblo_test", DIM, List.of(
                new RegionPoint(0, 0), new RegionPoint(10, 0),
                new RegionPoint(10, 10), new RegionPoint(0, 10)), null, null);
    }

    @Test
    void cuboidContainsIsBlockInclusive() {
        TerasRegion region = TerasRegion.cuboid("pueblo_caja", DIM,
                new TerasRegion.Corner(0, 60, 0), new TerasRegion.Corner(10, 70, 10));
        assertTrue(region.contains(0.0, 60.0, 0.0));
        assertTrue(region.contains(10.9, 70.9, 10.9));
        assertFalse(region.contains(11.0, 65.0, 5.0));
        assertFalse(region.contains(5.0, 59.9, 5.0));
        assertFalse(region.contains(-0.1, 65.0, 5.0));
    }

    @Test
    void cuboidNormalizesSwappedCorners() {
        TerasRegion region = TerasRegion.cuboid("pueblo_caja", DIM,
                new TerasRegion.Corner(10, 70, 10), new TerasRegion.Corner(0, 60, 0));
        assertTrue(region.contains(5.0, 65.0, 5.0));
        assertEquals(0, region.getMin().getX());
        assertEquals(70, region.getMax().getY());
    }

    @Test
    void polygonContainsSquare() {
        TerasRegion region = square();
        assertTrue(region.contains(5.0, 64.0, 5.0));
        assertTrue(region.contains(5.0, -60.0, 5.0));
        assertFalse(region.contains(15.0, 64.0, 5.0));
        assertFalse(region.contains(5.0, 64.0, -1.0));
    }

    @Test
    void polygonContainsConcaveShape() {
        TerasRegion region = TerasRegion.polygon("pueblo_ele", DIM, List.of(
                new RegionPoint(0, 0), new RegionPoint(10, 0), new RegionPoint(10, 4),
                new RegionPoint(4, 4), new RegionPoint(4, 10), new RegionPoint(0, 10)), null, null);
        assertTrue(region.contains(2.0, 64.0, 2.0));
        assertTrue(region.contains(8.0, 64.0, 2.0));
        assertTrue(region.contains(2.0, 64.0, 8.0));
        assertFalse(region.contains(8.0, 64.0, 8.0));
    }

    @Test
    void polygonRespectsYRange() {
        TerasRegion region = TerasRegion.polygon("pueblo_test", DIM, List.of(
                new RegionPoint(0, 0), new RegionPoint(10, 0),
                new RegionPoint(10, 10), new RegionPoint(0, 10)), 60, 70);
        assertFalse(region.contains(5.0, 50.0, 5.0));
        assertTrue(region.contains(5.0, 65.0, 5.0));
        assertTrue(region.contains(5.0, 70.5, 5.0));
        assertFalse(region.contains(5.0, 71.1, 5.0));
    }

    @Test
    void polygonNormalizesSwappedYRange() {
        TerasRegion region = TerasRegion.polygon("pueblo_test", DIM, List.of(
                new RegionPoint(0, 0), new RegionPoint(10, 0),
                new RegionPoint(10, 10), new RegionPoint(0, 10)), 70, 60);
        assertEquals(60, region.getMinY());
        assertTrue(region.contains(5.0, 65.0, 5.0));
    }

    @Test
    void validationRejectsBadNamesAndShapes() {
        assertNull(square().validationError());
        assertNotNull(TerasRegion.polygon("Pueblo Test", DIM, square().getPoints(), null, null)
                .validationError());
        assertNotNull(TerasRegion.polygon("pueblo_test", DIM,
                List.of(new RegionPoint(0, 0), new RegionPoint(1, 1)), null, null).validationError());
        // Variant-field mismatches can only come from hand-edited json, so build them via Gson.
        TerasRegion missingCorners = GSON.fromJson(
                "{\"name\":\"pueblo_test\",\"dimension\":\"" + DIM + "\",\"shape\":\"CUBOID\"}",
                TerasRegion.class);
        assertNotNull(missingCorners.validationError());
        TerasRegion missingDimension = GSON.fromJson(
                "{\"name\":\"pueblo_test\",\"shape\":\"POLYGON\",\"points\":[]}", TerasRegion.class);
        assertNotNull(missingDimension.validationError());
    }

    @Test
    void bannerResolution() {
        assertEquals("pueblo_test", square().bannerOrNull());
        TerasRegion road = TerasRegion.polygon("carretera_norte", DIM, square().getPoints(), null, null);
        assertNull(road.bannerOrNull());
        TerasRegion explicit = square();
        explicit.setBanner("puerto_wingull");
        assertEquals("puerto_wingull", explicit.bannerOrNull());
        explicit.setBanner(TerasRegion.BANNER_NONE);
        assertNull(explicit.bannerOrNull());
    }

    @Test
    void flagDenialOnlyOnExplicitFalse() {
        TerasRegion region = square();
        assertFalse(region.deniesFlag(RegionFlag.BREAK));
        region.setFlag(RegionFlag.BREAK, false);
        assertTrue(region.deniesFlag(RegionFlag.BREAK));
        assertFalse(region.deniesFlag(RegionFlag.BUILD));
        region.setFlag(RegionFlag.BREAK, true);
        assertFalse(region.deniesFlag(RegionFlag.BREAK));
        region.setFlag(RegionFlag.BREAK, null);
        assertFalse(region.deniesFlag(RegionFlag.BREAK));
    }

    @Test
    void webArrayRoundTripSynthesizesCuboidPoints() {
        TerasRegion cuboid = TerasRegion.cuboid("pueblo_caja", DIM,
                new TerasRegion.Corner(0, 60, 0), new TerasRegion.Corner(10, 70, 10));
        cuboid.setFillColor(0x00FF00);
        String json = RegionJson.toWebArray(List.of(square(), cuboid));

        // Legacy consumers read points off every entry, including cuboids.
        assertTrue(json.contains("\"points\""));
        List<TerasRegion> parsed = RegionJson.fromWebArray(json);
        assertEquals(2, parsed.size());
        TerasRegion parsedCuboid = parsed.get(1);
        assertEquals(TerasRegion.Shape.CUBOID, parsedCuboid.getShape());
        assertEquals(4, parsedCuboid.getPoints().size());
        assertTrue(parsedCuboid.contains(5.0, 65.0, 5.0));
        assertEquals(0x00FF00, parsedCuboid.getFillColor());
    }

    @Test
    void webArrayDropsInvalidEntries() {
        String json = "[{\"name\":\"BAD NAME\",\"dimension\":\"" + DIM + "\",\"shape\":\"POLYGON\","
                + "\"points\":[{\"x\":0,\"z\":0},{\"x\":1,\"z\":0},{\"x\":1,\"z\":1}]}, null,"
                + "{\"name\":\"pueblo_ok\",\"dimension\":\"" + DIM + "\",\"shape\":\"POLYGON\","
                + "\"points\":[{\"x\":0,\"z\":0},{\"x\":9,\"z\":0},{\"x\":9,\"z\":9}]}]";
        List<TerasRegion> parsed = RegionJson.fromWebArray(json);
        assertEquals(1, parsed.size());
        assertEquals("pueblo_ok", parsed.get(0).getName());
    }

    @Test
    void outlineReturnsPolygonPointsVerbatim() {
        TerasRegion region = square();
        assertEquals(region.getPoints(), region.outline());
    }
}
