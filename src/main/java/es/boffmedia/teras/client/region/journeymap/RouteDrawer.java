package es.boffmedia.teras.client.region.journeymap;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.region.ClientRegionStore;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.route.RoadRouter;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the GPS route on the player's JourneyMap, ported from the 1.16.5 {@code RouteCreator}
 * drawing half. The routing itself is in {@link RoadRouter}; this only turns its path into an
 * overlay.
 *
 * <p>The route is a single overlay, replaced on every redraw so successive recomputes don't stack.
 * It is drawn as a ribbon rather than a line because JourneyMap has no polyline overlay — only
 * closed polygons it triangulates to fill, so a zero-area "line" never renders.</p>
 */
public final class RouteDrawer {
    private RouteDrawer() {}

    /** Y level used for every drawn point (routing happens in 2D on the map). */
    private static final int DRAW_Y = 64;
    private static final int ROUTE_COLOR = 0x00B0FF;

    /** The last route we drew, kept so we can clear it before drawing a new one. */
    private static PolygonOverlay currentRoute;

    /** Removes the currently drawn route overlay, if any. */
    public static void clearRoute() {
        TerasJourneyMapPlugin plugin = TerasJourneyMapPlugin.instance();
        if (plugin != null && plugin.api() != null && currentRoute != null) {
            try {
                plugin.api().remove(currentRoute);
            } catch (Exception ignored) {
            }
        }
        currentRoute = null;
    }

    public static void createRoute(int startX, int startZ, int endX, int endZ) {
        TerasJourneyMapPlugin plugin = TerasJourneyMapPlugin.instance();
        if (plugin == null || plugin.api() == null) {
            Teras.LOGGER.warn("JourneyMap client API not available; cannot draw route");
            return;
        }
        List<double[]> path = RoadRouter.route(ClientRegionStore.all(),
                Level.OVERWORLD.location().toString(),
                new RegionPoint(startX, startZ), new RegionPoint(endX, endZ));
        drawRoute(plugin.api(), path);
    }

    private static void drawRoute(IClientAPI jmAPI, List<double[]> path) {
        // Clear the previous route so successive calls don't stack overlays.
        if (currentRoute != null) {
            try {
                jmAPI.remove(currentRoute);
            } catch (Exception ignored) {
            }
            currentRoute = null;
        }

        // Split long straight segments into intermediate points so the route has dense vertices
        // along its length (smoother corners, better minimap coverage).
        List<double[]> dense = RoadRouter.subdivide(path, RoadRouter.ROUTE_POINT_SPACING);
        List<RegionPoint> ribbon = RoadRouter.buildRibbon(dense, RoadRouter.ROUTE_HALF_WIDTH);
        if (ribbon.isEmpty()) return;

        List<BlockPos> outline = new ArrayList<>(ribbon.size());
        for (RegionPoint point : ribbon) {
            outline.add(new BlockPos(point.getX(), DRAW_Y, point.getZ()));
        }

        ShapeProperties props = new ShapeProperties()
                .setStrokeColor(ROUTE_COLOR)
                .setStrokeOpacity(1.0f)
                .setStrokeWidth(3.0f)
                .setFillColor(ROUTE_COLOR)
                .setFillOpacity(0.7f);

        PolygonOverlay overlay = new PolygonOverlay(
                Teras.MOD_ID, Level.OVERWORLD, props, new MapPolygon(outline));
        overlay.setTitle("Ruta");
        overlay.setLabel("Ruta");
        overlay.setActiveUIs(Context.UI.Minimap, Context.UI.Fullscreen, Context.UI.Webmap);

        try {
            jmAPI.show(overlay);
            currentRoute = overlay;
            Teras.LOGGER.debug("Route drawn with {} waypoint(s)", path.size());
        } catch (Exception e) {
            Teras.LOGGER.error("Error drawing route on map", e);
        }
    }
}
