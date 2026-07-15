package es.boffmedia.teras.battle.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.TerasConfig;
import es.boffmedia.teras.util.net.HttpText;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a {@link BattleConfig} (JSON + raw team paste). Read from
 * {@code config/teras/combates/{tipo}/{id}/} on disk first, then the SmartRotom HTTP API. The team
 * paste is stored raw for the active provider to parse. Blocking — call off the server thread.
 */
public final class BattleConfigLoader {
    private BattleConfigLoader() {}

    private static final Gson GSON = new Gson();

    /** Trainer battles live under {@code entrenadores}. */
    public static BattleConfig loadTrainer(String id) {
        return load(id, "entrenadores");
    }

    /** Wild encounters / events live under {@code eventos}. */
    public static BattleConfig loadEvent(String id) {
        return load(id, "eventos");
    }

    public static BattleConfig load(String id, String tipo) {
        if (!HttpText.isValidIdentifier(id)) {
            Teras.LOGGER.error("Refused combat config load: invalid identifier '{}'", id);
            return null;
        }

        String configJson = readConfigJson(id, tipo);
        if (configJson == null) {
            Teras.LOGGER.error("No combat config found for '{}' (local or remote)", id);
            return null;
        }

        BattleConfig config;
        try {
            config = GSON.fromJson(configJson, BattleConfig.class);
        } catch (JsonSyntaxException e) {
            Teras.LOGGER.error("Malformed combat config JSON for '{}'", id, e);
            return null;
        }
        if (config == null) {
            Teras.LOGGER.error("Empty combat config for '{}'", id);
            return null;
        }

        int[] equipos = config.getEquipos();
        if (equipos == null || equipos.length == 0) {
            Teras.LOGGER.error("Combat config for '{}' declares no teams", id);
            return null;
        }
        int teamId = equipos[(int) (Math.random() * equipos.length)];

        String paste = readTeamPaste(id, tipo, teamId);
        if (paste == null || paste.isBlank()) {
            Teras.LOGGER.error("Combat config for '{}' resolved an empty team ({})", id, teamId);
            return null;
        }

        config.setTeamPaste(paste);
        config.setNombreArchivo(id);
        config.setCarpeta(tipo);
        return config;
    }

    /* ---- Sources: local dir first, then the SmartRotom HTTP API ---- */

    private static String readConfigJson(String id, String tipo) {
        String local = readLocal(localBase(tipo, id).resolve("config.json"));
        if (local != null) {
            return local;
        }
        return HttpText.fetchCached(apiBase(tipo, id) + "/config.json");
    }

    private static String readTeamPaste(String id, String tipo, int teamId) {
        String local = readLocal(localBase(tipo, id).resolve(teamId + ".txt"));
        if (local != null) {
            return local;
        }
        return HttpText.fetchCached(apiBase(tipo, id) + "/" + teamId + ".txt");
    }

    private static Path localBase(String tipo, String id) {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("combates").resolve(tipo).resolve(id);
    }

    private static String apiBase(String tipo, String id) {
        return TerasConfig.getHome() + "/combates/" + tipo + "/" + id;
    }

    private static String readLocal(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            Teras.LOGGER.error("Failed to read local combat file {}", path, e);
            return null;
        }
    }
}
