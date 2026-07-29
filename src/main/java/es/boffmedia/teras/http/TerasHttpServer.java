package es.boffmedia.teras.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.team.BattleTeam;
import es.boffmedia.teras.battle.team.api.TeamProvider;
import es.boffmedia.teras.battle.team.api.TeamProviders;
import es.boffmedia.teras.dex.api.DexProvider;
import es.boffmedia.teras.dex.api.DexProviders;
import es.boffmedia.teras.dex.api.DexSnapshot;
import es.boffmedia.teras.economy.EconomyStore;
import es.boffmedia.teras.storage.api.StorageProvider;
import es.boffmedia.teras.storage.api.StorageProviders;
import es.boffmedia.teras.storage.api.StorageSession;
import es.boffmedia.teras.util.TerasConfig;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The read-only HTTP API the SmartRotom backend pulls quest state from — a <b>drop-in replacement for
 * the old Wungill {@code WINGULL_API}</b>, which served these same routes off
 * {@code com.sun.net.httpserver} on port 34370 from the 1.16.5 hybrid server.
 *
 * <pre>
 * GET  /ping               -> {success:true, message:"pong", data:null}  (no auth; liveness probe)
 * GET  /quests/all         -> {success, message, data:{quests, categories, dialogs}}  (wrapped)
 * GET  /quests/user/{uuid} -> {quests:{questId: QuestProgress}, categories:{...}}     (bare)
 * POST /givepokemon        -> {data:{given:true}}      body {uuid, pokespec, sendMessage}
 * POST /giveitems          -> {data:{given:N}}         body {uuid, items:[{id, amount, …}]}
 * POST /pc                 -> {data:[{box, index, pokemon}]}   body {uuid}   (occupied slots only)
 * POST /equipo             -> {data:[mon|null × 6]}            body {uuid}
 * POST /pc/move            -> {data:{moved:true}}     body {uuid, sourceBox, sourceIndex,
 *                                                           destinationBox, destinationIndex}
 * POST /updateBalance      -> {data:{success:true}}    body {balance, type, uuid}   (mirror in)
 * POST /getCurrentBalance  -> {data:balance}           body {uuid, amount}          (add, return new)
 * POST /money              -> {data:{money:balance}}   body {uuid}
 * GET  /weather            -> {data:{weather, changeTime, minecraftTime}}   (no body)
 * GET  /performance        -> {data:{tps, players, memory, uptime}}        (no body)
 * POST /globalchat         -> {data:{sent:true}}       body {uuid, message}
 * POST /position           -> {data:{online, x, y, z, dimension}}   body {uuid}
 *                             (offline is a 200 with online:false, not an error — the caller
 *                             branches on it; the taxi prices a fare from this)
 * POST /updatedex          -> {data:{SEEN:[...], CAUGHT:[...]}}   body {uuid}
 * POST /getallbattleteams  -> {data:{teams:[{id, name, pokemon}], maxTeams}}   body {uuid}
 * POST /updatebattleteam   -> {data:{updated:true}}   body {uuid, name, teamSlot,
 *                                                          pokemon:{box, slot}}
 * POST /stats              -> {data:{stats:{...}, DataVersion}}   body {uuid}
 * GET  /regions            -> [{name, points, fillColor, strokeColor, dimension, shape, ...}]
 *                             (bare array — the shape the old backend itself served at /regions;
 *                             the mod is now the source of truth, the backend/web map the consumer)
 * </pre>
 *
 * The envelope on one route and not the other is 1.16.5's inconsistency, reproduced deliberately: the
 * backend reads {@code response.data.data.quests} for the catalog and {@code response.data.quests} for
 * the user. "Fixing" it here breaks the live pipeline. Point {@code WINGULL_API} at this server and
 * the whole backend + 4h cache + board works unchanged.
 *
 * <p>This is the only place Teras listens; everything else ({@code SmartRotomService}/{@code HttpText})
 * is outbound.</p>
 *
 * <h2>Security posture</h2>
 * Built for <b>server-to-server</b> calls, not browsers — no CORS headers, so a page can't read it
 * cross-origin.
 * <ul>
 *   <li><b>Off by default</b> ({@code httpEnabled: false}); single-player never binds a socket.</li>
 *   <li><b>Loopback by default</b> ({@code httpBind: "127.0.0.1"}); widening it logs a warning.</li>
 *   <li><b>Auth is opt-in</b>: a blank {@code httpToken} means no auth, matching Wungill (whose API was
 *       unauthenticated) so the backend needs no change. Set a token and it's enforced in constant
 *       time.</li>
 *   <li><b>Not read-only.</b> {@code /givepokemon} and {@code /giveitems} <b>write</b>: they hand a
 *       player an arbitrary Pokémon or item stack. The backend authenticates none of this — it sends
 *       no {@code Authorization} header at all (23 raw axios call sites, no shared client, so there is
 *       nowhere to add one today) — which is why they must stay fail-open, and why requiring
 *       {@code httpToken} would 401 every arcade claim.</li>
 * </ul>
 * ⚠️ <b>Blank token + public bind therefore means anyone who can reach this port can mint items.</b>
 * Nothing in this mod prevents that; only the network does. Keep the bind private or firewall the port
 * to the backend — see {@link #warnAboutExposure()}. No TLS — terminate at a proxy.
 *
 * <h2>Threading</h2>
 * Handlers run on {@link Teras#EXECUTOR}, so reading quest state directly would race the game loop.
 * Every read hops onto the server thread via {@link MinecraftServer#submit(Supplier)} and waits with a
 * timeout, so a stalled server yields 503 rather than pinning HTTP threads.
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class TerasHttpServer {
    private TerasHttpServer() {}

    private static final String PING_PATH = "/ping";
    private static final String QUESTS_ALL_PATH = "/quests/all";
    private static final String QUESTS_USER_PREFIX = "/quests/user/";
    private static final String GIVE_POKEMON_PATH = "/givepokemon";
    private static final String GIVE_ITEMS_PATH = "/giveitems";
    private static final String UPDATE_BALANCE_PATH = "/updateBalance";
    private static final String GET_CURRENT_BALANCE_PATH = "/getCurrentBalance";
    private static final String MONEY_PATH = "/money";
    private static final String PC_PATH = "/pc";
    private static final String PC_MOVE_PATH = "/pc/move";
    private static final String PARTY_PATH = "/equipo";
    private static final String WEATHER_PATH = "/weather";
    private static final String PERFORMANCE_PATH = "/performance";
    private static final String GLOBAL_CHAT_PATH = "/globalchat";
    private static final String UPDATE_DEX_PATH = "/updatedex";
    private static final String STATS_PATH = "/stats";
    private static final String GET_TEAMS_PATH = "/getallbattleteams";
    private static final String UPDATE_TEAM_PATH = "/updatebattleteam";
    private static final String REGIONS_PATH = "/regions";
    private static final String KARTS_LEADERBOARD_PATH = "/karts/leaderboard";
    private static final String KARTS_STATUS_PATH = "/karts/status";
    private static final String TAXI_STOPS_PATH = "/taxi/stops";
    private static final String TAXI_TELEPORT_PATH = "/taxi/teleport";
    private static final String MESSAGE_PATH = "/message";
    private static final String POSITION_PATH = "/position";
    private static final String BEARER_PREFIX = "Bearer ";
    /** Section sign, kept as an escape so the source stays ASCII. */
    private static final char SECTION = '\u00a7';

    /** The catalog walks every dialog, so it gets more room than a simple lookup. */
    private static final long CATALOG_TIMEOUT_SECONDS = 20;
    private static final long USER_TIMEOUT_SECONDS = 10;
    /** Under the backend's 10s axios timeout: past it the caller has given up while the row is spent. */
    private static final long GIVE_TIMEOUT_SECONDS = 8;
    /**
     * The server-thread half of a PC route, short because {@code /pc/move} is a <b>swap</b> and so
     * not idempotent: past the backend's 10s axios timeout the user has been told it failed while it
     * still lands, and redoing it swaps the slots straight back. With the provider's load budget this
     * stays under that 10s. The work is an array walk (serialising happens off-thread), so the wait
     * is queue latency, not compute.
     */
    private static final long STORAGE_TIMEOUT_SECONDS = 5;
    private static final long WEATHER_TIMEOUT_SECONDS = 5;
    private static final int TICKS_PER_DAY = 24_000;
    /** Extra wait for a give that had already started when {@link #GIVE_TIMEOUT_SECONDS} expired. */
    private static final long GIVE_GRACE_SECONDS = 2;
    private static final int SHUTDOWN_DELAY_SECONDS = 1;

    private static final Gson GSON = new Gson();

    /** Locale-fixed: a comma decimal separator would not survive the page's Number(). */
    private static final DecimalFormat TPS_FORMAT =
            new DecimalFormat("##.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private static HttpServer server;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!TerasConfig.isHttpEnabled()) {
            return;
        }
        MinecraftServer mc = event.getServer();
        try {
            server = HttpServer.create(
                    new InetSocketAddress(TerasConfig.getHttpBind(), TerasConfig.getHttpPort()), 0);
            // Unauthenticated liveness probe: answers on the HTTP thread without touching game state, so
            // it confirms the socket is bound even while the server thread is stalled (when a game-state
            // read would 503). No auth so it's a pure "is this port reachable" check.
            server.createContext(PING_PATH, TerasHttpServer::handlePing);
            // A single "/quests" context: HttpServer matches by longest prefix, so this catches both
            // routes and anything else under /quests (which 404s) — the same shape Wungill used.
            server.createContext("/quests", exchange -> handleQuests(exchange, mc));
            // Separate contexts, not one "/give": HttpServer matches by longest prefix, so a shared
            // prefix would also swallow /givefoo. Anything unrouted 404s — which /takepokemon and
            // /takeitems RELY on: the backend's ATOMIC custody path uses their 404 to roll an order
            // back and charge nothing. The game has givePokemon but no takePokemon, so shipping them
            // would silently arm WIGGLYPOP_ATOMIC_CUSTODY and duplicate the mon. Do not add them here
            // without reviewing that flag in the same change.
            server.createContext(GIVE_POKEMON_PATH, exchange -> handleGivePokemon(exchange, mc));
            server.createContext(GIVE_ITEMS_PATH, exchange -> handleGiveItems(exchange, mc));
            // Economy bridge: the backend calls these after a starbank-side change (updateBalance) and
            // on trainer defeat (getCurrentBalance). EconomyStore is engine-free and thread-safe, so
            // they register unconditionally and serve on the HTTP thread with no server-thread hop.
            server.createContext(UPDATE_BALANCE_PATH, TerasHttpServer::handleUpdateBalance);
            server.createContext(GET_CURRENT_BALANCE_PATH, TerasHttpServer::handleGetCurrentBalance);
            server.createContext(MONEY_PATH, TerasHttpServer::handleMoney);
            // /pc/move needs its own context: HttpServer matches by longest prefix, so under /pc
            // alone it would be handled as a read. Each still rejects the sub-paths its own prefix
            // swallows — see respondFromStorage.
            server.createContext(PC_PATH, exchange -> handlePc(exchange, mc));
            server.createContext(PC_MOVE_PATH, exchange -> handlePcMove(exchange, mc));
            server.createContext(PARTY_PATH, exchange -> handleParty(exchange, mc));
            server.createContext(WEATHER_PATH, exchange -> handleWeather(exchange, mc));
            server.createContext(PERFORMANCE_PATH, exchange -> handlePerformance(exchange, mc));
            server.createContext(GLOBAL_CHAT_PATH, exchange -> handleGlobalChat(exchange, mc));
            server.createContext(UPDATE_DEX_PATH, exchange -> handleUpdateDex(exchange, mc));
            server.createContext(STATS_PATH, exchange -> handleStats(exchange, mc));
            server.createContext(GET_TEAMS_PATH, exchange -> handleGetTeams(exchange, mc));
            server.createContext(UPDATE_TEAM_PATH, exchange -> handleUpdateTeam(exchange, mc));
            // Regions serve from RegionStore's immutable snapshot (published on the server thread,
            // warmed before this server starts), so no server-thread hop is needed — the same
            // rationale as the economy routes.
            server.createContext(REGIONS_PATH, TerasHttpServer::handleRegions);
            server.createContext(KARTS_LEADERBOARD_PATH, TerasHttpServer::handleKartsLeaderboard);
            server.createContext(KARTS_STATUS_PATH, TerasHttpServer::handleKartsStatus);
            // Stops serve from TaxiStore's immutable snapshot, so no server-thread hop — same
            // rationale as /regions, and it matters because the taxi page polls this list.
            server.createContext(TAXI_STOPS_PATH, TerasHttpServer::handleTaxiStops);
            server.createContext(TAXI_TELEPORT_PATH, exchange -> handleTaxiTeleport(exchange, mc));
            server.createContext(MESSAGE_PATH, exchange -> handleMessage(exchange, mc));
            server.createContext(POSITION_PATH, exchange -> handlePosition(exchange, mc));
            server.setExecutor(Teras.EXECUTOR);
            server.start();

            Teras.LOGGER.info("Teras HTTP API listening on {}:{} (GET /ping, GET /quests/all, "
                            + "GET /quests/user/{{uuid}}, POST /givepokemon, POST /giveitems, "
                            + "POST /updateBalance, POST /getCurrentBalance, POST /money, "
                            + "POST /pc, POST /pc/move, POST /equipo, GET /weather, "
                            + "GET /performance, POST /globalchat, POST /updatedex, "
                            + "POST /getallbattleteams, POST /updatebattleteam, POST /stats, "
                            + "GET /regions, GET /taxi/stops, POST /taxi/teleport, POST /message, "
                            + "POST /position)",
                    TerasConfig.getHttpBind(), TerasConfig.getHttpPort());
            warnAboutExposure();
        } catch (IOException e) {
            Teras.LOGGER.error("Failed to start the Teras HTTP API on {}:{}",
                    TerasConfig.getHttpBind(), TerasConfig.getHttpPort(), e);
            server = null;
        }
    }

    private static void warnAboutExposure() {
        boolean open = TerasConfig.getHttpToken().isBlank();
        if (open) {
            Teras.LOGGER.warn("Teras HTTP API has no 'httpToken' — requests are UNAUTHENTICATED "
                    + "(this matches the old Wungill API, so the SmartRotom backend works as-is). "
                    + "Set 'httpToken' in config/teras/config.yml to require a bearer token.");
        }
        if (TerasConfig.isHttpBindPublic()) {
            Teras.LOGGER.warn("Teras HTTP API is bound to '{}', reachable off this machine, over "
                            + "plaintext HTTP{}. Restrict it by firewall and/or front it with a TLS proxy.",
                    TerasConfig.getHttpBind(), open ? " AND WITHOUT AUTHENTICATION" : "");
        }
        if (open && TerasConfig.isHttpBindPublic()) {
            Teras.LOGGER.warn("SECURITY: POST /givepokemon and /giveitems are UNAUTHENTICATED on a "
                    + "public bind. Anyone who can reach {}:{} can grant any player any item or "
                    + "Pokémon. Firewall this port to the backend only.",
                    TerasConfig.getHttpBind(), TerasConfig.getHttpPort());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (server == null) {
            return;
        }
        server.stop(SHUTDOWN_DELAY_SECONDS);
        server = null;
        Teras.LOGGER.info("Teras HTTP API stopped");
    }

    // ---- Routing ----

    private static void handlePing(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, error("Method not allowed"));
            return;
        }
        respond(exchange, 200, "{\"success\":true,\"message\":\"pong\",\"data\":null}");
    }

    private static void handleQuests(HttpExchange exchange, MinecraftServer mc) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, error("Method not allowed"));
                return;
            }
            if (!isAuthorized(exchange)) {
                // No detail: an unauthorized caller learns nothing about what exists here.
                respond(exchange, 401, error("Unauthorized"));
                return;
            }
            if (!es.boffmedia.teras.quests.QuestBridge.isAvailable()) {
                respond(exchange, 503, error("Quest system unavailable (CustomNPCs not installed)"));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (QUESTS_ALL_PATH.equals(path)) {
                respond(exchange, 200, onServerThread(mc,
                        es.boffmedia.teras.quests.QuestJson::catalogJson, CATALOG_TIMEOUT_SECONDS));
                return;
            }

            UUID uuid = parseUuidFromPath(path);
            if (uuid == null) {
                respond(exchange, 400, error("Malformed player uuid"));
                return;
            }
            Optional<String> json = onServerThread(mc,
                    () -> es.boffmedia.teras.quests.QuestJson.progressJson(uuid), USER_TIMEOUT_SECONDS);
            if (json.isEmpty()) {
                // Unknown to this server: never logged in, so there is no saved progress to read.
                respond(exchange, 404, error("Player not found"));
                return;
            }
            respond(exchange, 200, json.get());
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    private static void handleGivePokemon(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            GiveRequests.PokemonGive req = GiveRequests.parsePokemon(readBody(exchange));
            boolean given = onServerThreadOrAbandon(mc,
                    () -> es.boffmedia.teras.http.GiveService.givePokemon(mc, req),
                    GIVE_TIMEOUT_SECONDS, false);
            if (!given) {
                Teras.LOGGER.error("givePokemon SPENT AND LOST for {}: spec '{}' was not delivered "
                        + "(offline, unparseable, or no engine)", req.uuid(), req.pokespec());
                respond(exchange, 422, error("Pokémon not delivered"));
                return;
            }
            respond(exchange, 200, "{\"data\":{\"given\":true}}");
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    private static void handleGiveItems(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            GiveRequests.ItemsGive req = GiveRequests.parseItems(readBody(exchange));
            int given = onServerThreadOrAbandon(mc,
                    () -> es.boffmedia.teras.http.GiveService.giveItems(mc, req),
                    GIVE_TIMEOUT_SECONDS, -1);
            if (given < 0) {
                Teras.LOGGER.error("giveItems SPENT AND LOST for {}: {} was not delivered (player offline)",
                        req.uuid(), req.items());
                respond(exchange, 422, error("Player not online"));
                return;
            }
            respond(exchange, 200, "{\"data\":{\"given\":" + given + "}}");
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /**
     * The backend pushes an authoritative balance here after a starbank-side change the game did not
     * originate (a web transfer, an admin set, another player paying you); {@link EconomyStore#accept}
     * mirrors it into the cache so the in-game balance reflects it without a re-login. Mirror only — the
     * ledger write already happened on the backend. Body {@code {balance, type, uuid}} (type ignored).
     */
    private static void handleUpdateBalance(HttpExchange exchange) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            JsonObject body = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            UUID uuid = UUID.fromString(body.get("uuid").getAsString());
            BigDecimal balance = body.get("balance").getAsBigDecimal();
            EconomyStore.accept(uuid, balance);
            Teras.LOGGER.info("updateBalance: mirrored {} -> {}", uuid, balance);
            respond(exchange, 200, "{\"data\":{\"success\":true}}");
        } catch (RuntimeException e) {
            respond(exchange, 400, error("Malformed updateBalance body"));
        }
    }

    /**
     * Trainer-defeat flow: add {@code amount} to the player's balance and return the new total. The
     * backend diffs the returned value against the starbank balance and ledgers the difference, so this
     * must return the <b>post-credit</b> balance. An unloaded balance answers 409 (not {@code amount})
     * so the backend falls back to its own stored balance rather than overwriting with just the reward.
     * Body {@code {uuid, amount}}.
     */
    private static void handleGetCurrentBalance(HttpExchange exchange) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            JsonObject body = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            UUID uuid = UUID.fromString(body.get("uuid").getAsString());
            BigDecimal amount = (body.has("amount") && !body.get("amount").isJsonNull())
                    ? body.get("amount").getAsBigDecimal() : BigDecimal.ZERO;
            if (!EconomyStore.isLoaded(uuid)) {
                respond(exchange, 409, error("Balance not loaded"));
                return;
            }
            if (amount.signum() > 0) {
                // mirrorDeposit, not deposit: the backend ledgers this credit itself — the funnel
                // would report it back and double-count.
                EconomyStore.mirrorDeposit(uuid, amount);
            }
            respond(exchange, 200, "{\"data\":" + EconomyStore.get(uuid).toPlainString() + "}");
        } catch (RuntimeException e) {
            respond(exchange, 400, error("Malformed getCurrentBalance body"));
        }
    }

    /**
     * Balance read: returns {@code {money}} for a player. Pure cache read, no mutation; an unknown
     * balance reads as 0. Body {@code {uuid}}.
     */
    private static void handleMoney(HttpExchange exchange) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            JsonObject body = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            UUID uuid = UUID.fromString(body.get("uuid").getAsString());
            respond(exchange, 200,
                    "{\"data\":{\"money\":" + EconomyStore.get(uuid).toPlainString() + "}}");
        } catch (RuntimeException e) {
            respond(exchange, 400, error("Malformed money body"));
        }
    }

    /**
     * Overworld weather and time-of-day for the SmartRotom clock. GET with no body, as 1.16.5 served
     * it. {@code changeTime} is the time-of-day the weather flips at.
     */
    private static void handleWeather(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, error("Method not allowed"));
            return;
        }
        if (!isAuthorized(exchange)) {
            respond(exchange, 401, error("Unauthorized"));
            return;
        }
        try {
            Weather weather = onServerThread(mc, () -> readWeather(mc), WEATHER_TIMEOUT_SECONDS);
            respond(exchange, 200, data(GSON.toJson(weather)));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        }
    }

    /** The backend's {@code Weather} entity. */
    private record Weather(String weather, long changeTime, long minecraftTime) {}

    private static Weather readWeather(MinecraftServer mc) {
        ServerLevel level = mc.overworld(); // 1.16.5 read Bukkit's world 0; the clock is server-wide
        String state = level.isRaining() ? (level.isThundering() ? "thunder" : "rain") : "clear";
        long time = Math.floorMod(level.getDayTime(), TICKS_PER_DAY);
        // getRainTime counts down to the next flip either way; vanilla's getWeatherDuration.
        int untilChange = level.getLevelData() instanceof ServerLevelData data ? data.getRainTime() : 0;
        return new Weather(state, Math.floorMod(time + untilChange, TICKS_PER_DAY), time);
    }


    /** Server health for the SmartRotom dashboard. GET with no body, as 1.16.5 served it. */
    private static void handlePerformance(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginRead(exchange)) {
            return;
        }
        try {
            respond(exchange, 200, data(GSON.toJson(
                    onServerThread(mc, () -> readPerformance(mc), WEATHER_TIMEOUT_SECONDS))));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        }
    }

    /** {@code tps} is a string and {@code memory} a percentage — 1.16.5's shapes, which the page binds. */
    private record Performance(String tps, int players, double memory, String uptime) {}

    private static Performance readPerformance(MinecraftServer mc) {
        double msPerTick = mc.getAverageTickTimeNanos() / 1_000_000.0;
        // A tick that finishes early still costs a full 50ms of wall clock, so the server cannot
        // exceed 20 TPS however fast it runs.
        double tps = Math.min(20.0, 1000.0 / Math.max(50.0, msPerTick));
        Runtime runtime = Runtime.getRuntime();
        double memory = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100;
        return new Performance(TPS_FORMAT.format(tps), mc.getPlayerList().getPlayerCount(), memory,
                uptime(System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()));
    }

    private static String uptime(long millis) {
        long days = millis / 86_400_000;
        millis %= 86_400_000;
        long hours = millis / 3_600_000;
        millis %= 3_600_000;
        return days + "d " + hours + "h " + millis / 60_000 + "m";
    }

    /**
     * Broadcasts a SmartRotom chat message in-game, as {@code [name]: message} with colour codes.
     *
     * <p>The message is player-typed and goes to everyone, so section signs are stripped: 1.16.5 let
     * them through, which let anyone colour, bold or obfuscate global chat. The name is the server's,
     * not the caller's, so it needs no such treatment.</p>
     */
    private static void handleGlobalChat(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            JsonObject body = JsonBody.object(readBody(exchange));
            UUID uuid = JsonBody.uuid(body);
            String message = JsonBody.string(body, "message");
            if (message == null || message.isBlank()) {
                respond(exchange, 400, error("'message' is required"));
                return;
            }
            String clean = message.replace(SECTION, ' ');
            onServerThread(mc, () -> {
                ServerPlayer sender = mc.getPlayerList().getPlayer(uuid);
                String name = sender != null ? sender.getGameProfile().getName() : "Unknown";
                MessageHelper.enviarMensajeGlobal(mc, String.format("%1$s7[%1$s6%2$s%1$s7]: %1$sr%3$s",
                        SECTION, name, clean));
                return true;
            }, WEATHER_TIMEOUT_SECONDS);
            respond(exchange, 200, "{\"data\":{\"sent\":true}}");
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        }
    }

    /**
     * A private message to one player — the backend's "whisper someone in-game" path, used by the
     * web and ficusai. Same body as {@link #handleGlobalChat}, one recipient.
     */
    private static void handleMessage(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            JsonObject body = JsonBody.object(readBody(exchange));
            UUID uuid = JsonBody.uuid(body);
            String message = JsonBody.string(body, "message");
            if (message == null || message.isBlank()) {
                respond(exchange, 400, error("'message' is required"));
                return;
            }
            String clean = message.replace(SECTION, ' ');
            Boolean sent = onServerThread(mc, () -> {
                ServerPlayer player = mc.getPlayerList().getPlayer(uuid);
                if (player == null) {
                    return false;
                }
                MessageHelper.enviarMensaje(player, clean);
                return true;
            }, WEATHER_TIMEOUT_SECONDS);
            if (!Boolean.TRUE.equals(sent)) {
                // 422 rather than 404: the same answer /giveitems gives for an offline player, so a
                // caller can treat "the player is not here" identically across every route.
                respond(exchange, 422, error("Player not online"));
                return;
            }
            respond(exchange, 200, "{\"data\":{\"sent\":true}}");
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        }
    }

    /**
     * Where a player is standing right now.
     *
     * <p>The only server-side source of a player's coordinates. The web has its own through the
     * MCEF bridge, but nothing the backend can reach — and the backend needs them twice: to price
     * a taxi fare from the player's real position rather than one the browser reports, and to
     * settle a teleport the mod could not confirm by reading back where the player ended up
     * (docs/TAXI.md).</p>
     *
     * <p>Offline is a <b>200 with {@code online:false}</b>, not a 404: to every caller it is an
     * ordinary state of the world, not a failure of the request.</p>
     */
    private static void handlePosition(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            JsonObject body = JsonBody.object(readBody(exchange));
            UUID uuid = JsonBody.uuid(body);
            JsonObject position = onServerThread(mc, () -> {
                ServerPlayer player = mc.getPlayerList().getPlayer(uuid);
                JsonObject json = new JsonObject();
                if (player == null) {
                    json.addProperty("online", false);
                    return json;
                }
                json.addProperty("online", true);
                json.addProperty("x", player.getX());
                json.addProperty("y", player.getY());
                json.addProperty("z", player.getZ());
                json.addProperty("dimension", player.level().dimension().location().toString());
                return json;
            }, WEATHER_TIMEOUT_SECONDS);
            respond(exchange, 200, data(GSON.toJson(position)));
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        }
    }

    /**
     * The taxi's destinations. Served straight from {@code TaxiStore}'s snapshot — no server-thread
     * hop, because the SmartRotom taxi page polls this while the player walks around.
     */
    private static void handleTaxiStops(HttpExchange exchange) throws IOException {
        if (!beginRead(exchange)) {
            return;
        }
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (es.boffmedia.teras.taxi.TaxiStop stop : es.boffmedia.teras.taxi.TaxiStore.all()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", stop.id());
            json.addProperty("x", stop.x());
            json.addProperty("y", stop.y());
            json.addProperty("z", stop.z());
            // Stops are overworld-only by rule (docs/TAXI.md), so this is a constant. It is sent
            // because the backend's TaxiStop entity declares it; the taxi page never reads it.
            json.addProperty("world", "minecraft:overworld");
            array.add(json);
        }
        respond(exchange, 200, data(GSON.toJson(array)));
    }

    /**
     * Moves a player to a stop.
     *
     * <p><b>The caller charges the fare afterwards</b>, and only on an answer it can trust — the
     * backend teleports first and bills second (docs/TAXI.md). That inverts what this route used to
     * assume, and it is what makes the two properties below load-bearing rather than merely tidy:</p>
     *
     * <ul>
     *   <li><b>All-or-nothing.</b> Nothing is touched until the stop, the player and the arrival are
     *       all known good, so a refusal means the player did not move and must not be charged.</li>
     *   <li><b>Distinguishable failures.</b> Each one gets its own status, and the two that share
     *       409 carry a {@code code}, because the backend turns them into different sentences for
     *       the player. 503 is the only ambiguous answer: it means we cannot vouch either way, and
     *       the backend resolves it by reading the player's position back through {@code /position}.</li>
     * </ul>
     */
    private static void handleTaxiTeleport(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            JsonObject body = JsonBody.object(readBody(exchange));
            UUID uuid = JsonBody.uuid(body);
            String id = JsonBody.string(body, "id");
            if (id == null || id.isBlank()) {
                respond(exchange, 400, error("'id' is required"));
                return;
            }
            es.boffmedia.teras.taxi.TaxiStop stop = es.boffmedia.teras.taxi.TaxiStore.find(id);
            if (stop == null) {
                respond(exchange, 404, error("Unknown taxi stop"));
                return;
            }
            es.boffmedia.teras.taxi.TaxiTeleport.Result result = onServerThreadOrAbandon(mc,
                    () -> es.boffmedia.teras.taxi.TaxiTeleport.travel(
                            mc.getPlayerList().getPlayer(uuid), stop),
                    GIVE_TIMEOUT_SECONDS, null);
            if (result == null) {
                // Abandoned or timed out: we do not know whether it ran, so say so rather than
                // reporting a trip we cannot vouch for.
                Teras.LOGGER.warn("taxi/teleport for {} to '{}' did not complete in time", uuid, stop.id());
                respond(exchange, 503, error("Server busy"));
                return;
            }
            switch (result) {
                case OK -> respond(exchange, 200, "{\"data\":{\"teleported\":true}}");
                case OFFLINE -> respond(exchange, 422, error("Player not online"));
                case UNSAFE -> {
                    // An error, not a warning: a stop nobody can arrive at is broken content, and
                    // it will keep refusing every passenger until an admin moves it.
                    Teras.LOGGER.error("taxi/teleport: stop '{}' has no safe arrival; refusing to "
                            + "drop {} into it", stop.id(), uuid);
                    respond(exchange, 409, error("No safe arrival at that stop", "unsafe_arrival"));
                }
                case BUSY -> respond(exchange, 409,
                        error("Player is in a dungeon run", "in_dungeon_run"));
            }
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        }
    }

    /**
     * The player's whole Pokédex, for the backend's bulk resync. The day-to-day path is the push in
     * {@code dex.*.*DexSync}; this is the backfill that reconciles it.
     */
    private static void handleUpdateDex(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        DexProvider provider = DexProviders.get();
        if (provider == null) {
            respond(exchange, 503, error("Pokédex unavailable (no supported engine)"));
            return;
        }
        UUID uuid;
        try {
            uuid = JsonBody.uuid(JsonBody.object(readBody(exchange)));
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
            return;
        }
        try {
            // Off the server thread, like the PC routes: the dex load may be scheduled onto it.
            DexSnapshot snapshot = provider.readAll(mc, uuid);
            if (snapshot == null) {
                respond(exchange, 404, error("Player not found"));
                return;
            }
            respond(exchange, 200, data(GSON.toJson(snapshot)));
        } catch (TimeoutException e) {
            Teras.LOGGER.warn("Teras HTTP API: timed out reading the Pokédex for {}", uuid);
            respond(exchange, 503, error("Pokédex did not load in time"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            respond(exchange, 503, error("Server is shutting down"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: failed to read the Pokédex for {}", uuid, e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /** Shared GET preamble, mirroring {@link #beginWrite}. Returns false if already handled. */
    private static boolean beginRead(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, error("Method not allowed"));
            return false;
        }
        if (!isAuthorized(exchange)) {
            respond(exchange, 401, error("Unauthorized"));
            return false;
        }
        return true;
    }



    /**
     * The player's vanilla Minecraft statistics, straight from {@code world/stats/<uuid>.json}.
     *
     * <p>An online player's counters are flushed first — vanilla only writes them periodically, so
     * the file alone can be minutes behind the session being looked at. See
     * {@link PlayerStatsService} for the shape, and for why it is not inlined here.</p>
     */
    private static void handleStats(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        try {
            UUID uuid = JsonBody.uuid(JsonBody.object(readBody(exchange)));
            onServerThread(mc, () -> {
                PlayerStatsService.flush(mc, uuid);
                return true;
            }, WEATHER_TIMEOUT_SECONDS);
            String stats = PlayerStatsService.read(mc, uuid);
            if (stats == null) {
                respond(exchange, 404, error("No statistics for that player"));
                return;
            }
            respond(exchange, 200, data(stats));
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    // ---- Battle teams ----

    /**
     * The player's saved battle teams, for the PC's teams panel.
     *
     * <p>Wrapped as {@code {teams, maxTeams}}, not the bare name-to-team map 1.16.5 sent: the panel
     * reads {@code data.teams}, and the backend passes this response through untouched, so the flat
     * map arrived as {@code undefined} and the panel rendered "no saved teams" whatever the player
     * had.</p>
     */
    private static void handleGetTeams(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        TeamProvider provider = TeamProviders.get();
        if (provider == null) {
            respond(exchange, 503, error("Battle teams unavailable (no supported engine)"));
            return;
        }
        try {
            UUID uuid = JsonBody.uuid(JsonBody.object(readBody(exchange)));
            List<BattleTeam> teams = provider.readAll(mc, uuid);
            respond(exchange, 200, data(GSON.toJson(new BattleTeams(teams, MAX_BATTLE_TEAMS))));
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (TimeoutException e) {
            respond(exchange, 503, error("Battle teams did not load in time"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            respond(exchange, 503, error("Server is shutting down"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /** The panel's {@code BattleTeamData}. */
    private record BattleTeams(List<BattleTeam> teams, int maxTeams) {}

    /** No engine limit exists; the number is the panel's display cap, matching a PC box row. */
    private static final int MAX_BATTLE_TEAMS = 6;

    /**
     * Copies one Pokémon from the party or a PC box into a team slot.
     *
     * <p>No live caller yet — the PC's teams panel is read-only pending its own API controller — but
     * the backend already routes {@code battleteams/update} here, so the pair is complete.</p>
     */
    private static void handleUpdateTeam(HttpExchange exchange, MinecraftServer mc) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        TeamProvider provider = TeamProviders.get();
        if (provider == null) {
            respond(exchange, 503, error("Battle teams unavailable (no supported engine)"));
            return;
        }
        try {
            JsonObject body = JsonBody.object(readBody(exchange));
            UUID uuid = JsonBody.uuid(body);
            String name = JsonBody.string(body, "name");
            JsonObject source = body.getAsJsonObject("pokemon");
            if (source == null) {
                respond(exchange, 400, error("'pokemon' is required"));
                return;
            }
            boolean updated = provider.updateSlot(mc, uuid,
                    name == null || name.isBlank() ? DEFAULT_TEAM_NAME : name.trim(),
                    JsonBody.integer(body, "teamSlot"),
                    JsonBody.integer(source, "box"),
                    JsonBody.integer(source, "slot"));
            if (!updated) {
                respond(exchange, 409, error("No Pokémon at that slot"));
                return;
            }
            respond(exchange, 200, "{\"data\":{\"updated\":true}}");
        } catch (JsonBody.BadRequest | IllegalArgumentException e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (TimeoutException e) {
            respond(exchange, 503, error("Battle teams did not load in time"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            respond(exchange, 503, error("Server is shutting down"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /** 1.16.5's fallback when the caller names no team. */
    private static final String DEFAULT_TEAM_NAME = "battleteam";

    // ---- SmartRotom PC ----
    // The three routes that superseded openPC: rather than push the player into the in-game PC
    // screen, the SmartRotom reads storage here and writes moves back. They work offline, which is
    // the point. respondFromStorage carries the threading.

    private static void handlePc(HttpExchange exchange, MinecraftServer mc) throws IOException {
        respondFromStorage(exchange, mc, PC_PATH, StorageRequests::parseUuid,
                (session, req) -> session.readPc());
    }

    private static void handleParty(HttpExchange exchange, MinecraftServer mc) throws IOException {
        respondFromStorage(exchange, mc, PARTY_PATH, StorageRequests::parseUuid,
                (session, req) -> session.readParty());
    }

    /** The PC's only write. A refused swap is 409, not 500: the client's view has simply drifted. */
    private static void handlePcMove(HttpExchange exchange, MinecraftServer mc) throws IOException {
        respondFromStorage(exchange, mc, PC_MOVE_PATH, StorageRequests::parseMove, (session, move) -> {
            boolean moved = session.swap(move.sourceBox(), move.sourceIndex(),
                    move.destinationBox(), move.destinationIndex());
            if (!moved) {
                Teras.LOGGER.warn("pc/move refused for {}: [{},{}] <-> [{},{}]", move.uuid(),
                        move.sourceBox(), move.sourceIndex(),
                        move.destinationBox(), move.destinationIndex());
                return null;
            }
            return Map.of("moved", true);
        });
    }

    /**
     * A PC route, in the order the threads require.
     *
     * <ol>
     *   <li>Reject a non-POST, an unauthorized caller, or a path this context merely swallowed by
     *       prefix ({@code /pcfoo}, {@code /equipo/bar}). Path checked after auth, so an
     *       unauthorized caller learns nothing about what exists here.</li>
     *   <li>Parse on this thread, so a malformed body 400s before anything else.</li>
     *   <li>{@link StorageProvider#open} <b>here, not on the server thread</b> — the engine may
     *       schedule the load onto that thread, and waiting there would deadlock.</li>
     *   <li>Touch storage on the server thread, and only that: serialising a 900-Pokémon PC is CPU
     *       on a model nobody can see yet, so it happens back here rather than holding the tick.</li>
     * </ol>
     *
     * <p>A {@code null} from {@code work} means the game declined: 409, not 500.</p>
     */
    private static <T extends StorageRequests.Request> void respondFromStorage(
            HttpExchange exchange, MinecraftServer mc, String exactPath, Function<String, T> parse,
            BiFunction<StorageSession, T, Object> work) throws IOException {
        if (!beginWrite(exchange)) {
            return;
        }
        if (!exactPath.equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, error("Not found"));
            return;
        }
        StorageProvider provider = StorageProviders.get();
        if (provider == null) {
            respond(exchange, 503, error("Pokémon storage unavailable (no supported engine)"));
            return;
        }
        T request;
        try {
            request = parse.apply(readBody(exchange));
        } catch (JsonBody.BadRequest e) {
            respond(exchange, 400, error(e.getMessage()));
            return;
        }

        StorageSession session;
        try {
            session = provider.open(request.uuid());
        } catch (TimeoutException e) {
            Teras.LOGGER.warn("Teras HTTP API: timed out loading storage for {}", request.uuid());
            respond(exchange, 503, error("Storage did not load in time"));
            return;
        } catch (InterruptedException e) {
            // Re-arm so the next blocking call on this pooled thread still unwinds.
            Thread.currentThread().interrupt();
            respond(exchange, 503, error("Server is shutting down"));
            return;
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: failed to load storage for {}", request.uuid(), e);
            respond(exchange, 500, error("Internal error"));
            return;
        }
        if (session == null) {
            // Never logged in here, so there is nothing to show or move.
            respond(exchange, 404, error("Player not found"));
            return;
        }

        try {
            Object result = onServerThread(mc, () -> work.apply(session, request),
                    STORAGE_TIMEOUT_SECONDS);
            if (result == null) {
                respond(exchange, 409, error("Slot is out of range or unavailable"));
                return;
            }
            respond(exchange, 200, data(GSON.toJson(result)));
        } catch (IllegalStateException e) {
            Teras.LOGGER.warn("Teras HTTP API: {}", e.getMessage());
            respond(exchange, 503, error("Server busy"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /** Shared POST preamble: rejects non-POST (405) and unauthorized (401). Returns false if handled. */
    /**
     * The full region catalog in the legacy web shape (bare array; cuboid regions synthesize their
     * four XZ corners into {@code points}). Serves the immutable snapshot, so no server-thread hop:
     * mutations republish it atomically and {@link es.boffmedia.teras.region.RegionTracker} warms it
     * before this server binds.
     */
    private static void handleRegions(HttpExchange exchange) throws IOException {
        if (!beginRead(exchange)) {
            return;
        }
        try {
            respond(exchange, 200, es.boffmedia.teras.region.RegionJson.toWebArray(
                    es.boffmedia.teras.region.RegionStore.all().values()));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /**
     * Circuit records. {@code ?track=<nombre>} for one circuit, otherwise every circuit that has
     * any; {@code ?limit=<n>} caps the rows per table.
     *
     * <p>Served straight from the leaderboard store with no server-thread hop: records only change
     * when a race finishes, on the server thread, and the read is of an already-sorted list.</p>
     */
    private static void handleKartsLeaderboard(HttpExchange exchange) throws IOException {
        if (!beginRead(exchange)) {
            return;
        }
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            int limit = parsePositiveInt(query.get("limit"), es.boffmedia.teras.karts.KartsConfig.leaderboardTopN());
            String track = query.get("track");
            respond(exchange, 200, track == null || track.isBlank()
                    ? es.boffmedia.teras.karts.http.KartsJsonView.leaderboardIndex(limit).toString()
                    : es.boffmedia.teras.karts.http.KartsJsonView.leaderboard(track, limit).toString());
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /**
     * Circuits and any race currently running. Hops to the server thread: live races are mutated
     * every tick, so reading them off the HTTP pool would race with the engine.
     */
    private static void handleKartsStatus(HttpExchange exchange) throws IOException {
        if (!beginRead(exchange)) {
            return;
        }
        MinecraftServer mc = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (mc == null) {
            respond(exchange, 503, error("Server not ready"));
            return;
        }
        try {
            respond(exchange, 200, onServerThread(mc, () -> es.boffmedia.teras.karts.http.KartsJsonView.status().toString(), 5));
        } catch (IllegalStateException e) {
            // onServerThread reports a timed-out or interrupted hop this way.
            respond(exchange, 503, error("Server busy"));
        } catch (Exception e) {
            Teras.LOGGER.error("Teras HTTP API: unhandled error on {}", exchange.getRequestURI(), e);
            respond(exchange, 500, error("Internal error"));
        }
    }

    /** Splits a raw query string into decoded key/value pairs. */
    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> parsed = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return parsed;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            parsed.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
        return parsed;
    }

    /** A positive integer parameter, falling back to {@code fallback} for anything unusable. */
    static int parsePositiveInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean beginWrite(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, error("Method not allowed"));
            return false;
        }
        if (!isAuthorized(exchange)) {
            respond(exchange, 401, error("Unauthorized"));
            return false;
        }
        return true;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Runs {@code work} on the server thread and waits. Quest state is game state; reading it from an
     * HTTP thread would race the tick loop.
     */
    private static <T> T onServerThread(MinecraftServer mc, Supplier<T> work, long timeoutSeconds) {
        try {
            return mc.submit(work).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for the server thread", e);
        } catch (Exception e) {
            throw new IllegalStateException("Timed out waiting for the server thread", e);
        }
    }

    /**
     * Like {@link #onServerThread}, but the work is <b>abandoned</b> rather than merely un-awaited if it
     * has not started by the time the wait expires.
     *
     * <p>Only correct for the give routes, and required there. {@code mc.submit} queues the work; a
     * timeout ends our wait but not the task, so a plain {@code onServerThread} answers 503 and then
     * hands over the item anyway. The backend rolls the spend back on that 503 (its axios timeout is
     * 10s, ours is {@value #GIVE_TIMEOUT_SECONDS}), so the player keeps a free item.</p>
     *
     * <p>{@code claim} decides who acts: if we win it the work no-ops and 503 is truthful; if the work
     * already won it, the give is happening and only its real result can be reported, so we wait out a
     * short grace period for it. A give that outlasts even that still answers 503 having possibly
     * delivered — closing that last window needs an idempotency key threaded through to the backend,
     * which is tracked separately.</p>
     */
    private static <T> T onServerThreadOrAbandon(MinecraftServer mc, Supplier<T> work,
                                                 long timeoutSeconds, T abandoned) {
        AtomicBoolean claim = new AtomicBoolean();
        Future<T> pending = mc.submit(() -> claim.compareAndSet(false, true) ? work.get() : abandoned);
        try {
            return pending.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            claim.set(true);
            throw new IllegalStateException("Interrupted waiting for the server thread", e);
        } catch (TimeoutException e) {
            if (claim.compareAndSet(false, true)) {
                throw new IllegalStateException("Timed out waiting for the server thread", e);
            }
            try {
                return pending.get(GIVE_GRACE_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException grace) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted awaiting an in-flight give", grace);
            } catch (Exception grace) {
                throw new IllegalStateException("A give was in flight when the wait expired and did "
                        + "not report back; it may have been delivered", grace);
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException("The give failed on the server thread", e);
        }
    }

    // ---- Routing / auth primitives (package-private: unit-tested in TerasHttpServerTest) ----

    /**
     * The player uuid out of {@code /quests/user/{uuid}}, or {@code null} if the path isn't that shape
     * or the uuid is malformed. Rejects a trailing segment so {@code /quests/user/{uuid}/anything}
     * can't be mistaken for a bare uuid.
     */
    static UUID parseUuidFromPath(String path) {
        if (path == null || !path.startsWith(QUESTS_USER_PREFIX)) {
            return null;
        }
        String rawUuid = path.substring(QUESTS_USER_PREFIX.length());
        if (rawUuid.isEmpty() || rawUuid.contains("/")) {
            return null;
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isAuthorized(HttpExchange exchange) {
        return isTokenValid(exchange.getRequestHeaders().getFirst("Authorization"),
                TerasConfig.getHttpToken());
    }

    /**
     * Constant-time bearer check, so a wrong token can't be recovered by timing the comparison.
     *
     * <p>A blank {@code expected} means <b>no auth configured</b> and everything passes — Wungill's
     * API was unauthenticated and the backend sends no {@code Authorization} header, so requiring one
     * by default would 401 the entire live pipeline. The startup warning covers the exposure.</p>
     */
    static boolean isTokenValid(String authHeader, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] presented = authHeader.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expected.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Response helpers ----

    /** The {@code {data:…}} envelope the backend unwraps as {@code response.data.data}. */
    private static String data(String json) {
        return "{\"data\":" + json + "}";
    }

    private static String error(String message) {
        return "{\"success\":false,\"message\":\"" + message.replace("\"", "'") + "\",\"data\":null}";
    }

    /**
     * An error that also carries a machine-readable {@code code}.
     *
     * For the cases where one status covers two different things — the taxi serves both "no safe
     * arrival" and "the player is in a dungeon run" as 409 — and the caller has to tell them apart
     * to say anything useful to a player. The prose stays for logs; the code is what is branched on.
     */
    private static String error(String message, String code) {
        return "{\"success\":false,\"message\":\"" + message.replace("\"", "'")
                + "\",\"code\":\"" + code + "\",\"data\":null}";
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = exchange.getResponseBody()) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            out.write(bytes);
        } catch (IOException e) {
            Teras.LOGGER.warn("Teras HTTP API: failed writing response: {}", e.toString());
        }
    }
}
