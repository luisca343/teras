package es.boffmedia.teras.util;

import es.boffmedia.teras.Teras;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.impl.ClientAPI;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class RouteCreator {
    private static final Map<String, PolygonCreator.Region> roadMap = new HashMap<>();

    public static void initializeRoadMap() {
        Teras.getLogger().info("Initializing road map");
        if (Teras.regions == null) {
            Teras.getLogger().error("Teras.regions is null");
            return;
        }
        for (PolygonCreator.Region region : Teras.regions) {
            if (region.getName().startsWith("carretera_")) {
                roadMap.put(region.getName(), region);
                Teras.getLogger().info("Added region to road map: " + region.getName());
            }
        }
    }

    public static void createRoute(Point start, Point end) {
        Teras.getLogger().info("Creating route from " + start + " to " + end);
        initializeRoadMap();
        IClientAPI jmAPI = ClientAPI.INSTANCE;

        if (jmAPI != null) {
            // Find the closest road to the starting point
            BlockPos closestStartPoint = findClosestRoadPoint(start);
            BlockPos closestEndPoint = findClosestRoadPoint(end);

            // Draw blue line from start to closest road point
            List<BlockPos> blueLine = createLine(start, closestStartPoint);
            drawLine(jmAPI, blueLine, 0x0000FF, "Blue Line");

            // Find the shortest path along the roads
            /*
            List<BlockPos> redLine = findShortestPath(closestStartPoint, closestEndPoint);
            drawLine(jmAPI, redLine, 0xFF0000, "Red Line");*/

            // Draw red line from closest road point to end
            List<BlockPos> finalRedLine = createLine(end, closestEndPoint);
            drawLine(jmAPI, finalRedLine, 0xFF0000, "Final Red Line");
        } else {
            Teras.getLogger().warn("jmAPI is null");
        }
    }

    private static BlockPos findClosestRoadPoint(Point point) {
        BlockPos closestPoint = null;
        double minDistance = Double.MAX_VALUE;

        for (PolygonCreator.Region region : roadMap.values()) {
            for (PolygonCreator.Point roadPoint : region.getPoints()) {
                BlockPos roadBlockPos = new BlockPos(roadPoint.getX(), 64, roadPoint.getZ());
                double distance = point.distanceTo(roadBlockPos);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPoint = roadBlockPos;
                }
            }
        }

        return closestPoint;
    }

    private static List<BlockPos> createLine(Point start, BlockPos end) {
        List<BlockPos> line = new ArrayList<>();
        line.add(new BlockPos(start.getX(), 64, start.getZ()));
        line.add(end);
        return createArea(line);
    }

    private static List<BlockPos> createArea(List<BlockPos> line) {
        List<BlockPos> area = new ArrayList<>(line);
        Collections.reverse(line);
        area.addAll(line);
        return area;
    }

    private static void drawLine(IClientAPI jmAPI, List<BlockPos> line, int color, String title) {
        ShapeProperties shapeProperties = new ShapeProperties();
        shapeProperties.setStrokeColor(color);

        MapPolygon polygon = new MapPolygon(line);
        RegistryKey<World> dimension = World.OVERWORLD;
        PolygonOverlay overlay = new PolygonOverlay("journeymap", title, dimension, shapeProperties, polygon);
        overlay.setTitle(title);
        overlay.setLabel(title);

        try {
            jmAPI.show(overlay);
            Teras.getLogger().info(title + " displayed on map");
        } catch (Exception e) {
            Teras.getLogger().error("Error displaying " + title + " on map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<BlockPos> findShortestPath(BlockPos start, BlockPos end) {
        Teras.getLogger().info("Finding shortest path from " + start + " to " + end);
        Map<BlockPos, BlockPos> predecessors = new HashMap<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        PriorityQueue<BlockPos> queue = new PriorityQueue<>(Comparator.comparingInt(distances::get));

        distances.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            Teras.getLogger().info("Processing node: " + current);

            if (current.equals(end)) {
                Teras.getLogger().info("End node reached");
                return reconstructPath(predecessors, end);
            }

            for (BlockPos neighbor : getNeighbors(current)) {
                int newDist = distances.get(current) + getDistance(current, neighbor);
                if (newDist < distances.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    distances.put(neighbor, newDist);
                    predecessors.put(neighbor, current);
                    queue.add(neighbor);
                    Teras.getLogger().info("Updated distance for neighbor: " + neighbor + " to " + newDist);
                }
            }
        }

        Teras.getLogger().warn("No path found");
        return Collections.emptyList();
    }

    private static List<BlockPos> getNeighbors(BlockPos pos) {
        Teras.getLogger().info("Getting neighbors for position: " + pos);
        List<BlockPos> neighbors = new ArrayList<>();
        for (PolygonCreator.Region region : roadMap.values()) {
            for (PolygonCreator.Point point : region.getPoints()) {
                BlockPos neighbor = new BlockPos(point.getX(), 64, point.getZ());
                if (!neighbor.equals(pos)) {
                    neighbors.add(neighbor);
                    Teras.getLogger().info("Added neighbor: " + neighbor);
                }
            }
        }
        return neighbors;
    }

    private static int getDistance(BlockPos a, BlockPos b) {
        int distance = Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
        Teras.getLogger().info("Calculated distance between " + a + " and " + b + ": " + distance);
        return distance;
    }

    private static List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> predecessors, BlockPos end) {
        Teras.getLogger().info("Reconstructing path to end node: " + end);
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos at = end; at != null; at = predecessors.get(at)) {
            path.add(at);
            Teras.getLogger().info("Added to path: " + at);
        }
        Collections.reverse(path);
        Teras.getLogger().info("Path reconstruction complete");
        return path;
    }

    public static class Point {
        private int x;
        private int z;

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

        public double distanceTo(BlockPos pos) {
            return Math.sqrt(Math.pow(x - pos.getX(), 2) + Math.pow(z - pos.getZ(), 2));
        }

        @Override
        public String toString() {
            return "Point{" +
                    "x=" + x +
                    ", z=" + z +
                    '}';
        }
    }
}