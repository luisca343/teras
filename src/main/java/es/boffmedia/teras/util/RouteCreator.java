package es.boffmedia.teras.util;

import es.boffmedia.teras.Teras;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.impl.ClientAPI;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Draws a road route on the player's JourneyMap.
 *
 * <p>Roads are authored as WorldGuard cuboid regions named {@code carretera_*}.
 * Each one is treated as an axis-aligned footprint in the XZ plane. Two roads are
 * considered connected when their footprints overlap (or are within
 * {@link #CONNECT_TOLERANCE} blocks of each other), and the centre of that overlap
 * becomes a "turn point". We then run Dijkstra over a graph of road centres + turn
 * points to find the shortest chain of roads from the start to the end, and draw a
 * single connected line through it.
 *
 * <p>Everything here runs on the client (JourneyMap's API is client-only); it is
 * triggered from the server via {@code CMessageFindPath}.
 */
public class RouteCreator {

    /** Roads whose footprints are within this many blocks are treated as connected. */
    private static final int CONNECT_TOLERANCE = 2;
    /** Y level used for every drawn point (routing happens in 2D on the map). */
    private static final int DRAW_Y = 64;
    /** Half-width, in blocks, of the drawn route band. */
    private static final double ROUTE_HALF_WIDTH = 4.0;
    private static final int ROUTE_COLOR = 0x00B0FF;
    private static final String MOD_ID = "journeymap";
    private static final String ROUTE_DISPLAY_ID = "teras_route";

    /** The last route we drew, kept so we can clear it before drawing a new one. */
    private static PolygonOverlay currentRoute;

    public static void createRoute(Point start, Point end) {
        Teras.getLogger().info("Creating route from " + start + " to " + end);

        IClientAPI jmAPI = ClientAPI.INSTANCE;
        if (jmAPI == null) {
            Teras.getLogger().warn("JourneyMap client API not available; cannot draw route");
            return;
        }

        // Regions are normally loaded at login by CMessageConfigServer; load on demand
        // if that never happened (or failed) so the route can still be computed.
        if (Teras.regions == null) {
            Teras.getLogger().warn("Teras.regions is null; loading regions on demand");
            PolygonCreator.loadRegions();
        }

        List<Road> roads = buildRoads();
        List<double[]> path = computePath(roads, start, end);

        if (path.isEmpty()) {
            Teras.getLogger().warn("No road route found between the points; drawing a direct line as fallback");
            path = Arrays.asList(
                    new double[]{start.getX(), start.getZ()},
                    new double[]{end.getX(), end.getZ()});
        }

        drawRoute(jmAPI, path);
    }

    // ------------------------------------------------------------------
    // Road network
    // ------------------------------------------------------------------

    /** Axis-aligned footprint of one road in the XZ plane. */
    private static final class Road {
        final double minX, minZ, maxX, maxZ;

        Road(double minX, double minZ, double maxX, double maxZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        boolean contains(double x, double z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** Euclidean distance from a point to this rectangle (0 if inside). */
        double distanceTo(double x, double z) {
            double dx = Math.max(Math.max(minX - x, x - maxX), 0);
            double dz = Math.max(Math.max(minZ - z, z - maxZ), 0);
            return Math.sqrt(dx * dx + dz * dz);
        }

        boolean connectsTo(Road o) {
            return minX - CONNECT_TOLERANCE <= o.maxX && o.minX - CONNECT_TOLERANCE <= maxX
                    && minZ - CONNECT_TOLERANCE <= o.maxZ && o.minZ - CONNECT_TOLERANCE <= maxZ;
        }
    }

    private static List<Road> buildRoads() {
        List<Road> roads = new ArrayList<>();
        if (Teras.regions == null) {
            Teras.getLogger().error("Teras.regions is null; cannot build road network");
            return roads;
        }

        int skipped = 0;
        for (PolygonCreator.Region region : Teras.regions) {
            if (region.getName() == null || !region.getName().startsWith("carretera_")) continue;

            List<PolygonCreator.Point> points = region.getPoints();
            if (points == null || points.isEmpty()) {
                // The region is named like a road but carries no polygon geometry. This
                // usually means the /regions JSON delivered it as raw cuboid fields
                // (min_x/max_x/...) that Gson dropped because Region has no such fields.
                Teras.getLogger().warn("Road region '" + region.getName()
                        + "' has no points; check the /regions JSON schema (cuboid vs polygon)");
                skipped++;
                continue;
            }

            double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (PolygonCreator.Point p : points) {
                minX = Math.min(minX, p.getX());
                maxX = Math.max(maxX, p.getX());
                minZ = Math.min(minZ, p.getZ());
                maxZ = Math.max(maxZ, p.getZ());
            }
            roads.add(new Road(minX, minZ, maxX, maxZ));
        }

        Teras.getLogger().info("Built road network with " + roads.size() + " road(s)"
                + (skipped > 0 ? " (" + skipped + " road region(s) skipped for missing geometry)" : ""));
        return roads;
    }

    // ------------------------------------------------------------------
    // Path finding (Dijkstra over road centres + turn points)
    // ------------------------------------------------------------------

    private static List<double[]> computePath(List<Road> roads, Point start, Point end) {
        if (roads.isEmpty()) return Collections.emptyList();

        List<double[]> nodePos = new ArrayList<>();      // node index -> {x, z}
        List<List<double[]>> adj = new ArrayList<>();    // node index -> list of {neighborIndex, weight}

        int startNode = addNode(nodePos, adj, start.getX(), start.getZ());
        int endNode = addNode(nodePos, adj, end.getX(), end.getZ());

        // For each road, the junction nodes (overlap centres) that sit on it. We route
        // through these turn points rather than road centres, so the line hugs the
        // actual intersections instead of detouring to the middle of long roads.
        List<List<Integer>> roadJunctions = new ArrayList<>();
        for (int i = 0; i < roads.size(); i++) {
            roadJunctions.add(new ArrayList<>());
        }

        for (int i = 0; i < roads.size(); i++) {
            for (int j = i + 1; j < roads.size(); j++) {
                Road a = roads.get(i);
                Road b = roads.get(j);
                if (!a.connectsTo(b)) continue;

                double turnX = (Math.max(a.minX, b.minX) + Math.min(a.maxX, b.maxX)) / 2.0;
                double turnZ = (Math.max(a.minZ, b.minZ) + Math.min(a.maxZ, b.maxZ)) / 2.0;
                int turn = addNode(nodePos, adj, turnX, turnZ);
                roadJunctions.get(i).add(turn);
                roadJunctions.get(j).add(turn);
            }
        }

        // Any two junctions on the same road are mutually reachable (travel along it).
        for (List<Integer> junctions : roadJunctions) {
            for (int a = 0; a < junctions.size(); a++) {
                for (int b = a + 1; b < junctions.size(); b++) {
                    link(nodePos, adj, junctions.get(a), junctions.get(b));
                }
            }
        }

        // Snap start/end to the road that contains them, else the nearest road, and
        // connect them to that road's junctions.
        int startRoad = snapToRoad(roads, start.getX(), start.getZ());
        int endRoad = snapToRoad(roads, end.getX(), end.getZ());
        if (startRoad < 0 || endRoad < 0) return Collections.emptyList();
        for (int turn : roadJunctions.get(startRoad)) link(nodePos, adj, startNode, turn);
        for (int turn : roadJunctions.get(endRoad)) link(nodePos, adj, endNode, turn);
        // Same road: a direct hop, no junction needed.
        if (startRoad == endRoad) link(nodePos, adj, startNode, endNode);

        return dijkstra(nodePos, adj, startNode, endNode);
    }

    private static int addNode(List<double[]> pos, List<List<double[]>> adj, double x, double z) {
        pos.add(new double[]{x, z});
        adj.add(new ArrayList<>());
        return pos.size() - 1;
    }

    private static void link(List<double[]> pos, List<List<double[]>> adj, int a, int b) {
        double w = distance(pos.get(a), pos.get(b));
        adj.get(a).add(new double[]{b, w});
        adj.get(b).add(new double[]{a, w});
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dz = a[1] - b[1];
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int snapToRoad(List<Road> roads, double x, double z) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < roads.size(); i++) {
            if (roads.get(i).contains(x, z)) return i;
            double d = roads.get(i).distanceTo(x, z);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static List<double[]> dijkstra(List<double[]> pos, List<List<double[]>> adj, int src, int dst) {
        int n = pos.size();
        double[] distTo = new double[n];
        int[] prev = new int[n];
        boolean[] settled = new boolean[n];
        Arrays.fill(distTo, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        distTo[src] = 0;

        // Lazy-deletion priority queue: stale entries are skipped via 'settled'.
        PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingDouble(i -> distTo[i]));
        pq.add(src);

        while (!pq.isEmpty()) {
            int u = pq.poll();
            if (settled[u]) continue;
            settled[u] = true;
            if (u == dst) break;

            for (double[] edge : adj.get(u)) {
                int v = (int) edge[0];
                double w = edge[1];
                if (distTo[u] + w < distTo[v]) {
                    distTo[v] = distTo[u] + w;
                    prev[v] = u;
                    pq.add(v);
                }
            }
        }

        if (distTo[dst] == Double.POSITIVE_INFINITY) return Collections.emptyList();

        LinkedList<double[]> path = new LinkedList<>();
        for (int at = dst; at != -1; at = prev[at]) {
            path.addFirst(pos.get(at));
        }
        return path;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private static void drawRoute(IClientAPI jmAPI, List<double[]> path) {
        // Clear the previous route so successive calls don't stack overlays.
        if (currentRoute != null) {
            try {
                jmAPI.remove(currentRoute);
            } catch (Exception ignored) {
            }
            currentRoute = null;
        }

        // JourneyMap 1.16.5 has no polyline overlay, only closed polygons that it
        // triangulates to fill. A zero-area "line" triangulates to nothing and never
        // renders, so we expand the path into a real-area ribbon (a band of constant
        // width following the path) and draw that as a filled, stroked polygon.
        List<BlockPos> outline = buildRibbon(path, ROUTE_HALF_WIDTH);

        ShapeProperties props = new ShapeProperties()
                .setStrokeColor(ROUTE_COLOR)
                .setStrokeOpacity(1.0f)
                .setStrokeWidth(3.0f)
                .setFillColor(ROUTE_COLOR)
                .setFillOpacity(0.7f);

        PolygonOverlay overlay = new PolygonOverlay(
                MOD_ID, ROUTE_DISPLAY_ID, World.OVERWORLD, props, new MapPolygon(outline));
        overlay.setTitle("Ruta");
        overlay.setLabel("Ruta");
        overlay.setActiveUIs(EnumSet.of(Context.UI.Minimap, Context.UI.Fullscreen, Context.UI.Webmap));

        try {
            jmAPI.show(overlay);
            currentRoute = overlay;
            Teras.getLogger().info("Route drawn with " + path.size() + " waypoint(s)");
        } catch (Exception e) {
            Teras.getLogger().error("Error drawing route on map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Expands a path (list of {x, z}) into a closed, constant-width band polygon:
     * the left edge walked forward, then the right edge walked back. Each vertex is
     * offset along the perpendicular of its local direction (central difference), so
     * the band follows the path and keeps a real, triangulatable area.
     */
    private static List<BlockPos> buildRibbon(List<double[]> path, double halfWidth) {
        int n = path.size();
        if (n == 1) {
            // Degenerate: emit a small square so there is something to draw.
            double[] p = path.get(0);
            return new ArrayList<>(Arrays.asList(
                    new BlockPos((int) Math.round(p[0] - halfWidth), DRAW_Y, (int) Math.round(p[1] - halfWidth)),
                    new BlockPos((int) Math.round(p[0] + halfWidth), DRAW_Y, (int) Math.round(p[1] - halfWidth)),
                    new BlockPos((int) Math.round(p[0] + halfWidth), DRAW_Y, (int) Math.round(p[1] + halfWidth)),
                    new BlockPos((int) Math.round(p[0] - halfWidth), DRAW_Y, (int) Math.round(p[1] + halfWidth))));
        }

        List<BlockPos> left = new ArrayList<>();
        List<BlockPos> right = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double[] prev = path.get(Math.max(0, i - 1));
            double[] next = path.get(Math.min(n - 1, i + 1));
            double dx = next[0] - prev[0];
            double dz = next[1] - prev[1];
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-6) {
                dx = 1;
                dz = 0;
                len = 1;
            }
            // Perpendicular (left-hand normal) of the travel direction.
            double nx = -dz / len * halfWidth;
            double nz = dx / len * halfWidth;
            double[] p = path.get(i);
            left.add(new BlockPos((int) Math.round(p[0] + nx), DRAW_Y, (int) Math.round(p[1] + nz)));
            right.add(new BlockPos((int) Math.round(p[0] - nx), DRAW_Y, (int) Math.round(p[1] - nz)));
        }

        List<BlockPos> ring = new ArrayList<>(left);
        Collections.reverse(right);
        ring.addAll(right);
        return ring;
    }

    // ------------------------------------------------------------------

    public static class Point {
        private final int x;
        private final int z;

        public Point(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }

        @Override
        public String toString() {
            return "Point{x=" + x + ", z=" + z + '}';
        }
    }
}
