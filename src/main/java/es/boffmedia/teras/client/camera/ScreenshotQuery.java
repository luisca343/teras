package es.boffmedia.teras.client.camera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * The options the web sends with {@code takeScreenshot}: {@code includeUI}, {@code format},
 * {@code quality}. Defaults match 1.16.5 ({@code true}, {@code png}, {@code 90}).
 *
 * <p>Parsed leniently — the page is the caller, and a missing or nonsense field should give a photo
 * rather than an error. {@code format} falls back to PNG when unrecognised and {@code quality} is
 * clamped, so neither can reach {@code ImageIO} as something it will throw on.</p>
 */
record ScreenshotQuery(boolean includeUI, String format, int quality) {

    static final String PNG = "png";
    static final String JPEG = "jpeg";

    private static final int MIN_QUALITY = 1;
    private static final int MAX_QUALITY = 100;

    static ScreenshotQuery from(JsonObject json) {
        return new ScreenshotQuery(
                bool(json, "includeUI", true),
                format(string(json, "format", PNG)),
                clamp(integer(json, "quality", 90)));
    }

    /** True when this asks for JPEG; {@code quality} only means anything then. */
    boolean isJpeg() {
        return JPEG.equals(format);
    }

    /** The {@code data:image/...} MIME subtype for this format. */
    String mimeSubtype() {
        return format;
    }

    /** Normalizes to {@link #PNG} or {@link #JPEG}; {@code jpg} is accepted as an alias. */
    private static String format(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return (lower.equals(JPEG) || lower.equals("jpg")) ? JPEG : PNG;
    }

    private static int clamp(int quality) {
        return Math.max(MIN_QUALITY, Math.min(MAX_QUALITY, quality));
    }

    /**
     * Requires a real JSON boolean. {@code getAsBoolean} on a string runs it through
     * {@code Boolean.parseBoolean}, which answers {@code false} for anything that isn't "true" instead
     * of failing — so {@code "includeUI":"nope"} would silently mean "hide the HUD".
     */
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return value.getAsBoolean();
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement value = json.get(key);
        return (value == null || !value.isJsonPrimitive()) ? fallback : value.getAsString();
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
