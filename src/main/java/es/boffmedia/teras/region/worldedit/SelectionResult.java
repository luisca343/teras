package es.boffmedia.teras.region.worldedit;

import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;

import java.util.List;

/**
 * A player's WorldEdit selection translated to Teras terms, so {@link WorldEditBridge} is the only
 * class that ever sees {@code com.sk89q} types. {@code minY}/{@code maxY} report WorldEdit's Y range
 * for polygon selections; whether to honor it is the caller's policy (region creation ignores it —
 * poly selections carry the Ys the admin happened to click, which is noise, not intent).
 */
public record SelectionResult(Status status, TerasRegion.Shape shape, List<RegionPoint> points,
                              Integer minY, Integer maxY,
                              TerasRegion.Corner min, TerasRegion.Corner max, String detail) {

    public enum Status { OK, INCOMPLETE, UNSUPPORTED }

    public static SelectionResult cuboid(TerasRegion.Corner min, TerasRegion.Corner max) {
        return new SelectionResult(Status.OK, TerasRegion.Shape.CUBOID, null, null, null, min, max, null);
    }

    public static SelectionResult polygon(List<RegionPoint> points, int minY, int maxY) {
        return new SelectionResult(Status.OK, TerasRegion.Shape.POLYGON, points, minY, maxY, null, null, null);
    }

    public static SelectionResult incomplete() {
        return new SelectionResult(Status.INCOMPLETE, null, null, null, null, null, null, null);
    }

    public static SelectionResult unsupported(String selectionType) {
        return new SelectionResult(Status.UNSUPPORTED, null, null, null, null, null, null, selectionType);
    }
}
