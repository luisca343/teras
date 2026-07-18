package es.boffmedia.teras.region.route;

import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadRouterTest {

    private static final String DIM = "minecraft:overworld";

    private static RegionPoint p(int x, int z) {
        return new RegionPoint(x, z);
    }

    /** A road is its region's XZ bounding box, so any outline shape describes a rectangle. */
    private static TerasRegion road(String name, int minX, int minZ, int maxX, int maxZ) {
        return TerasRegion.polygon(name, DIM, List.of(
                p(minX, minZ), p(maxX, minZ), p(maxX, maxZ), p(minX, maxZ)), null, null);
    }

    private static double area(List<RegionPoint> ring) {
        double doubled = 0;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            doubled += (double) ring.get(j).getX() * ring.get(i).getZ()
                    - (double) ring.get(i).getX() * ring.get(j).getZ();
        }
        return Math.abs(doubled / 2.0);
    }

    @Test
    void routesAlongOneRoad() {
        List<double[]> path = RoadRouter.route(
                List.of(road("carretera_recta", 0, 0, 200, 8)), DIM, p(10, 4), p(190, 4));
        assertTrue(path.size() >= 2);
        assertEquals(10.0, path.get(0)[0], 0.001);
        assertEquals(190.0, path.get(path.size() - 1)[0], 0.001);
    }

    @Test
    void routesThroughAJunctionBetweenTwoRoads() {
        // An L: east-west road meeting a north-south one at their overlapping ends.
        List<double[]> path = RoadRouter.route(List.of(
                road("carretera_este", 0, 0, 100, 8),
                road("carretera_sur", 92, 0, 100, 200)), DIM, p(5, 4), p(96, 190));
        // Start, entry, junction(s), entry, end — the corner must appear, not a straight diagonal.
        assertTrue(path.size() > 2, "expected a routed path with a turn point, got " + path.size());
        boolean turnsNearCorner = path.stream()
                .anyMatch(point -> Math.abs(point[0] - 96) < 12 && Math.abs(point[1] - 4) < 12);
        assertTrue(turnsNearCorner, "route should pass through the junction near (96, 4)");
    }

    @Test
    void disconnectedRoadsFallBackToADirectLine() {
        List<double[]> path = RoadRouter.route(List.of(
                road("carretera_aqui", 0, 0, 50, 8),
                road("carretera_lejos", 900, 900, 950, 908)), DIM, p(5, 4), p(925, 904));
        // The fallback is exactly the two endpoints.
        assertEquals(2, path.size());
        assertEquals(5.0, path.get(0)[0], 0.001);
        assertEquals(925.0, path.get(1)[0], 0.001);
    }

    @Test
    void noRoadsAtAllStillYieldsADirectLine() {
        List<double[]> path = RoadRouter.route(List.of(), DIM, p(0, 0), p(100, 100));
        assertEquals(2, path.size());
    }

    @Test
    void ignoresTownsAndOtherDimensions() {
        TerasRegion town = road("pueblo_no_es_carretera", 0, 0, 100, 100);
        TerasRegion nether = TerasRegion.polygon("carretera_nether", "minecraft:the_nether",
                List.of(p(0, 0), p(50, 0), p(50, 8)), null, null);
        List<double[]> path = RoadRouter.route(List.of(town, nether), DIM, p(0, 0), p(100, 100));
        assertEquals(2, path.size(), "no usable roads, so the direct-line fallback applies");
    }

    @Test
    void subdivideDensifiesLongSegmentsWithoutMovingThem() {
        List<double[]> dense = RoadRouter.subdivide(
                List.of(new double[]{0, 0}, new double[]{100, 0}), 16.0);
        assertTrue(dense.size() > 2);
        assertEquals(0.0, dense.get(0)[0], 0.001);
        assertEquals(100.0, dense.get(dense.size() - 1)[0], 0.001);
        for (double[] point : dense) {
            assertEquals(0.0, point[1], 0.001, "a straight segment must stay straight");
        }
    }

    /**
     * The property the whole ribbon exists for: JourneyMap triangulates polygons, so a zero-area
     * outline renders nothing at all — silently.
     */
    @Test
    void ribbonHasRealArea() {
        List<RegionPoint> ring = RoadRouter.buildRibbon(
                List.of(new double[]{0, 0}, new double[]{100, 0}), 4.0);
        assertEquals(800.0, area(ring), 0.001);

        List<RegionPoint> corner = RoadRouter.buildRibbon(
                List.of(new double[]{0, 0}, new double[]{100, 0}, new double[]{100, 100}), 4.0);
        assertNotEquals(0.0, area(corner));
    }

    @Test
    void singlePointRibbonIsASquare() {
        List<RegionPoint> ring = RoadRouter.buildRibbon(List.of(new double[]{50, 50}), 4.0);
        assertEquals(4, ring.size());
        assertEquals(64.0, area(ring), 0.001);
    }

    @Test
    void routeIsDrawableEndToEnd() {
        List<double[]> path = RoadRouter.route(List.of(
                road("carretera_este", 0, 0, 100, 8),
                road("carretera_sur", 92, 0, 100, 200)), DIM, p(5, 4), p(96, 190));
        List<RegionPoint> ring = RoadRouter.buildRibbon(
                RoadRouter.subdivide(path, RoadRouter.ROUTE_POINT_SPACING), RoadRouter.ROUTE_HALF_WIDTH);
        assertTrue(ring.size() >= 4);
        assertNotEquals(0.0, area(ring));
    }
}
