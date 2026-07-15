package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Port of the 1.16.5 {@code TerasConfig} + {@code FileHelper.getConfig()}. Reads
 * {@code config/teras/config.json}:
 * <pre>{ "id": "...", "home": "...", "API_URL": "...", "apiToken": "...", "requireHttps": false }</pre>
 *
 * <p>Loaded on <b>both sides</b> during common setup (see {@code Teras#onCommonSetup}). If the file
 * does not exist it is <b>created with defaults</b> (ported from the old {@code getConfig()}), so an
 * admin has a template to edit. The default {@code home} points at the <b>real SmartRotom site</b>
 * ({@link #DEFAULT_HOME}) rather than a generic browser page.</p>
 *
 * <p><b>{@code id} — the server/world identifier.</b> This is the value the SmartRotom web uses to
 * confirm which server a player is on: the <i>server</i> injects its own {@code id} into the
 * {@code getUserData} response as the {@code world} field (see {@code TerasNet#handleUserDataRequest}),
 * so a multiplayer client's local {@code id} is irrelevant — only the server's config {@code id}
 * counts. A random id is generated and persisted on first run; real deployments set it to the value
 * registered with the SmartRotom backend.</p>
 */
public final class TerasConfig {
    private TerasConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * The real SmartRotom site. Admins override this in {@code config/teras/config.json}.
     * Must contain {@code "smartrotom"} to pass {@link #isSiteAllowed(String)}. Switch to
     * {@code https://} in config once the endpoint serves TLS.
     */
    private static final String DEFAULT_HOME = "http://teras.es/smartrotom";
    /** Base URL of the SmartRotom HTTP API (used by the deferred server-side integrations). */
    private static final String DEFAULT_API_URL = "http://api.boffmedia.es/smartrotom";

    // Server/world identifier the SmartRotom web keys on. Empty until load() sets it.
    private static String id = "";
    private static String home = DEFAULT_HOME;
    private static String apiUrl = DEFAULT_API_URL;
    // Server-side secret (bearer token for outbound API requests). Stays empty on clients.
    private static String apiToken = "";
    private static boolean requireHttps = false;

    public static void load() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("teras");
            Path path = dir.resolve("config.json");

            if (!Files.exists(path)) {
                id = randomId();
                writeDefault(dir, path);
                Teras.LOGGER.info("Created default config/teras/config.json (id={}, home={})", id, home);
                return;
            }

            JsonObject json;
            try (Reader r = Files.newBufferedReader(path)) {
                json = GSON.fromJson(r, JsonObject.class);
            }
            if (json == null) json = new JsonObject();

            if (has(json, "home")) home = json.get("home").getAsString();
            if (has(json, "API_URL")) apiUrl = json.get("API_URL").getAsString();
            if (has(json, "apiToken")) apiToken = json.get("apiToken").getAsString();
            if (has(json, "requireHttps")) requireHttps = json.get("requireHttps").getAsBoolean();

            // The server/world id must be stable across restarts. If an existing file has none,
            // mint one and write it back (preserving any unknown fields already in the file).
            boolean dirty = false;
            if (has(json, "id")) {
                id = json.get("id").getAsString();
            } else {
                id = randomId();
                json.addProperty("id", id);
                dirty = true;
                Teras.LOGGER.info("config/teras/config.json had no 'id'; generated server id '{}'", id);
            }

            if (home == null || home.isBlank()) {
                Teras.LOGGER.warn("config/teras/config.json has no 'home'; falling back to {}", DEFAULT_HOME);
                home = DEFAULT_HOME;
            }
            if (!isSiteAllowed(home)) {
                Teras.LOGGER.warn("Configured SmartRotom home '{}' is not a smartrotom URL; navigation guards "
                        + "would treat it as untrusted.", home);
            }

            if (dirty) {
                Files.writeString(path, GSON.toJson(json));
            }
            Teras.LOGGER.info("Teras config loaded (id={}, home={})", id, home);
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to load Teras config", e);
        }
    }

    private static void writeDefault(Path dir, Path path) throws IOException {
        Files.createDirectories(dir);
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("home", home);
        json.addProperty("API_URL", apiUrl);
        json.addProperty("apiToken", apiToken);
        json.addProperty("requireHttps", requireHttps);
        Files.writeString(path, GSON.toJson(json));
    }

    private static boolean has(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    /** 8-char alphanumeric, matching 1.16.5 {@code RandomStringUtils.random(8, true, true)}. */
    private static String randomId() {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    /** The server/world identifier (config {@code id}); authoritative on the server. */
    public static String getId() { return id; }
    public static String getHome() { return home; }
    public static String getApiUrl() { return apiUrl; }
    public static String getApiToken() { return apiToken; }
    public static boolean isRequireHttps() { return requireHttps; }

    /** SmartRotoms are whitelisted to the smartrotom site only (matches 1.16.5 isSiteAllowed). */
    public static boolean isSiteAllowed(String url) {
        return url != null && url.contains("smartrotom");
    }
}
