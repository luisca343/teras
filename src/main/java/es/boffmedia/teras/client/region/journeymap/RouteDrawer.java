package es.boffmedia.teras.client.region.journeymap;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.region.ClientRegionStore;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Road navigation on the fullscreen map — minimal port of the 1.16.5 {@code RouteCreator}: a blue
 * connector from the start to its closest {@code carretera_*} vertex and a red one from the end to
 * its own, each drawn as the old degenerate there-and-back polygon. The old Dijkstra along the road
 * graph was dead code (commented out) and stays unported.
 *
 * <p>Only named behind a {@code ModList.isLoaded("journeymap")} guard
 * ({@link es.boffmedia.teras.client.ClientNetHandler}); road data comes from
 * {@link ClientRegionStore}, so no server round-trip is needed.</p>
 */
public final class RouteDrawer {
    private RouteDrawer() {}

    private static final int OVERLAY_Y = 64;
    private static final int START_CONNECTOR_COLOR = 0x0000FF;
    private static final int END_CONNECTOR_COLOR = 0xFF0000;

    /** The current route's overlays; a new route (or a redraw with no roads) replaces them. */
    private static final List<PolygonOverlay> SHOWN = new ArrayList<>();

    public static void draw(int startX, int startZ, int endX, int endZ) {
        TerasJourneyMapPlugin plugin = TerasJourneyMapPlugin.instance();
        if (plugin == null || plugin.api() == null) {
            Teras.LOGGER.warn("Route requested but the JourneyMap plugin is not initialized");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        IClientAPI api = plugin.api();
        ResourceKey<Level> dimension = mc.level.dimension();
        String dimensionKey = dimension.location().toString();

        for (PolygonOverlay overlay : SHOWN) {
            api.remove(overlay);
        }
        SHOWN.clear();

        List<RegionPoint> roadPoints = roadPoints(dimensionKey);
        if (roadPoints.isEmpty()) {
            Teras.LOGGER.warn("Route requested but there are no carretera_* regions in {}", dimensionKey);
            return;
        }
        RegionPoint closestToStart = closest(roadPoints, startX, startZ);
        RegionPoint closestToEnd = closest(roadPoints, endX, endZ);

        showLine(api, dimension, startX, startZ, closestToStart, START_CONNECTOR_COLOR);
        showLine(api, dimension, endX, endZ, closestToEnd, END_CONNECTOR_COLOR);
    }

    private static List<RegionPoint> roadPoints(String dimensionKey) {
        List<RegionPoint> points = new ArrayList<>();
        for (TerasRegion region : ClientRegionStore.all()) {
            if (region.isRoad() && dimensionKey.equals(region.getDimension())) {
                points.addAll(region.outline());
            }
        }
        return points;
    }

    private static RegionPoint closest(List<RegionPoint> points, int x, int z) {
        RegionPoint best = points.get(0);
        long bestDistance = Long.MAX_VALUE;
        for (RegionPoint point : points) {
            long dx = point.getX() - x;
            long dz = point.getZ() - z;
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        return best;
    }

    /** The old {@code createLine}/{@code createArea} trick: a two-point polygon traced both ways. */
    private static void showLine(IClientAPI api, ResourceKey<Level> dimension,
                                 int fromX, int fromZ, RegionPoint to, int color) {
        List<BlockPos> line = new ArrayList<>(4);
        line.add(new BlockPos(fromX, OVERLAY_Y, fromZ));
        line.add(new BlockPos(to.getX(), OVERLAY_Y, to.getZ()));
        line.add(new BlockPos(to.getX(), OVERLAY_Y, to.getZ()));
        line.add(new BlockPos(fromX, OVERLAY_Y, fromZ));

        ShapeProperties shape = new ShapeProperties().setStrokeColor(color);
        PolygonOverlay overlay = new PolygonOverlay(Teras.MOD_ID, dimension, shape, new MapPolygon(line));
        overlay.setActiveUIs(Context.UI.Fullscreen);
        try {
            api.show(overlay);
            SHOWN.add(overlay);
        } catch (Exception e) {
            Teras.LOGGER.warn("Failed to draw route connector: {}", e.toString());
        }
    }
}
