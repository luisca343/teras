package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.data.smartrotom.SmartRotomService;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.impl.ClientAPI;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Type;
import java.util.*;

public class PolygonCreator {
    private static final Map<String, PolygonOverlay> polygonMap = new HashMap<>();

    /**
     * Fetches the region list from SmartRotom and stores it in {@link Teras#regions}.
     *
     * <p>Tolerant of both response shapes seen across the API migration: a bare JSON
     * array, or an {@code ApiResponse} envelope ({@code {status,message,error,data}})
     * whose {@code data} holds the array (possibly as a nested JSON-encoded string).
     *
     * @return the loaded regions, or {@code null} if the request/parse failed.
     */
    public static List<Region> loadRegions() {
        try {
            String raw = SmartRotomService.getRegions();
            if (raw == null || raw.trim().isEmpty()) {
                Teras.getLogger().error("getRegions() returned an empty response");
                return null;
            }

            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(raw);

            // Unwrap the ApiResponse envelope if present.
            if (root.isJsonObject() && root.getAsJsonObject().has("data")) {
                root = root.getAsJsonObject().get("data");
                // data may itself be a JSON-encoded string rather than a nested array.
                if (root.isJsonPrimitive() && root.getAsJsonPrimitive().isString()) {
                    root = parser.parse(root.getAsString());
                }
            }

            if (!root.isJsonArray()) {
                Teras.getLogger().error("Region response was not a JSON array: " + raw);
                return null;
            }

            Type listType = new TypeToken<List<Region>>() {}.getType();
            List<Region> regions = new Gson().fromJson(root, listType);
            Teras.regions = regions;
            Teras.getLogger().info("Loaded " + (regions == null ? 0 : regions.size()) + " region(s)");
            return regions;
        } catch (Exception e) {
            Teras.getLogger().error("Failed to load regions", e);
            return null;
        }
    }

    public static void createPolygon() {
        IClientAPI jmAPI = ClientAPI.INSTANCE;

        if (jmAPI != null) {
            Teras.getLogger().info("jmAPI is not null");

            for (Region region : Teras.regions) {
                if(!region.getName().startsWith("pueblo_")) continue;
                String regionName = convertToTitleCase(region.getName());
                List<BlockPos> points = new ArrayList<>();

                for (Point point : region.getPoints()) {
                    points.add(new BlockPos(point.getX(), 64, point.getZ()));
                }

                if(points.size() < 3){
                    Teras.getLogger().info("Region " + regionName + " has less than 3 points, skipping");
                    continue;
                }

                ShapeProperties shapeProperties = new ShapeProperties();
                shapeProperties.setFillColor(region.getFillColor());
                shapeProperties.setStrokeColor(region.getStrokeColor());

                MapPolygon polygon = new MapPolygon(points);
                RegistryKey<World> dimension = World.OVERWORLD;
                PolygonOverlay overlay = new PolygonOverlay("journeymap", region.getName(), dimension, shapeProperties, polygon);

                //overlay.setTitle(regionName);
                //.setLabel(regionName);

                // We get the center of the polygon
                int x = 0;
                int z = 0;
                for (BlockPos point : points) {
                    x += point.getX();
                    z += point.getZ();
                }

                x /= points.size();
                z /= points.size();


                Waypoint waypoint = new Waypoint("journeymap", regionName, dimension, new BlockPos(x, 64, z));
                waypoint.setColor(region.getFillColor());




                overlay.setActiveUIs(EnumSet.of(Context.UI.Fullscreen));



                try {
                    jmAPI.show(overlay);
                    if(jmAPI.getWaypoint("journeymap", regionName) == null) {
                        jmAPI.show(waypoint);
                    }

                    polygonMap.put(region.getName(), overlay);
                } catch (Exception e) {
                    Teras.getLogger().error("Failed to show region overlay", e);
                }
            }
        }
    }

    public static void removePolygon(String name) {
        IClientAPI jmAPI = ClientAPI.INSTANCE;
        if (jmAPI != null) {
            PolygonOverlay overlay = polygonMap.remove(name);
            if (overlay != null) {
                try {
                    jmAPI.remove(overlay);
                } catch (Exception e) {
                    Teras.getLogger().error("Failed to remove region overlay", e);
                }
            }
        }
    }


    public static void removeAll(){
        IClientAPI jmAPI = ClientAPI.INSTANCE;
        if (jmAPI != null) {
            for (Map.Entry<String, PolygonOverlay> entry : polygonMap.entrySet()) {
                try {
                    jmAPI.remove(entry.getValue());
                } catch (Exception e) {
                    Teras.getLogger().error("Failed to remove region overlay", e);
                }
            }
            polygonMap.clear();
        }
    }


    public static class Region {
        private String name;
        private List<Point> points;
        private int fillColor;
        private int strokeColor;

        public String getName() {
            return name;
        }

        public List<Point> getPoints() {
            return points;
        }

        public int getFillColor() {
            return fillColor;
        }

        public int getStrokeColor() {
            return strokeColor;
        }
    }

    public static class Point {
        private int x;
        private int z;

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }
    }

    public static String convertToTitleCase(String snakeCase) {
        if (!snakeCase.contains("_") || "__global__".equals(snakeCase)) {
            return snakeCase;
        }

        String[] parts = snakeCase.split("_");
        StringBuilder titleCase = new StringBuilder();
        for (String part : parts) {
            titleCase.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase())
                    .append(" ");
        }
        return titleCase.toString().trim();
    }

}