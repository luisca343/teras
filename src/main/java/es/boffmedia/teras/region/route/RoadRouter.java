package es.boffmedia.teras.region.route;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Road routing for the GPS, ported from the 1.16.5 {@code RouteCreator}.
 *
 * <p>Each {@code carretera_*} region is treated as an axis-aligned footprint in the XZ plane. Two
 * roads are considered connected when their footprints overlap (or are within
 * {@link #CONNECT_TOLERANCE} blocks of each other), and the centre of that overlap becomes a "turn
 * point". Dijkstra then runs over a graph of turn points plus road entry points to find the
 * shortest chain of roads from start to end.</p>
 *
 * <p>Kept free of Minecraft and JourneyMap types so it runs anywhere and is unit tested; the
 * drawing half lives in the client {@code RouteDrawer}.</p>
 */
public final class RoadRouter {
    private RoadRouter() {}

    /** Roads whose footprints are within this many blocks are treated as connected. */
    private static final int CONNECT_TOLERANCE = 2;
    /** Long segments are split into points at most this many blocks apart. */
    public static final double ROUTE_POINT_SPACING = 16.0;
    /** Half-width, in blocks, of the drawn route band. */
    public static final double ROUTE_HALF_WIDTH = 4.0;

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

    /**
     * The route from {@code start} to {@code end} as a list of {@code {x, z}} waypoints. Falls back
     * to a direct line when no road route exists, so the GPS always shows something.
     */
    public static List<double[]> route(Collection<TerasRegion> regions, String dimension,
                                       RegionPoint start, RegionPoint end) {
        List<Road> roads = buildRoads(regions, dimension);
        List<double[]> path = computePath(roads, start, end);
        if (path.isEmpty()) {
            Teras.LOGGER.warn("No road route found between the points; drawing a direct line as fallback");
            path = Arrays.asList(
                    new double[]{start.getX(), start.getZ()},
                    new double[]{end.getX(), end.getZ()});
        }
        return path;
    }

    private static List<Road> buildRoads(Collection<TerasRegion> regions, String dimension) {
        List<Road> roads = new ArrayList<>();
        for (TerasRegion region : regions) {
            if (!region.isRoad() || !dimension.equals(region.getDimension())) continue;
            List<RegionPoint> points = region.outline();
            if (points == null || points.isEmpty()) {
                Teras.LOGGER.warn("Road region '{}' has no points; skipping", region.getName());
                continue;
            }
            double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (RegionPoint p : points) {
                minX = Math.min(minX, p.getX());
                maxX = Math.max(maxX, p.getX());
                minZ = Math.min(minZ, p.getZ());
                maxZ = Math.max(maxZ, p.getZ());
            }
            roads.add(new Road(minX, minZ, maxX, maxZ));
        }
        return roads;
    }

    private static List<double[]> computePath(List<Road> roads, RegionPoint start, RegionPoint end) {
        if (roads.isEmpty()) return Collections.emptyList();

        List<double[]> nodePos = new ArrayList<>();      // node index -> {x, z}
        List<List<double[]>> adj = new ArrayList<>();    // node index -> list of {neighborIndex, weight}

        int startNode = addNode(nodePos, adj, start.getX(), start.getZ());
        int endNode = addNode(nodePos, adj, end.getX(), end.getZ());

        // For each road, the junction nodes (overlap centres) that sit on it. We route through
        // these turn points rather than road centres, so the line hugs the actual intersections
        // instead of detouring to the middle of long roads.
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

        // Snap start/end to the road that contains them, else the nearest road.
        int startRoad = snapToRoad(roads, start.getX(), start.getZ());
        int endRoad = snapToRoad(roads, end.getX(), end.getZ());
        if (startRoad < 0 || endRoad < 0) return Collections.emptyList();

        // Enter/leave each road at its nearest point (a short perpendicular hop), then travel along
        // the road to its junctions. This keeps off-road approaches natural instead of shooting a
        // long diagonal straight at a distant junction.
        int startEntry = addEntryNode(nodePos, adj, roads.get(startRoad), start.getX(), start.getZ());
        int endEntry = addEntryNode(nodePos, adj, roads.get(endRoad), end.getX(), end.getZ());
        link(nodePos, adj, startNode, startEntry);
        link(nodePos, adj, endNode, endEntry);
        for (int turn : roadJunctions.get(startRoad)) link(nodePos, adj, startEntry, turn);
        for (int turn : roadJunctions.get(endRoad)) link(nodePos, adj, endEntry, turn);
        // Same road: a direct hop along it, no junction needed.
        if (startRoad == endRoad) link(nodePos, adj, startEntry, endEntry);

        return dijkstra(nodePos, adj, startNode, endNode);
    }

    private static int addNode(List<double[]> pos, List<List<double[]>> adj, double x, double z) {
        pos.add(new double[]{x, z});
        adj.add(new ArrayList<>());
        return pos.size() - 1;
    }

    /** Adds a node at the point on {@code road} nearest to (x, z) — the road entry point. */
    private static int addEntryNode(List<double[]> pos, List<List<double[]>> adj, Road road,
                                    double x, double z) {
        double ex = Math.max(road.minX, Math.min(x, road.maxX));
        double ez = Math.max(road.minZ, Math.min(z, road.maxZ));
        return addNode(pos, adj, ex, ez);
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

    private static List<double[]> dijkstra(List<double[]> pos, List<List<double[]>> adj,
                                           int src, int dst) {
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

    /**
     * Inserts intermediate points so no two consecutive points are more than {@code maxSpacing}
     * blocks apart. Straight segments stay straight; they just gain vertices along the way.
     */
    public static List<double[]> subdivide(List<double[]> path, double maxSpacing) {
        if (path.size() < 2) return path;

        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            double[] a = path.get(i);
            double[] b = path.get(i + 1);
            out.add(a);

            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double len = Math.sqrt(dx * dx + dz * dz);
            int steps = (int) (len / maxSpacing);
            for (int s = 1; s <= steps; s++) {
                double t = (s * maxSpacing) / len;
                if (t >= 1.0) break;
                out.add(new double[]{a[0] + dx * t, a[1] + dz * t});
            }
        }
        out.add(path.get(path.size() - 1));
        return out;
    }

    /**
     * Expands a path (list of {@code {x, z}}) into a closed, constant-width band polygon: the left
     * edge walked forward, then the right edge walked back. Each vertex is offset along the
     * perpendicular of its local direction (central difference), so the band follows the path and
     * keeps a real, triangulatable area — JourneyMap has no polyline overlay, only polygons it
     * triangulates to fill, and a zero-area "line" never renders.
     */
    public static List<RegionPoint> buildRibbon(List<double[]> path, double halfWidth) {
        int n = path.size();
        if (n == 0) return List.of();
        if (n == 1) {
            // Degenerate: emit a small square so there is something to draw.
            double[] p = path.get(0);
            return List.of(
                    new RegionPoint((int) Math.round(p[0] - halfWidth), (int) Math.round(p[1] - halfWidth)),
                    new RegionPoint((int) Math.round(p[0] + halfWidth), (int) Math.round(p[1] - halfWidth)),
                    new RegionPoint((int) Math.round(p[0] + halfWidth), (int) Math.round(p[1] + halfWidth)),
                    new RegionPoint((int) Math.round(p[0] - halfWidth), (int) Math.round(p[1] + halfWidth)));
        }

        List<RegionPoint> left = new ArrayList<>();
        List<RegionPoint> right = new ArrayList<>();
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
            left.add(new RegionPoint((int) Math.round(p[0] + nx), (int) Math.round(p[1] + nz)));
            right.add(new RegionPoint((int) Math.round(p[0] - nx), (int) Math.round(p[1] - nz)));
        }

        List<RegionPoint> ring = new ArrayList<>(left);
        Collections.reverse(right);
        ring.addAll(right);
        return ring;
    }
}
