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
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws {@code pueblo_*} regions on JourneyMap — port of the 1.16.5 {@code PolygonCreator}: filled
 * polygon on the fullscreen map plus a centroid waypoint named after the town. Redrawn from scratch
 * on every {@link ClientRegionStore} change (regions are few and mutations admin-rare).
 *
 * <p>Unlike the old code, which hardcoded the overworld, each overlay carries its region's own
 * dimension key, so JourneyMap files it under the right map with no per-dimension refresh.</p>
 */
final class RegionMapDrawer {
    private RegionMapDrawer() {}

    /** Same display Y the 1.16.5 overlays used; JourneyMap only reads XZ for map shapes. */
    private static final int OVERLAY_Y = 64;

    /**
     * Overlays this drawer showed, removed one by one on refresh — not
     * {@code removeAll(modId, Polygon)}, which would also wipe {@link RouteDrawer}'s route lines.
     */
    private static final List<PolygonOverlay> SHOWN = new ArrayList<>();

    static void refresh() {
        TerasJourneyMapPlugin plugin = TerasJourneyMapPlugin.instance();
        if (plugin == null || plugin.api() == null) return;
        IClientAPI api = plugin.api();

        for (PolygonOverlay overlay : SHOWN) {
            api.remove(overlay);
        }
        SHOWN.clear();
        for (TerasRegion region : ClientRegionStore.all()) {
            if (!region.isTown()) continue;
            List<RegionPoint> outline = region.outline();
            if (outline == null || outline.size() < 3) {
                Teras.LOGGER.warn("Region '{}' has fewer than 3 outline points; not drawing",
                        region.getName());
                continue;
            }
            try {
                show(api, region, outline);
            } catch (Exception e) {
                Teras.LOGGER.warn("Failed to draw region '{}' on JourneyMap: {}",
                        region.getName(), e.toString());
            }
        }
    }

    private static void show(IClientAPI api, TerasRegion region, List<RegionPoint> outline)
            throws Exception {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(region.getDimension()));

        ShapeProperties shape = new ShapeProperties()
                .setFillColor(region.getFillColor())
                .setStrokeColor(region.getStrokeColor());
        PolygonOverlay overlay = new PolygonOverlay(Teras.MOD_ID, dimension, shape,
                new MapPolygon(counterClockwise(outline)));
        overlay.setActiveUIs(Context.UI.Fullscreen);
        api.show(overlay);
        SHOWN.add(overlay);

        // Centroid waypoint labeled with the town's display name, deduped by name so resyncs
        // (and towns spanning several syncs) don't stack copies — the 1.16.5 getWaypoint check.
        RegionPoint center = region.centroid();
        String display = TerasRegion.titleCase(region.getName());
        boolean exists = api.getWaypoints(Teras.MOD_ID).stream()
                .anyMatch(wp -> display.equals(wp.getName()));
        if (!exists) {
            Waypoint waypoint = WaypointFactory.createClientWaypoint(Teras.MOD_ID,
                    new BlockPos(center.getX(), OVERLAY_Y, center.getZ()), display, dimension, false);
            waypoint.setColor(region.getFillColor());
            api.addWaypoint(Teras.MOD_ID, waypoint);
        }
    }

    /**
     * Outline with a consistent winding (positive shoelace area over XZ), so fills render the same
     * way regardless of the direction the admin clicked the poly points in.
     */
    private static List<BlockPos> counterClockwise(List<RegionPoint> outline) {
        long doubledArea = 0;
        for (int i = 0, j = outline.size() - 1; i < outline.size(); j = i++) {
            doubledArea += (long) (outline.get(j).getX() - outline.get(i).getX())
                    * (outline.get(j).getZ() + outline.get(i).getZ());
        }
        List<BlockPos> points = new ArrayList<>(outline.size());
        for (RegionPoint point : outline) {
            points.add(new BlockPos(point.getX(), OVERLAY_Y, point.getZ()));
        }
        if (doubledArea < 0) {
            java.util.Collections.reverse(points);
        }
        return points;
    }
}
