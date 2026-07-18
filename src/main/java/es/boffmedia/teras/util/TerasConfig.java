package es.boffmedia.teras.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Port of the 1.16.5 {@code TerasConfig} + {@code FileHelper.getConfig()}. Reads
 * {@code config/teras/config.json}:
 * <pre>{ "id": "...", "home": "...", "API_URL": "...", "apiToken": "...",
 *   "requireHttps": false }</pre>
 *
 * <p>This is the <b>server's</b> configuration, and only the server's. It is loaded when a server
 * starts — a dedicated server, or the integrated one behind a single-player world — and never on a
 * client that is merely connecting somewhere: a player's own {@code config.json} describes the world
 * <i>they</i> host, so honouring it while on someone else's server would point their SmartRotom at
 * the wrong site. What the client needs travels over the wire instead, from the server it joined
 * ({@code net.ServerConfigPayload} → {@code client.ServerConfig}); single-player goes through that
 * same path, since the integrated server is still the server.</p>
 *
 * <p>If the file does not exist it is <b>created with defaults</b> (ported from the old
 * {@code getConfig()}), so an admin has a template to edit. The default {@code home} points at the
 * <b>real SmartRotom site</b> ({@link #DEFAULT_HOME}) rather than a generic browser page.</p>
 *
 * <p><b>{@code id} — the server/world identifier.</b> This is the value the SmartRotom web uses to
 * confirm which server a player is on: the server injects its own {@code id} into the
 * {@code getUserData} response as the {@code world} field (see {@code TerasNet#handleUserDataRequest}).
 * A random id is generated and persisted on first run; real deployments set it to the value
 * registered with the SmartRotom backend.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class TerasConfig {
    private TerasConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * The real SmartRotom site. Admins override this in {@code config/teras/config.json}. Its host is
     * the trusted origin every SmartRotom browser is confined to ({@link #isSiteAllowed(String)}), so
     * it must be an absolute URL. Switch to {@code https://} in config once the endpoint serves TLS.
     */
    private static final String DEFAULT_HOME = "http://teras.es/smartrotom";
    /** Base URL of the SmartRotom HTTP API (used by the deferred server-side integrations). */
    private static final String DEFAULT_API_URL = "http://api.boffmedia.es/smartrotom";

    /** Loopback — the fail-safe default bind for the inbound HTTP API. */
    private static final String DEFAULT_HTTP_BIND = "127.0.0.1";
    /** Wungill's {@code DEFAULT_PORT}: the mod is a drop-in for WINGULL_API, so keep its port. */
    private static final int DEFAULT_HTTP_PORT = 34370;

    // Server/world identifier the SmartRotom web keys on. Empty until load() sets it.
    private static String id = "";
    private static String home = DEFAULT_HOME;
    private static String apiUrl = DEFAULT_API_URL;
    // Server-side secret (bearer token for outbound API requests). Stays empty on clients.
    private static String apiToken = "";
    private static boolean requireHttps = false;

    // ---- Inbound HTTP API (see http/TerasHttpServer). Server-side only; off by default. ----
    private static boolean httpEnabled = false;
    private static String httpBind = DEFAULT_HTTP_BIND;
    private static int httpPort = DEFAULT_HTTP_PORT;
    /**
     * Bearer token callers must present, or blank for <b>no authentication</b> — which is what the old
     * Wungill API did, and what the SmartRotom backend still expects (it sends no {@code Authorization}
     * header). Set it and it's enforced; the server warns at startup while it's blank.
     *
     * <p>Distinct from {@link #apiToken} on purpose: that one is an outbound credential we send to
     * SmartRotom, this one guards traffic coming in. Reusing a single secret for both directions would
     * mean a leak in either place compromises the other.</p>
     */
    private static String httpToken = "";

    /**
     * Loaded before the world does, so everything downstream (the HTTP API at
     * {@code ServerStartedEvent}, the join-time sync to clients) already has it.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        load();
    }

    public static void load() {
        try {
            // A client JVM starts an integrated server per world, so reset first: without this, keys
            // absent from the new file would keep the previous world's values.
            resetToDefaults();

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
            if (has(json, "httpEnabled")) httpEnabled = json.get("httpEnabled").getAsBoolean();
            if (has(json, "httpBind")) httpBind = json.get("httpBind").getAsString();
            if (has(json, "httpPort")) httpPort = json.get("httpPort").getAsInt();
            if (has(json, "httpToken")) httpToken = json.get("httpToken").getAsString();

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

            // NOTE: no token is minted here. A blank httpToken deliberately means "no auth", matching
            // the Wungill API the SmartRotom backend was written against — generating one would 401
            // every backend call. TerasHttpServer warns at startup instead. See docs/HTTP_API.md.

            if (home == null || home.isBlank()) {
                Teras.LOGGER.warn("config/teras/config.json has no 'home'; falling back to {}", DEFAULT_HOME);
                home = DEFAULT_HOME;
            }
            // The home defines the trusted origin, so it must have a parseable host: with none, the
            // client-side navigation guard has nothing to allow and every page reads as untrusted.
            if (UrlOrigin.hostOf(home) == null) {
                Teras.LOGGER.error("Configured SmartRotom home '{}' has no host — it must be an absolute "
                        + "URL (http://host/path). The browser's navigation guard will block every page "
                        + "and the JS bridge will refuse every query until this is fixed.", home);
            }

            if (dirty) {
                Files.writeString(path, GSON.toJson(json));
            }
            Teras.LOGGER.info("Teras config loaded (id={}, home={})", id, home);
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to load Teras config", e);
        }
    }

    private static void resetToDefaults() {
        id = "";
        home = DEFAULT_HOME;
        apiUrl = DEFAULT_API_URL;
        apiToken = "";
        requireHttps = false;
        httpEnabled = false;
        httpBind = DEFAULT_HTTP_BIND;
        httpPort = DEFAULT_HTTP_PORT;
        httpToken = "";
    }

    private static void writeDefault(Path dir, Path path) throws IOException {
        Files.createDirectories(dir);
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("home", home);
        json.addProperty("API_URL", apiUrl);
        json.addProperty("apiToken", apiToken);
        json.addProperty("requireHttps", requireHttps);
        json.addProperty("httpEnabled", httpEnabled);
        json.addProperty("httpBind", httpBind);
        json.addProperty("httpPort", httpPort);
        json.addProperty("httpToken", httpToken);
        Files.writeString(path, GSON.toJson(json));
    }

    private static boolean has(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    /** 8-char alphanumeric, matching 1.16.5 {@code RandomStringUtils.random(8, true, true)}. */
    private static String randomId() {
        return randomString(8);
    }

    private static String randomString(int length) {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    /** The server/world identifier (config {@code id}); authoritative on the server. */
    public static String getId() { return id; }
    public static String getHome() { return home; }
    public static String getApiUrl() { return apiUrl; }
    public static String getApiToken() { return apiToken; }
    public static boolean isRequireHttps() { return requireHttps; }

    public static boolean isHttpEnabled() { return httpEnabled; }
    public static String getHttpBind() { return httpBind; }
    public static int getHttpPort() { return httpPort; }
    public static String getHttpToken() { return httpToken; }

    /** True when {@link #getHttpBind()} is anything other than loopback — i.e. reachable off-box. */
    public static boolean isHttpBindPublic() {
        return !"127.0.0.1".equals(httpBind) && !"localhost".equals(httpBind) && !"::1".equals(httpBind);
    }

    /**
     * True when {@code url} is on the configured {@link #getHome() home} site — the allowlist the MCEF
     * navigation guard and the JS query bridge enforce client-side
     * ({@code mcef.TerasNavigationGuard}, {@code mcef.TerasQueryRouter}).
     *
     * <p>Host-based, not substring-based: 1.16.5's {@code url.contains("smartrotom")} accepted
     * {@code https://evil.example/smartrotom}, so any page that could steer the browser there inherited
     * the bridge. See {@link UrlOrigin#sameSite}.</p>
     */
    public static boolean isSiteAllowed(String url) {
        return UrlOrigin.sameSite(home, url);
    }
}
