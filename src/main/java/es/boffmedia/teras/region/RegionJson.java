package es.boffmedia.teras.region;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.region.model.TerasRegion;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The wire shape shared by the HTTP {@code GET /regions} endpoint and the client sync payload:
 * a bare array in the legacy backend format ({@code name}, {@code points}, {@code fillColor},
 * {@code strokeColor}) extended additively with the full model fields. Cuboid regions synthesize
 * their four XZ corners into {@code points} so legacy consumers (the web map) can draw them
 * without knowing about shapes.
 */
public final class RegionJson {
    private RegionJson() {}

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<TerasRegion>>() {}.getType();

    public static String toWebArray(Collection<TerasRegion> regions) {
        JsonArray array = new JsonArray();
        for (TerasRegion region : regions) {
            JsonObject obj = GSON.toJsonTree(region).getAsJsonObject();
            if (region.getShape() == TerasRegion.Shape.CUBOID) {
                obj.add("points", GSON.toJsonTree(region.outline()));
            }
            array.add(obj);
        }
        return GSON.toJson(array);
    }

    /** Parses a web-shaped array back into models, dropping entries that fail validation. */
    public static List<TerasRegion> fromWebArray(String json) {
        List<TerasRegion> parsed = GSON.fromJson(json, LIST_TYPE);
        List<TerasRegion> valid = new ArrayList<>();
        if (parsed == null) return valid;
        for (TerasRegion region : parsed) {
            if (region == null) continue;
            region.normalize();
            if (region.validationError() == null) valid.add(region);
        }
        return valid;
    }
}
