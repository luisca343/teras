package es.boffmedia.teras.quests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.quests.model.QuestInfo;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The known quest <i>definitions</i>, keyed by quest id, cached in {@code config/teras/misiones.json}.
 * Ported from the file handling the 1.16.5 {@code CustomNPCsEvents.openDialog} did inline.
 *
 * <p>Definitions are learned lazily: a quest lands here the first time a player opens a dialog that
 * offers it. {@link #put} only rewrites the file when the definition actually changed, using
 * {@link QuestInfo#equals} (which ignores per-player state) — the 1.16.5 dirty-check.</p>
 *
 * <p>Server-thread only, like {@link NpcCatalog}.</p>
 *
 * <p><b>Deviation from 1.16.5:</b> the file is read once and held in memory. The old code re-read and
 * re-parsed {@code misiones.json} on <i>every dialog open</i>; the file is only ever written by us, so
 * caching it costs nothing and takes disk I/O off the interaction path.</p>
 */
public final class MisionesStore {
    private MisionesStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<Map<Integer, QuestInfo>>() {}.getType();

    private static Map<Integer, QuestInfo> quests;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("misiones.json");
    }

    /** Stores {@code info} and persists, but only if it differs from what we already knew. */
    public static void put(QuestInfo info) {
        ensureLoaded();
        QuestInfo known = quests.get(info.getId());
        if (info.equals(known)) return;
        quests.put(info.getId(), info);
        save();
    }

    public static Map<Integer, QuestInfo> getQuests() {
        ensureLoaded();
        return quests;
    }

    private static void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(quests, STORE_TYPE));
            Teras.LOGGER.info("MisionesStore: saved {} quest definitions", quests.size());
        } catch (Exception e) {
            Teras.LOGGER.warn("MisionesStore: failed to save misiones.json: {}", e.toString());
        }
    }

    private static void ensureLoaded() {
        if (quests != null) return;
        quests = new HashMap<>();
        Path file = path();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<Integer, QuestInfo> loaded = GSON.fromJson(reader, STORE_TYPE);
            if (loaded != null) quests.putAll(loaded);
        } catch (Exception e) {
            Teras.LOGGER.warn("MisionesStore: failed to load misiones.json: {}", e.toString());
        }
        Teras.LOGGER.info("MisionesStore: loaded {} quest definitions from disk", quests.size());
    }
}
