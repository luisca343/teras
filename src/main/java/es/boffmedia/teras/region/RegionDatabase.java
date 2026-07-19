package es.boffmedia.teras.region;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.plot.sql.TerasDatabase;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Region geometry in the database: the query surface over {@link TerasDatabase}'s {@code region}
 * table. Connections, dialect and schema belong to {@link TerasDatabase}; this only issues
 * statements and maps rows.
 *
 * <p><b>Shape of the row.</b> The fields the SmartRotom web wants to filter on — name, dimension,
 * priority, price — are real columns. Geometry and flags are single json documents, because point
 * order is significant (polygon winding) and a child table makes that ordering an invariant a
 * stray {@code ORDER BY} can silently break. The json uses the same field names Gson already
 * produces for {@code GET /regions} and the client sync payload, so the web parses one shape
 * whichever way it reads a region.</p>
 */
public final class RegionDatabase {

    private static final Gson GSON = new Gson();
    private static final Type FLAGS_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();
    private static final Type POINTS_TYPE = new TypeToken<List<RegionPoint>>() {}.getType();

    private final TerasDatabase database;

    public RegionDatabase(TerasDatabase database) {
        this.database = database;
    }

    private String table() {
        return database.table("region");
    }

    /** Every region, keyed by name — the whole catalog, for the in-memory snapshot. */
    public synchronized Map<String, TerasRegion> loadAll() throws SQLException {
        Map<String, TerasRegion> regions = new LinkedHashMap<>();
        try (Connection connection = database.connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT name, dimension, shape, priority, "
                     + "purchasable, price, fill_color, stroke_color, banner, geometry, flags, "
                     + "created_by, created_at FROM " + table())) {
            while (rows.next()) {
                TerasRegion region = read(rows);
                if (region == null) continue;
                regions.put(region.getName(), region);
            }
        }
        return regions;
    }

    /** Adds or replaces one region. */
    public synchronized void put(TerasRegion region) throws SQLException {
        try (Connection connection = database.connect()) {
            write(connection, region, System.currentTimeMillis());
        }
    }

    /**
     * Replaces the whole catalog in one transaction. Used by the seed import, where a half-written
     * region table would be worse than none — the mod would enforce protection from a catalog that
     * is missing regions nobody realises are gone.
     */
    public synchronized void replaceAll(Collection<TerasRegion> regions) throws SQLException {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM " + table());
                }
                long now = System.currentTimeMillis();
                for (TerasRegion region : regions) {
                    write(connection, region, now);
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Removes a region, returning whether it existed. Fails when a plot still references it — the
     * foreign key is {@code RESTRICT}, so a region cannot be deleted out from under an owner.
     */
    public synchronized boolean remove(String name) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + table() + " WHERE name = ?")) {
            statement.setString(1, name);
            return statement.executeUpdate() > 0;
        }
    }

    /** Whether the catalog is empty — i.e. whether the seed import still has to run. */
    public synchronized boolean isEmpty() throws SQLException {
        try (Connection connection = database.connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table())) {
            return !rows.next() || rows.getLong(1) == 0;
        }
    }

    /**
     * The newest {@code updated_at} in the catalog, or 0 when empty. The hook for noticing edits
     * made by another writer to a shared database; nothing polls it yet.
     */
    public synchronized long latestRevision() throws SQLException {
        try (Connection connection = database.connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT MAX(updated_at) FROM " + table())) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }

    private void write(Connection connection, TerasRegion region, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table() + " (name, dimension, shape, priority, purchasable, price, "
                        + "fill_color, stroke_color, banner, geometry, flags, created_by, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + database.dialect().upsert(new String[]{"name"},
                                "dimension", "shape", "priority", "purchasable", "price",
                                "fill_color", "stroke_color", "banner", "geometry", "flags",
                                "created_by", "created_at", "updated_at"))) {
            statement.setString(1, region.getName());
            statement.setString(2, region.getDimension());
            statement.setString(3, region.getShape().name());
            statement.setInt(4, region.getPriority());
            statement.setBoolean(5, region.isPurchasable());
            statement.setLong(6, region.getPrice());
            statement.setInt(7, region.getFillColor());
            statement.setInt(8, region.getStrokeColor());
            statement.setString(9, region.getBanner());
            statement.setString(10, GSON.toJson(geometryOf(region)));
            statement.setString(11, region.getFlags() == null ? null : GSON.toJson(region.getFlags()));
            statement.setString(12, region.getCreatedBy());
            statement.setLong(13, region.getCreatedAt());
            statement.setLong(14, now);
            statement.executeUpdate();
        }
    }

    /** The {@code geometry} document: the variant fields for whichever shape this region is. */
    private static JsonObject geometryOf(TerasRegion region) {
        JsonObject json = new JsonObject();
        if (region.getShape() == TerasRegion.Shape.POLYGON) {
            json.add("points", GSON.toJsonTree(region.getPoints()));
            if (region.getMinY() != null) json.addProperty("minY", region.getMinY());
            if (region.getMaxY() != null) json.addProperty("maxY", region.getMaxY());
        } else {
            json.add("min", GSON.toJsonTree(region.getMin()));
            json.add("max", GSON.toJsonTree(region.getMax()));
        }
        return json;
    }

    /**
     * Rebuilds a region from its row, or {@code null} if the row is unusable. Lenient by design,
     * matching the json store it replaces: one corrupt row should cost that region, not the whole
     * catalog — losing the catalog would drop protection everywhere at once.
     */
    private static TerasRegion read(ResultSet rows) throws SQLException {
        String name = rows.getString(1);
        try {
            String dimension = rows.getString(2);
            TerasRegion.Shape shape = TerasRegion.Shape.valueOf(rows.getString(3));
            JsonObject geometry = GSON.fromJson(rows.getString(10), JsonObject.class);

            TerasRegion region;
            if (shape == TerasRegion.Shape.POLYGON) {
                List<RegionPoint> points = GSON.fromJson(geometry.get("points"), POINTS_TYPE);
                Integer minY = geometry.has("minY") ? geometry.get("minY").getAsInt() : null;
                Integer maxY = geometry.has("maxY") ? geometry.get("maxY").getAsInt() : null;
                region = TerasRegion.polygon(name, dimension, points, minY, maxY);
            } else {
                region = TerasRegion.cuboid(name, dimension,
                        GSON.fromJson(geometry.get("min"), TerasRegion.Corner.class),
                        GSON.fromJson(geometry.get("max"), TerasRegion.Corner.class));
            }

            region.setPriority(rows.getInt(4));
            region.setPurchasable(rows.getBoolean(5));
            region.setPrice(rows.getLong(6));
            region.setFillColor(rows.getInt(7));
            region.setStrokeColor(rows.getInt(8));
            region.setBanner(rows.getString(9));
            String flags = rows.getString(11);
            if (flags != null && !flags.isBlank()) {
                region.setFlags(GSON.fromJson(flags, FLAGS_TYPE));
            }
            region.setCreatedBy(rows.getString(12));
            region.setCreatedAt(rows.getLong(13));

            region.normalize();
            String error = region.validationError();
            if (error != null) {
                Teras.LOGGER.warn("RegionDatabase: skipping region '{}': {}", name, error);
                return null;
            }
            return region;
        } catch (Exception e) {
            Teras.LOGGER.warn("RegionDatabase: skipping unreadable region '{}': {}", name, e.toString());
            return null;
        }
    }
}
