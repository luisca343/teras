package es.boffmedia.teras.region.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A named world region, defined either as a block-aligned cuboid or as a 2D XZ polygon with an
 * optional Y range. Replaces the 1.16.5 backend-served {@code PolygonCreator.Region}; the polygon
 * fields ({@code name}, {@code points}, {@code fillColor}, {@code strokeColor}) keep that shape so
 * the SmartRotom web can still consume the serialized form.
 *
 * <p>Semantics ride on the name prefix, as they always did: {@code pueblo_*} regions are towns
 * (map overlay + enter banner), {@code carretera_*} regions are roads (routing). The name doubles
 * as the cartel texture id and the web key, hence the {@code [a-z0-9_]+} restriction.</p>
 *
 * <p>Pure Java on purpose — no Minecraft imports — so it loads on both sides and in unit tests.</p>
 */
public final class TerasRegion {

    public enum Shape { CUBOID, POLYGON }

    public static final String NAME_PATTERN = "[a-z0-9_]+";
    public static final String TOWN_PREFIX = "pueblo_";
    public static final String ROAD_PREFIX = "carretera_";
    /** Explicit banner value meaning "never show a banner for this region". */
    public static final String BANNER_NONE = "none";

    /** A cuboid corner. Distinct from {@link RegionPoint} because corners carry Y. */
    public static final class Corner {
        private final int x;
        private final int y;
        private final int z;

        public Corner(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
    }

    private String name;
    private String dimension;
    private Shape shape;

    // POLYGON variant
    private List<RegionPoint> points;
    private Integer minY;
    private Integer maxY;

    // CUBOID variant
    private Corner min;
    private Corner max;

    /**
     * WorldGuard's shadowing rank. Among the regions containing a position, only those at the
     * highest priority get a say; lower ones are ignored entirely rather than merged. Defaults to
     * 0, which is also what Gson gives a region file written before this field existed — so every
     * region that shipped resolves exactly as it did before.
     */
    private int priority;

    /**
     * The standing offer, if any. Geometry and price are admin-authored and reviewable, so they
     * live here in {@code regions.json}; who actually owns the plot and what was paid live in
     * SQLite, where a purchase can be one transaction. Whether a region is a <em>plot</em> is
     * decided by the ownership row, not by this flag — clearing it withdraws the offer without
     * evicting the current owner.
     */
    private boolean purchasable;
    private long price;

    private int fillColor;
    private int strokeColor;
    private Map<String, Boolean> flags;
    private String banner;
    private String createdBy;
    private long createdAt;

    // Lazy XZ bounding box for polygon containment pre-check; never serialized.
    private transient boolean bboxComputed;
    private transient int bboxMinX, bboxMaxX, bboxMinZ, bboxMaxZ;

    /** Gson constructor. */
    private TerasRegion() {}

    public static TerasRegion cuboid(String name, String dimension, Corner a, Corner b) {
        TerasRegion region = new TerasRegion();
        region.name = name;
        region.dimension = dimension;
        region.shape = Shape.CUBOID;
        region.min = new Corner(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
        region.max = new Corner(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
        return region;
    }

    public static TerasRegion polygon(String name, String dimension, List<RegionPoint> points,
                                      Integer minY, Integer maxY) {
        TerasRegion region = new TerasRegion();
        region.name = name;
        region.dimension = dimension;
        region.shape = Shape.POLYGON;
        region.points = new ArrayList<>(points);
        if (minY != null && maxY != null && minY > maxY) {
            region.minY = maxY;
            region.maxY = minY;
        } else {
            region.minY = minY;
            region.maxY = maxY;
        }
        return region;
    }

    /** Whether the (entity) position is inside the region. Block-inclusive on every axis. */
    public boolean contains(double x, double y, double z) {
        return switch (shape) {
            case CUBOID -> x >= min.x && x < max.x + 1
                    && y >= min.y && y < max.y + 1
                    && z >= min.z && z < max.z + 1;
            case POLYGON -> withinYRange(y) && withinBoundingBox(x, z) && insidePolygon(x, z);
        };
    }

    private boolean withinYRange(double y) {
        if (minY != null && y < minY) return false;
        return maxY == null || y < maxY + 1;
    }

    private boolean withinBoundingBox(double x, double z) {
        if (!bboxComputed) {
            bboxMinX = Integer.MAX_VALUE;
            bboxMaxX = Integer.MIN_VALUE;
            bboxMinZ = Integer.MAX_VALUE;
            bboxMaxZ = Integer.MIN_VALUE;
            for (RegionPoint p : points) {
                bboxMinX = Math.min(bboxMinX, p.getX());
                bboxMaxX = Math.max(bboxMaxX, p.getX());
                bboxMinZ = Math.min(bboxMinZ, p.getZ());
                bboxMaxZ = Math.max(bboxMaxZ, p.getZ());
            }
            bboxComputed = true;
        }
        return x >= bboxMinX && x <= bboxMaxX + 1 && z >= bboxMinZ && z <= bboxMaxZ + 1;
    }

    /** Standard even-odd ray cast over the XZ vertex list. */
    private boolean insidePolygon(double x, double z) {
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            double xi = points.get(i).getX(), zi = points.get(i).getZ();
            double xj = points.get(j).getX(), zj = points.get(j).getZ();
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Whether {@code flag} is explicitly denied here (absent or {@code true} = allowed). */
    public boolean deniesFlag(RegionFlag flag) {
        return flags != null && Boolean.FALSE.equals(flags.get(flag.key()));
    }

    public boolean isTown() {
        return name.startsWith(TOWN_PREFIX);
    }

    public boolean isRoad() {
        return name.startsWith(ROAD_PREFIX);
    }

    /**
     * The cartel texture id to flash on enter, or {@code null} for no banner. An explicit
     * {@code banner} wins ({@link #BANNER_NONE} disables); by default towns use their own name
     * (matching {@code textures/carteles/<name>.png}) and everything else stays silent.
     */
    public String bannerOrNull() {
        if (banner != null) {
            return BANNER_NONE.equals(banner) ? null : banner;
        }
        return isTown() ? name : null;
    }

    /**
     * The region's outline as XZ points: the polygon's vertices, or the cuboid's four corners.
     * This is what the web map and JourneyMap draw.
     */
    public List<RegionPoint> outline() {
        if (shape == Shape.POLYGON) return points;
        return List.of(
                new RegionPoint(min.x, min.z),
                new RegionPoint(max.x, min.z),
                new RegionPoint(max.x, max.z),
                new RegionPoint(min.x, max.z));
    }

    /**
     * Why this region is unusable, or {@code null} if it is valid. Loading is lenient: the store
     * logs and skips invalid entries instead of aborting the whole file.
     */
    public String validationError() {
        if (name == null || !name.matches(NAME_PATTERN)) {
            return "invalid name (must be " + NAME_PATTERN + "): " + name;
        }
        if (dimension == null || dimension.isEmpty()) return "missing dimension";
        if (shape == null) return "missing shape";
        if (shape == Shape.POLYGON) {
            if (points == null || points.size() < 3) return "polygon needs at least 3 points";
            if (points.stream().anyMatch(java.util.Objects::isNull)) return "polygon has null points";
        } else {
            if (min == null || max == null) return "cuboid needs min and max corners";
        }
        return null;
    }

    /** Fixes orderable fields in place after a load or hand-edit (corner order, Y order). */
    public void normalize() {
        if (shape == Shape.CUBOID && min != null && max != null) {
            Corner a = min, b = max;
            min = new Corner(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
            max = new Corner(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
        }
        if (minY != null && maxY != null && minY > maxY) {
            Integer tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
        bboxComputed = false;
    }

    public String getName() { return name; }
    public String getDimension() { return dimension; }
    public Shape getShape() { return shape; }
    public List<RegionPoint> getPoints() { return points; }
    public Integer getMinY() { return minY; }
    public Integer getMaxY() { return maxY; }
    public Corner getMin() { return min; }
    public Corner getMax() { return max; }
    public int getPriority() { return priority; }
    public boolean isPurchasable() { return purchasable; }
    public long getPrice() { return price; }
    public int getFillColor() { return fillColor; }
    public int getStrokeColor() { return strokeColor; }
    public Map<String, Boolean> getFlags() { return flags; }
    public String getBanner() { return banner; }
    public String getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }

    public void setPriority(int priority) { this.priority = priority; }
    public void setPurchasable(boolean purchasable) { this.purchasable = purchasable; }
    public void setPrice(long price) { this.price = price; }
    public void setFillColor(int fillColor) { this.fillColor = fillColor; }
    public void setStrokeColor(int strokeColor) { this.strokeColor = strokeColor; }
    public void setBanner(String banner) { this.banner = banner; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    /**
     * Replaces the whole flag map. Exists for the storage layer, which round-trips flags as a JSON
     * blob: going through {@link #setFlag} would silently drop any key this version does not know
     * as a {@link RegionFlag}, turning an unrecognised flag into data loss on the next write.
     */
    public void setFlags(Map<String, Boolean> flags) {
        this.flags = flags == null || flags.isEmpty() ? null : new java.util.LinkedHashMap<>(flags);
    }

    public void setFlag(RegionFlag flag, Boolean value) {
        if (value == null) {
            if (flags != null) flags.remove(flag.key());
            return;
        }
        if (flags == null) flags = new java.util.LinkedHashMap<>();
        flags.put(flag.key(), value);
    }

    /** The outline's XZ centroid — where the town's waypoint sits and where {@code /gps} aims. */
    public RegionPoint centroid() {
        List<RegionPoint> outline = outline();
        int x = 0;
        int z = 0;
        for (RegionPoint point : outline) {
            x += point.getX();
            z += point.getZ();
        }
        return new RegionPoint(x / outline.size(), z / outline.size());
    }

    /**
     * {@code pueblo_tulipan} → {@code "Pueblo Tulipan"} — the display name for waypoints and the
     * title fallback. Verbatim port of the 1.16.5 {@code PolygonCreator.convertToTitleCase},
     * including the {@code __global__} special case.
     */
    public static String titleCase(String snakeCase) {
        if (!snakeCase.contains("_") || "__global__".equals(snakeCase)) {
            return snakeCase;
        }
        String[] parts = snakeCase.split("_");
        StringBuilder titleCase = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            titleCase.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase())
                    .append(" ");
        }
        return titleCase.toString().trim();
    }

    /** Copies the cosmetic/behavior fields (colors, flags, banner, creator) from {@code other}. */
    public void inheritSettingsFrom(TerasRegion other) {
        priority = other.priority;
        purchasable = other.purchasable;
        price = other.price;
        fillColor = other.fillColor;
        strokeColor = other.strokeColor;
        flags = other.flags;
        banner = other.banner;
        createdBy = other.createdBy;
        createdAt = other.createdAt;
    }
}
