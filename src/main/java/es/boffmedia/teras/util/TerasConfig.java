package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal port of the 1.16.5 {@code TerasConfig}. Reads {@code config/teras/config.json}:
 * <pre>{ "home": "...", "API_URL": "...", "apiToken": "...", "requireHttps": false }</pre>
 * Only the fields SmartRotom's first slice needs are loaded here.
 */
public final class TerasConfig {
    private TerasConfig() {}

    private static final Gson GSON = new Gson();

    // Fallback home page until config is present. The SmartRotom whitelist keys on "smartrotom".
    private static String home = "https://www.google.com";
    private static String apiUrl = null;
    private static String apiToken = null;
    private static boolean requireHttps = false;

    public static void load() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("config.json");
            if (!Files.exists(path)) {
                Teras.LOGGER.warn("No config/teras/config.json found; SmartRotom home defaults to {}", home);
                return;
            }
            try (Reader r = Files.newBufferedReader(path)) {
                JsonObject json = GSON.fromJson(r, JsonObject.class);
                if (json.has("home")) home = json.get("home").getAsString();
                if (json.has("API_URL")) apiUrl = json.get("API_URL").getAsString();
                if (json.has("apiToken")) apiToken = json.get("apiToken").getAsString();
                if (json.has("requireHttps")) requireHttps = json.get("requireHttps").getAsBoolean();
            }
            Teras.LOGGER.info("Teras config loaded (home={})", home);
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to load Teras config", e);
        }
    }

    public static String getHome() { return home; }
    public static String getApiUrl() { return apiUrl; }
    public static String getApiToken() { return apiToken; }
    public static boolean isRequireHttps() { return requireHttps; }

    /** SmartRotom pads are whitelisted to the smartrotom site only (matches 1.16.5 isSiteAllowed). */
    public static boolean isSiteAllowed(String url) {
        return url != null && url.contains("smartrotom");
    }
}
