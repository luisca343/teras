package es.boffmedia.teras.karts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.engine.RaceSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Karts tunables, in {@code config/teras/karts/config.json}. Same discipline as
 * {@link es.boffmedia.teras.util.TerasConfig}: defaults reset before every load, so a client's
 * integrated server never inherits the previous world's numbers, and each key is read defensively.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class KartsConfig {
    private KartsConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int minPlayers;
    private static int voteThresholdPct;
    private static int countdownSeconds;
    private static int raceTimeoutSeconds;
    private static int rankingIntervalTicks;
    private static int hudIntervalTicks;
    private static int dismountGraceSeconds;
    private static int wrongWayGraceSeconds;
    private static int checkpointHeight;
    private static int leaderboardTopN;
    private static boolean backendPostEnabled;
    private static Map<String, BigDecimal> payouts = new LinkedHashMap<>();
    private static int[] grandPrixPoints = {10, 8, 6, 4, 2, 1};

    static {
        resetToDefaults();
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        load();
    }

    public static void load() {
        resetToDefaults();
        Path path = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("karts").resolve("config.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(writeDefaults()));
                Teras.LOGGER.info("Karts: created default {}", path);
                return;
            }
            JsonObject json;
            try (Reader reader = Files.newBufferedReader(path)) {
                json = GSON.fromJson(reader, JsonObject.class);
            }
            if (json == null) {
                return;
            }
            if (has(json, "minJugadores")) minPlayers = json.get("minJugadores").getAsInt();
            if (has(json, "umbralVotosPct")) voteThresholdPct = json.get("umbralVotosPct").getAsInt();
            if (has(json, "cuentaAtrasSegundos")) countdownSeconds = json.get("cuentaAtrasSegundos").getAsInt();
            if (has(json, "timeoutCarreraSegundos")) raceTimeoutSeconds = json.get("timeoutCarreraSegundos").getAsInt();
            if (has(json, "rankingIntervalTicks")) rankingIntervalTicks = json.get("rankingIntervalTicks").getAsInt();
            if (has(json, "hudIntervalTicks")) hudIntervalTicks = json.get("hudIntervalTicks").getAsInt();
            if (has(json, "graciaDesmonteSegundos")) dismountGraceSeconds = json.get("graciaDesmonteSegundos").getAsInt();
            if (has(json, "graciaSentidoContrarioSegundos")) wrongWayGraceSeconds = json.get("graciaSentidoContrarioSegundos").getAsInt();
            if (has(json, "alturaCheckpoint")) checkpointHeight = json.get("alturaCheckpoint").getAsInt();
            if (has(json, "leaderboardTopN")) leaderboardTopN = json.get("leaderboardTopN").getAsInt();
            if (has(json, "backendPostEnabled")) backendPostEnabled = json.get("backendPostEnabled").getAsBoolean();
            if (has(json, "pagos")) readPayouts(json.getAsJsonObject("pagos"));
            if (has(json, "puntosGp")) readGrandPrixPoints(json.getAsJsonArray("puntosGp"));

            Teras.LOGGER.info("Karts: config loaded from {}", path);
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to load config, using defaults: {}", e.toString());
        }
    }

    private static void readPayouts(JsonObject json) {
        payouts = new LinkedHashMap<>();
        for (String key : json.keySet()) {
            try {
                payouts.put(key, json.get(key).getAsBigDecimal());
            } catch (Exception e) {
                Teras.LOGGER.warn("Karts: ignoring payout '{}': {}", key, e.toString());
            }
        }
    }

    private static void readGrandPrixPoints(com.google.gson.JsonArray array) {
        int[] points = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            points[i] = array.get(i).getAsInt();
        }
        grandPrixPoints = points;
    }

    private static void resetToDefaults() {
        minPlayers = 2;
        voteThresholdPct = 60;
        countdownSeconds = 3;
        raceTimeoutSeconds = 600;
        rankingIntervalTicks = 10;
        hudIntervalTicks = 20;
        dismountGraceSeconds = 5;
        wrongWayGraceSeconds = 3;
        checkpointHeight = 4;
        leaderboardTopN = 10;
        backendPostEnabled = true;
        payouts = new LinkedHashMap<>();
        payouts.put("1", BigDecimal.valueOf(1000));
        payouts.put("2", BigDecimal.valueOf(500));
        payouts.put("3", BigDecimal.valueOf(250));
        payouts.put("participacion", BigDecimal.valueOf(50));
        payouts.put("campeonGp", BigDecimal.valueOf(2000));
        grandPrixPoints = new int[]{10, 8, 6, 4, 2, 1};
    }

    private static JsonObject writeDefaults() {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("minJugadores", minPlayers);
        json.addProperty("umbralVotosPct", voteThresholdPct);
        json.addProperty("cuentaAtrasSegundos", countdownSeconds);
        json.addProperty("timeoutCarreraSegundos", raceTimeoutSeconds);
        json.addProperty("rankingIntervalTicks", rankingIntervalTicks);
        json.addProperty("hudIntervalTicks", hudIntervalTicks);
        json.addProperty("graciaDesmonteSegundos", dismountGraceSeconds);
        json.addProperty("graciaSentidoContrarioSegundos", wrongWayGraceSeconds);
        json.addProperty("alturaCheckpoint", checkpointHeight);
        json.addProperty("leaderboardTopN", leaderboardTopN);
        json.addProperty("backendPostEnabled", backendPostEnabled);

        JsonObject pagos = new JsonObject();
        payouts.forEach(pagos::addProperty);
        json.add("pagos", pagos);

        com.google.gson.JsonArray points = new com.google.gson.JsonArray();
        for (int value : grandPrixPoints) {
            points.add(value);
        }
        json.add("puntosGp", points);
        return json;
    }

    private static boolean has(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    /** The settings a race runs with, in ticks. */
    public static RaceSettings raceSettings() {
        return new RaceSettings(
                minPlayers,
                voteThresholdPct,
                countdownSeconds * RaceSettings.TICKS_PER_SECOND,
                raceTimeoutSeconds * RaceSettings.TICKS_PER_SECOND,
                rankingIntervalTicks,
                hudIntervalTicks,
                dismountGraceSeconds * RaceSettings.TICKS_PER_SECOND,
                wrongWayGraceSeconds * RaceSettings.TICKS_PER_SECOND);
    }

    /** Payout for a finishing position, or the participation payment for {@code "participacion"}. */
    public static BigDecimal payoutFor(String key) {
        return payouts.getOrDefault(key, BigDecimal.ZERO);
    }

    public static Map<String, BigDecimal> payouts() {
        return Map.copyOf(payouts);
    }

    public static int[] grandPrixPoints() {
        return grandPrixPoints.clone();
    }

    public static int leaderboardTopN() {
        return leaderboardTopN;
    }

    /**
     * Blocks a new checkpoint is grown upward from its WorldEdit selection.
     *
     * <p>Admins mark a gate by selecting the road blocks, but a car's tracked position is its
     * centre, which floats above them — a box that stops at the selection's top is driven straight
     * over, and the lap silently never counts. Growing upward is what makes a gate behave like a
     * wall rather than a slab.</p>
     */
    public static int checkpointHeight() {
        return checkpointHeight;
    }

    public static boolean isBackendPostEnabled() {
        return backendPostEnabled;
    }
}
