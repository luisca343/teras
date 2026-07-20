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
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Port of the 1.16.5 {@code TerasConfig} + {@code FileHelper.getConfig()}. Reads
 * {@code config/teras/config.yml}:
 * <pre>
 * id: "..."
 * home: "..."
 * apiURL: "..."
 * requireHttps: false
 * sql:
 *   use: false
 * </pre>
 *
 * <p>YAML rather than JSON so the file can carry comments explaining each key; a pre-existing
 * {@code config.json} is migrated once on startup and kept as {@code config.json.migrated}.
 * Defaults and migrations render a commented template ({@link #renderTemplate()}) instead of
 * serializing this class, because every YAML writer discards comments.</p>
 *
 * <p>This is the <b>server's</b> configuration, and only the server's. It is loaded when a server
 * starts — a dedicated server, or the integrated one behind a single-player world — and never on a
 * client that is merely connecting somewhere: a player's own {@code config.yml} describes the world
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
     * The real SmartRotom site. Admins override this in {@code config/teras/config.yml}. Its host is
     * the trusted origin every SmartRotom browser is confined to ({@link #isSiteAllowed(String)}), so
     * it must be an absolute URL. Switch to {@code https://} in config once the endpoint serves TLS.
     */
    private static final String DEFAULT_HOME = "http://teras.es/smartrotom";
    /**
     * Base URL of the SmartRotom HTTP API — the <b>host only</b>, with no path.
     *
     * <p>Every call site in {@code net.SmartRotomService} appends the full route including its
     * {@code /smartrotom} prefix ({@code getApiUrl() + "/smartrotom/dungeons/run"}), so a base that
     * already ends in {@code /smartrotom} produces {@code /smartrotom/smartrotom/…} and every
     * outbound call 404s. {@link #normalizeApiUrl} exists to catch exactly that.</p>
     */
    private static final String DEFAULT_API_URL = "https://api.ficuslab.es";

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

    // ---- Plot storage (see plot/PlotDatabase). SQLite unless a dsn is configured. ----

    /**
     * Where plot ownership and the money ledger are stored.
     *
     * <p>{@code use = false} (the default) keeps everything in a local SQLite file, which needs no
     * setup and is what the tests and single-player run against. Setting it points the mod at
     * MySQL instead — the deployment where the SmartRotom backend shares the same database and
     * reads plot ownership directly, rather than being mirrored to over HTTP.</p>
     *
     * <p>{@code tablePrefix} exists because that shared database is not ours alone: it namespaces
     * our tables away from whatever else lives there.</p>
     *
     * <p>The password is a credential on disk. It must never reach a log line or an HTTP response
     * — see {@link #describe()}, which is the only form of these settings safe to print.</p>
     */
    public record SqlSettings(boolean use, String dsn, String username, String password,
                              String tablePrefix) {

        public static final String DEFAULT_TABLE_PREFIX = "teras_";

        public static SqlSettings sqliteDefault() {
            return new SqlSettings(false, "", "", "", DEFAULT_TABLE_PREFIX);
        }

        /** Loggable form: everything except the password, which is only ever reported as set/unset. */
        public String describe() {
            if (!use) return "sqlite (local file)";
            return "mysql dsn=" + dsn + " user=" + username
                    + " password=" + (password == null || password.isEmpty() ? "(unset)" : "(set)")
                    + " prefix=" + tablePrefix;
        }

        /** Why these settings are unusable, or {@code null} if they are fine. */
        public String validationError() {
            if (!use) return null;
            if (dsn == null || dsn.isBlank()) {
                return "sql.use is true but sql.dsn is empty";
            }
            if (!dsn.startsWith("jdbc:")) {
                return "sql.dsn must be a JDBC url starting with 'jdbc:' (got '" + dsn + "')";
            }
            return null;
        }
    }

    private static SqlSettings sql = SqlSettings.sqliteDefault();

    public static SqlSettings sql() {
        return sql;
    }

    /**
     * Loaded before the world does, so everything downstream (the HTTP API at
     * {@code ServerStartedEvent}, the join-time sync to clients) already has it.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        load();
    }

    /**
     * Re-reads the file on a running server, returning the keys whose new values will <b>not</b>
     * apply until a restart — empty when the reload took full effect.
     *
     * <p>Not every setting can be hot-swapped, and the difference is invisible from the outside.
     * {@code apiURL}, {@code apiToken} and {@code id} are read per call, so they change the very
     * next outbound request; {@code httpBind}, {@code httpPort} and {@code httpEnabled} were
     * consumed once when the socket was bound, {@code sql} when the databases opened, and
     * {@code home}/{@code requireHttps} are handed to clients as they join. Reloading silently
     * would leave an admin who fixed a port convinced it had taken, which is worse than not
     * offering a reload at all — so the caller is handed the list and tells them.</p>
     */
    public static List<String> reload() {
        boolean wasHttpEnabled = httpEnabled;
        String wasHttpBind = httpBind;
        int wasHttpPort = httpPort;
        String wasHttpToken = httpToken;
        String wasHome = home;
        boolean wasRequireHttps = requireHttps;
        SqlSettings wasSql = sql;

        load();

        List<String> restartOnly = new ArrayList<>();
        if (httpEnabled != wasHttpEnabled) {
            restartOnly.add("httpEnabled");
        }
        if (!Objects.equals(httpBind, wasHttpBind)) {
            restartOnly.add("httpBind");
        }
        if (httpPort != wasHttpPort) {
            restartOnly.add("httpPort");
        }
        if (!Objects.equals(httpToken, wasHttpToken)) {
            restartOnly.add("httpToken");
        }
        if (!Objects.equals(home, wasHome)) {
            restartOnly.add("home");
        }
        if (requireHttps != wasRequireHttps) {
            restartOnly.add("requireHttps");
        }
        if (!Objects.equals(sql, wasSql)) {
            restartOnly.add("sql");
        }
        return restartOnly;
    }

    public static void load() {
        try {
            // A client JVM starts an integrated server per world, so reset first: without this, keys
            // absent from the new file would keep the previous world's values.
            resetToDefaults();

            Path dir = FMLPaths.CONFIGDIR.get().resolve("teras");
            Path path = dir.resolve("config.yml");
            Path legacy = dir.resolve("config.json");

            if (!Files.exists(path) && Files.exists(legacy)) {
                migrateFromJson(dir, path, legacy);
                // Fall through and read the file just written, so the migrated and steady-state
                // paths cannot drift apart.
            }

            if (!Files.exists(path)) {
                id = randomId();
                YamlConfig.write(path, renderTemplate());
                Teras.LOGGER.info("Created default config/teras/config.yml (id={}, home={})", id, home);
                return;
            }

            YamlConfig yaml = YamlConfig.read(path);

            home = yaml.string("home", home);
            // API_URL was the pre-rename spelling; files written before it still say that, and a
            // miss here would silently point the server at the default backend.
            apiUrl = normalizeApiUrl(yaml.string("apiURL", yaml.string("API_URL", apiUrl)));
            apiToken = yaml.string("apiToken", apiToken);
            requireHttps = yaml.bool("requireHttps", requireHttps);
            httpEnabled = yaml.bool("httpEnabled", httpEnabled);
            httpBind = yaml.string("httpBind", httpBind);
            httpPort = yaml.integer("httpPort", httpPort);
            httpToken = yaml.string("httpToken", httpToken);
            if (yaml.has("sql")) {
                sql = readSql(yaml.section("sql"));
            }

            // The server/world id must be stable across restarts. If an existing file has none,
            // mint one and append it — appending rather than rewriting so the admin's comments and
            // any keys this version does not know about survive untouched.
            if (yaml.has("id")) {
                id = yaml.string("id", "");
            } else {
                id = randomId();
                Files.writeString(path, "\n# Generated on first run; keep it stable across restarts.\nid: \""
                        + id + "\"\n", java.nio.file.StandardOpenOption.APPEND);
                Teras.LOGGER.info("config/teras/config.yml had no 'id'; generated server id '{}'", id);
            }

            // NOTE: no token is minted here. A blank httpToken deliberately means "no auth", matching
            // the Wungill API the SmartRotom backend was written against — generating one would 401
            // every backend call. TerasHttpServer warns at startup instead. See docs/HTTP_API.md.

            if (home == null || home.isBlank()) {
                Teras.LOGGER.warn("config/teras/config.yml has no 'home'; falling back to {}", DEFAULT_HOME);
                home = DEFAULT_HOME;
            }
            // The home defines the trusted origin, so it must have a parseable host: with none, the
            // client-side navigation guard has nothing to allow and every page reads as untrusted.
            if (UrlOrigin.hostOf(home) == null) {
                Teras.LOGGER.error("Configured SmartRotom home '{}' has no host — it must be an absolute "
                        + "URL (http://host/path). The browser's navigation guard will block every page "
                        + "and the JS bridge will refuse every query until this is fixed.", home);
            }

            String sqlError = sql.validationError();
            if (sqlError != null) {
                Teras.LOGGER.error("Invalid plot storage config ({}); falling back to local SQLite. "
                        + "Plots will NOT be shared with the SmartRotom backend until this is fixed.",
                        sqlError);
                sql = SqlSettings.sqliteDefault();
            }

            Teras.LOGGER.info("Teras config loaded (id={}, home={}, plots={})", id, home, sql.describe());
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
        sql = SqlSettings.sqliteDefault();
    }

    private static SqlSettings readSql(YamlConfig o) {
        SqlSettings defaults = SqlSettings.sqliteDefault();
        return new SqlSettings(
                o.bool("use", defaults.use()),
                o.string("dsn", defaults.dsn()),
                o.string("username", defaults.username()),
                o.string("password", defaults.password()),
                o.string("tablePrefix", defaults.tablePrefix()));
    }

    /**
     * One-shot upgrade from the pre-YAML {@code config.json}. Values are carried into the
     * commented template and the old file is renamed rather than deleted — a template render
     * cannot preserve keys this version does not know about, so the original stays on disk as the
     * record of what was there.
     */
    private static void migrateFromJson(Path dir, Path path, Path legacy) throws IOException {
        JsonObject json;
        try (Reader r = Files.newBufferedReader(legacy)) {
            json = GSON.fromJson(r, JsonObject.class);
        }
        if (json == null) json = new JsonObject();

        if (has(json, "id")) id = json.get("id").getAsString();
        if (has(json, "home")) home = json.get("home").getAsString();
        if (has(json, "API_URL")) apiUrl = normalizeApiUrl(json.get("API_URL").getAsString());
        if (has(json, "apiURL")) apiUrl = normalizeApiUrl(json.get("apiURL").getAsString());
        if (has(json, "apiToken")) apiToken = json.get("apiToken").getAsString();
        if (has(json, "requireHttps")) requireHttps = json.get("requireHttps").getAsBoolean();
        if (has(json, "httpEnabled")) httpEnabled = json.get("httpEnabled").getAsBoolean();
        if (has(json, "httpBind")) httpBind = json.get("httpBind").getAsString();
        if (has(json, "httpPort")) httpPort = json.get("httpPort").getAsInt();
        if (has(json, "httpToken")) httpToken = json.get("httpToken").getAsString();
        if (has(json, "sql") && json.get("sql").isJsonObject()) {
            JsonObject o = json.getAsJsonObject("sql");
            SqlSettings defaults = SqlSettings.sqliteDefault();
            sql = new SqlSettings(
                    has(o, "use") ? o.get("use").getAsBoolean() : defaults.use(),
                    has(o, "dsn") ? o.get("dsn").getAsString() : defaults.dsn(),
                    has(o, "username") ? o.get("username").getAsString() : defaults.username(),
                    has(o, "password") ? o.get("password").getAsString() : defaults.password(),
                    has(o, "tablePrefix") ? o.get("tablePrefix").getAsString() : defaults.tablePrefix());
        }
        if (id == null || id.isEmpty()) id = randomId();

        YamlConfig.write(path, renderTemplate());
        Path kept = dir.resolve("config.json.migrated");
        try {
            Files.move(legacy, kept, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Teras.LOGGER.warn("Migrated config to config.yml but could not rename the old "
                    + "config.json ({}). Delete it by hand — it is no longer read.", e.toString());
        }
        Teras.LOGGER.info("Migrated config/teras/config.json -> config.yml "
                + "(the old file is kept as config.json.migrated and is no longer read)");
    }

    /**
     * The config file as text, comments and all. Rendered rather than serialized: the whole reason
     * this is YAML is that an admin can read what each key does, and every YAML writer discards
     * comments. Values are quoted so that tokens YAML reads specially — {@code :} in a URL, a
     * password of digits, {@code no} as a literal — survive a round trip.
     */
    private static String renderTemplate() {
        return """
                # Teras server configuration.
                # Only the server reads this file; a client uses whatever the server it joined sends.

                # Identifies this server/world to the SmartRotom backend. Generated on first run —
                # set it to the value registered with the backend, and keep it stable. It is sent as
                # the top-level "server" field and must equal the backend's MC_WORLD, or its
                # MinecraftMiddleware tripwire 403s every POST before the route is even reached.
                id: "%s"

                # The SmartRotom site. Must be an absolute URL: its host is the ONLY origin the
                # in-game browser is allowed to navigate to.
                home: "%s"

                # Base URL of the SmartRotom HTTP API, for outbound calls. HOST ONLY — do not add a
                # path. Each route appends its own "/smartrotom/..." prefix, so a value ending in
                # /smartrotom sends /smartrotom/smartrotom/... and every call 404s.
                apiURL: "%s"

                # Bearer token sent WITH outbound requests to the API above. Must match the backend's
                # TERAS_API_TOKEN; routes behind GameServerAuthGuard (dungeons, caja) 401 without it.
                apiToken: "%s"

                # Refuse to load the site over plain http.
                requireHttps: %s

                # ---------------------------------------------------------------------------
                # Inbound HTTP API — what the SmartRotom backend calls to reach this server.
                # See docs/HTTP_API.md.
                # ---------------------------------------------------------------------------

                httpEnabled: %s

                # 127.0.0.1 only accepts connections from this machine. Use 0.0.0.0 to accept from
                # anywhere, and only behind a firewall or reverse proxy.
                httpBind: "%s"
                httpPort: %s

                # Token callers must present. BLANK MEANS NO AUTHENTICATION, which is what the
                # SmartRotom backend currently expects — it sends no Authorization header.
                httpToken: "%s"

                # ---------------------------------------------------------------------------
                # Plot storage — ownership and the money ledger. Region geometry always stays in
                # regions.json. See docs/PARCELAS.md.
                # ---------------------------------------------------------------------------

                sql:
                  # false = a local SQLite file at config/teras/teras.db. Needs no setup and is the
                  #         right choice unless the SmartRotom web must read plots directly.
                  # true  = MySQL. Use this to share one database with the backend, so the web reads
                  #         ownership itself instead of being mirrored to over HTTP.
                  use: %s

                  # JDBC url. The database must already exist; the mod creates and migrates its own
                  # tables inside it.
                  dsn: "%s"
                  username: "%s"

                  # Kept in this file in plain text — make sure it is not world-readable and not
                  # committed to git. It is never written to the log.
                  password: "%s"

                  # Namespaces our tables away from anything else sharing that database.
                  tablePrefix: "%s"
                """.formatted(id, home, apiUrl, apiToken, requireHttps, httpEnabled, httpBind,
                httpPort, httpToken, sql.use(), sql.dsn(), sql.username(), sql.password(),
                sql.tablePrefix());
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

    /**
     * Trims a configured {@code apiURL} down to the bare base every call site expects: no trailing
     * slash, and no trailing {@code /smartrotom}.
     *
     * <p>The prefix strip is a migration, not a convenience. Versions up to and including the 1.21.1
     * port shipped {@code DEFAULT_API_URL = "http://api.boffmedia.es/smartrotom"} while every route in
     * {@code SmartRotomService} appended its own {@code /smartrotom}, so every config.yml written by
     * those builds carries the doubled form — and config files are never rewritten in place. Fixing
     * only the default would leave those servers still POSTing to
     * {@code /smartrotom/smartrotom/dungeons/run} and still silently 404ing. The warning is loud
     * because the file on disk stays wrong until an admin edits it.</p>
     */
    public static String normalizeApiUrl(String url) {
        if (url == null || url.isBlank()) {
            return DEFAULT_API_URL;
        }
        String trimmed = url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/smartrotom")) {
            String fixed = trimmed.substring(0, trimmed.length() - "/smartrotom".length());
            Teras.LOGGER.warn("config/teras/config.yml apiURL ends in '/smartrotom' ('{}'). Every route "
                    + "already adds that prefix, so this would send /smartrotom/smartrotom/... and 404. "
                    + "Using '{}' instead — please drop the suffix from the file.", trimmed, fixed);
            return fixed;
        }
        return trimmed;
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
