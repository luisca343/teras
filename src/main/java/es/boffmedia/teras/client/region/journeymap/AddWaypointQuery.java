package es.boffmedia.teras.client.region.journeymap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The fields the web sends with {@code addWaypoint}: {@code name}, {@code x}/{@code y}/{@code z},
 * {@code color}, {@code dimension}. Defaults match 1.16.5 ({@code "waypoint"}, {@code 0/64/0}, white,
 * and the player's current dimension).
 *
 * <p>Split from {@link WaypointQueries} so the parse is testable: it names no JourneyMap type, while
 * that class cannot even load without JourneyMap installed. The caller supplies
 * {@code fallbackDimension} rather than this reading it off the player, which is what keeps it pure.</p>
 *
 * <p>Parsed leniently — the page is the caller, and a nonsense field should still place a waypoint
 * somewhere sane rather than fail.</p>
 */
record AddWaypointQuery(String name, int x, int y, int z, int color, String dimension) {

    static final int WHITE = 0xFFFFFF;

    static AddWaypointQuery from(JsonObject json, String fallbackDimension) {
        return new AddWaypointQuery(
                string(json, "name", "waypoint"),
                integer(json, "x", 0),
                integer(json, "y", 64),
                integer(json, "z", 0),
                color(string(json, "color", null)),
                string(json, "dimension", fallbackDimension));
    }

    /**
     * {@code "#RRGGBB"} or bare {@code "RRGGBB"}. 1.16.5 used {@code Color.decode}, which also accepted
     * decimal and octal; hex is the only form the page has ever sent, and anything unparseable falls
     * back to white exactly as it did there.
     *
     * <p>Parsed as a {@code long} before masking so an alpha-carrying {@code "#AARRGGBB"} keeps its
     * colour: {@code Integer.parseInt} overflows on anything above {@code 7FFFFFFF} and would throw,
     * turning a valid-looking colour white.</p>
     */
    private static int color(String raw) {
        if (raw == null) return WHITE;
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            return (int) (Long.parseLong(hex, 16) & WHITE);
        } catch (NumberFormatException e) {
            return WHITE;
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement value = json.get(key);
        return (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                ? fallback : value.getAsString();
    }

    private static int integer(JsonObject json, String key, int fallback) {
        JsonElement value = json.get(key);
        try {
            return (value == null || !value.isJsonPrimitive()) ? fallback : value.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
