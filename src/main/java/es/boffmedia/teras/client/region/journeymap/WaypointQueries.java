package es.boffmedia.teras.client.region.journeymap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Serves the SmartRotom page's {@code getWaypoints} / {@code addWaypoint} queries against JourneyMap.
 *
 * <p>Names {@code journeymap.api.v2.*} directly, so it is only ever reached behind a
 * {@code ModList.isLoaded("journeymap")} guard (see {@code util.QueryHelper}) — without JourneyMap
 * installed this class never loads.</p>
 *
 * <p>The 1.16.5 original drove the internal {@code WaypointStore} through ~90 lines of reflection
 * because the v1 {@code Waypoint} accessors were not public API. On v2 every accessor used here —
 * {@code getName}, {@code getX/Y/Z}, {@code getColor}, {@code getPrimaryDimension} — is on the public
 * {@link Waypoint} interface, so the whole reflection block is gone. The JSON wire shape is unchanged,
 * so the page needs no edit.</p>
 */
public final class WaypointQueries {
    private WaypointQueries() {}

    private static final Gson GSON = new Gson();

    /** Replies with every waypoint the player has, from any mod — the 1.16.5 {@code getAll()} scope. */
    public static void handleGetWaypoints(JsQueryCallback callback) {
        Minecraft.getInstance().execute(() -> {
            try {
                IClientAPI api = api();
                if (api == null) {
                    callback.failure(503, "JourneyMap is installed but not initialized yet");
                    return;
                }
                JsonArray waypoints = new JsonArray();
                for (Waypoint waypoint : api.getAllWaypoints()) {
                    try {
                        waypoints.add(toJson(waypoint));
                    } catch (Exception e) {
                        // One malformed waypoint must not empty the whole list, as in 1.16.5.
                        Teras.LOGGER.warn("Skipping unreadable waypoint: {}", e.toString());
                    }
                }
                JsonObject response = new JsonObject();
                response.addProperty("status", "ok");
                response.add("waypoints", waypoints);
                callback.success(GSON.toJson(response));
            } catch (Exception e) {
                Teras.LOGGER.error("Error handling getWaypoints", e);
                callback.failure(0, "Error handling getWaypoints: " + e.getMessage());
            }
        });
    }

    /**
     * Adds a waypoint from {@code {name, x, y, z, color, dimension}}, defaulting the dimension to the
     * one the player is in. A name already in use is reported as {@code status:"exists"} rather than
     * failing, so the page can tell "already there" from a real error.
     */
    public static void handleAddWaypoint(JsonObject json, JsQueryCallback callback) {
        Minecraft.getInstance().execute(() -> {
            try {
                IClientAPI api = api();
                if (api == null) {
                    callback.failure(503, "JourneyMap is installed but not initialized yet");
                    return;
                }
                AddWaypointQuery request = AddWaypointQuery.from(json, currentDimension());

                boolean exists = api.getAllWaypoints().stream()
                        .anyMatch(existing -> request.name().equals(existing.getName()));
                if (exists) {
                    JsonObject response = new JsonObject();
                    response.addProperty("status", "exists");
                    response.addProperty("message", "Waypoint with that name already exists");
                    callback.success(GSON.toJson(response));
                    return;
                }

                // persistent=true: the page's waypoints are meant to outlive the session. 1.16.5 passed
                // false here but then put the waypoint in the store that saves to disk anyway, so this
                // matches what the old code actually did, not what its argument said.
                Waypoint waypoint = WaypointFactory.createWaypoint(Teras.MOD_ID,
                        new BlockPos(request.x(), request.y(), request.z()),
                        request.name(), request.dimension(), true);
                waypoint.setColor(request.color());
                api.addWaypoint(Teras.MOD_ID, waypoint);

                JsonObject response = new JsonObject();
                response.addProperty("status", "ok");
                response.addProperty("name", request.name());
                response.addProperty("x", request.x());
                response.addProperty("y", request.y());
                response.addProperty("z", request.z());
                response.addProperty("dimension", request.dimension());
                callback.success(GSON.toJson(response));
            } catch (Exception e) {
                Teras.LOGGER.error("Error handling addWaypoint", e);
                callback.failure(0, "Error handling addWaypoint: " + e.getMessage());
            }
        });
    }

    private static JsonObject toJson(Waypoint waypoint) {
        JsonObject json = new JsonObject();
        json.addProperty("name", waypoint.getName() == null ? "" : waypoint.getName());
        json.addProperty("x", waypoint.getX());
        json.addProperty("y", waypoint.getY());
        json.addProperty("z", waypoint.getZ());
        json.addProperty("color", String.format("#%06X", 0xFFFFFF & waypoint.getColor()));
        json.addProperty("dimension", waypoint.getPrimaryDimension());
        return json;
    }

    private static IClientAPI api() {
        TerasJourneyMapPlugin plugin = TerasJourneyMapPlugin.instance();
        return plugin == null ? null : plugin.api();
    }

    private static String currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? "minecraft:overworld"
                : minecraft.player.level().dimension().location().toString();
    }

}
