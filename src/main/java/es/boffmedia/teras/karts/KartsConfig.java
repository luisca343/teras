package es.boffmedia.teras.karts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.engine.RaceSettings;
import es.boffmedia.teras.util.YamlConfig;
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
 * Karts tunables, in {@code config/teras/karts/config.yml}. Same discipline as
 * {@link es.boffmedia.teras.util.TerasConfig}: defaults reset before every load, so a client's
 * integrated server never inherits the previous world's numbers, and each key is read defensively.
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
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
        Path dir = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("karts");
        Path path = dir.resolve("config.yml");
        Path legacy = dir.resolve("config.json");
        try {
            if (!Files.exists(path) && Files.exists(legacy)) {
                migrateFromJson(dir, path, legacy);
                // Fall through and read what was just written, so migrated and steady-state agree.
            }
            if (!Files.exists(path)) {
                YamlConfig.write(path, renderTemplate());
                Teras.LOGGER.info("Karts: created default {}", path);
                return;
            }

            YamlConfig yaml = YamlConfig.read(path);
            minPlayers = yaml.integer("minJugadores", minPlayers);
            voteThresholdPct = yaml.integer("umbralVotosPct", voteThresholdPct);
            countdownSeconds = yaml.integer("cuentaAtrasSegundos", countdownSeconds);
            raceTimeoutSeconds = yaml.integer("timeoutCarreraSegundos", raceTimeoutSeconds);
            rankingIntervalTicks = yaml.integer("rankingIntervalTicks", rankingIntervalTicks);
            hudIntervalTicks = yaml.integer("hudIntervalTicks", hudIntervalTicks);
            dismountGraceSeconds = yaml.integer("graciaDesmonteSegundos", dismountGraceSeconds);
            wrongWayGraceSeconds = yaml.integer("graciaSentidoContrarioSegundos", wrongWayGraceSeconds);
            checkpointHeight = yaml.integer("alturaCheckpoint", checkpointHeight);
            leaderboardTopN = yaml.integer("leaderboardTopN", leaderboardTopN);
            backendPostEnabled = yaml.bool("backendPostEnabled", backendPostEnabled);
            if (yaml.has("pagos")) readPayouts(yaml.section("pagos"));
            if (yaml.has("puntosGp")) readGrandPrixPoints(yaml.list("puntosGp"));

            Teras.LOGGER.info("Karts: config loaded from {}", path);
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to load config, using defaults: {}", e.toString());
        }
    }

    /** One-shot upgrade from the pre-YAML {@code config.json}; the old file is kept, not deleted. */
    private static void migrateFromJson(Path dir, Path path, Path legacy) throws Exception {
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(legacy)) {
            json = GSON.fromJson(reader, JsonObject.class);
        }
        if (json != null) {
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
            if (has(json, "pagos")) {
                JsonObject pagos = json.getAsJsonObject("pagos");
                payouts = new LinkedHashMap<>();
                for (String key : pagos.keySet()) {
                    try {
                        payouts.put(key, pagos.get(key).getAsBigDecimal());
                    } catch (Exception e) {
                        Teras.LOGGER.warn("Karts: ignoring payout '{}': {}", key, e.toString());
                    }
                }
            }
            if (has(json, "puntosGp")) {
                com.google.gson.JsonArray array = json.getAsJsonArray("puntosGp");
                int[] points = new int[array.size()];
                for (int i = 0; i < array.size(); i++) points[i] = array.get(i).getAsInt();
                grandPrixPoints = points;
            }
        }
        YamlConfig.write(path, renderTemplate());
        try {
            Files.move(legacy, dir.resolve("config.json.migrated"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: migrated to config.yml but could not rename the old "
                    + "config.json ({}). Delete it by hand — it is no longer read.", e.toString());
        }
        Teras.LOGGER.info("Karts: migrated config.json -> config.yml "
                + "(old file kept as config.json.migrated and no longer read)");
    }

    private static void readPayouts(YamlConfig yaml) {
        payouts = new LinkedHashMap<>();
        for (String key : yaml.keys()) {
            try {
                payouts.put(key, new BigDecimal(String.valueOf(yaml.raw(key)).trim()));
            } catch (Exception e) {
                Teras.LOGGER.warn("Karts: ignoring payout '{}': {}", key, e.toString());
            }
        }
    }

    private static void readGrandPrixPoints(java.util.List<Object> values) {
        int[] points = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            try {
                points[i] = new BigDecimal(String.valueOf(values.get(i)).trim()).intValue();
            } catch (Exception e) {
                Teras.LOGGER.warn("Karts: ignoring grand prix points entry '{}': {}",
                        values.get(i), e.toString());
                return;
            }
        }
        grandPrixPoints = points;
    }

    /** Rendered rather than serialized, so the explanation next to each key survives. */
    private static String renderTemplate() {
        StringBuilder pagos = new StringBuilder();
        payouts.forEach((key, value) -> pagos.append("  ").append(key).append(": ").append(value).append('\n'));
        StringBuilder puntos = new StringBuilder();
        for (int i = 0; i < grandPrixPoints.length; i++) {
            if (i > 0) puntos.append(", ");
            puntos.append(grandPrixPoints[i]);
        }
        return """
                # Karts tunables. See docs/KARTS.md.
                version: 1

                # Racers needed before a race can start.
                minJugadores: %s
                # Percentage of waiting players who must vote to start early.
                umbralVotosPct: %s
                # Countdown once the race is agreed, in seconds.
                cuentaAtrasSegundos: %s
                # Abandon a race that has run this long, in seconds.
                timeoutCarreraSegundos: %s

                # How often positions are recomputed and the HUD redrawn, in ticks (20 = 1 second).
                rankingIntervalTicks: %s
                hudIntervalTicks: %s

                # Grace before a racer who left their kart, or is driving backwards, is penalised.
                graciaDesmonteSegundos: %s
                graciaSentidoContrarioSegundos: %s

                # How far above the track a checkpoint still counts, in blocks.
                alturaCheckpoint: %s

                # Entries kept per track leaderboard.
                leaderboardTopN: %s

                # Send race results to the SmartRotom backend.
                backendPostEnabled: %s

                # Prize money. Keys are finishing positions; the two named ones are for showing up
                # at all and for winning a whole grand prix.
                pagos:
                %s
                # Championship points by finishing position, best first.
                puntosGp: [%s]
                """.formatted(minPlayers, voteThresholdPct, countdownSeconds, raceTimeoutSeconds,
                rankingIntervalTicks, hudIntervalTicks, dismountGraceSeconds, wrongWayGraceSeconds,
                checkpointHeight, leaderboardTopN, backendPostEnabled,
                pagos.toString().stripTrailing(), puntos);
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
