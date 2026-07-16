package es.boffmedia.teras.quests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.quests.model.NpcData;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The persistent catalog of every NPC-dialog pairing the server has seen, backed by
 * {@code config/teras/npc_catalog.json}. Ported from the 1.16.5 {@code NpcCatalog}.
 *
 * <p>Entries are keyed by dialog id, and deduplicated within a dialog by the NPC's uuid
 * ({@code "uuid:dialogId"}), so an NPC that moves updates its entry rather than adding a second one.
 * Because the catalog persists, NPCs from earlier sessions that aren't currently loaded still show up
 * in the SmartRotom web.</p>
 *
 * <p>Server-thread only: every caller ({@link QuestEvents}, {@link NpcScanner}) runs there.</p>
 *
 * <p><b>Deviation from 1.16.5:</b> {@code update()} no longer writes the file on every call. The old
 * version re-serialized the whole catalog once per NPC <i>and once per dialog option</i>, so a
 * full-server scan did hundreds of full-file writes. Callers now batch and call
 * {@link #saveIfDirty()} once when they're done.</p>
 */
public final class NpcCatalog {
    private NpcCatalog() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CATALOG_TYPE = new TypeToken<Map<Integer, List<NpcData>>>() {}.getType();

    private static Map<Integer, List<NpcData>> catalog;
    /** {@code "uuid:dialogId"} for O(1) dedup, rebuilt from the catalog on load. */
    private static Set<String> registeredKeys;
    private static boolean dirty = false;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("npc_catalog.json");
    }

    /**
     * Adds or refreshes {@code data} for {@code dialogId}, keeping the entry's coordinates current if
     * the NPC has moved. Marks the catalog dirty; the caller is responsible for {@link #saveIfDirty()}.
     */
    public static void update(int dialogId, NpcData data) {
        if (data.getUuid() == null || data.getUuid().isEmpty()) return;
        ensureLoaded();
        String key = data.getUuid() + ":" + dialogId;
        List<NpcData> list = catalog.computeIfAbsent(dialogId, k -> new ArrayList<>());
        if (registeredKeys.contains(key)) {
            list.replaceAll(existing -> key.equals(keyOf(existing, dialogId)) ? data : existing);
        } else {
            registeredKeys.add(key);
            list.add(data);
        }
        dirty = true;
    }

    public static Map<Integer, List<NpcData>> getCatalog() {
        ensureLoaded();
        return catalog;
    }

    public static void saveIfDirty() {
        if (!dirty) return;
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(catalog, CATALOG_TYPE));
            dirty = false;
            Teras.LOGGER.info("NpcCatalog: saved {} NPC-dialog entries", registeredKeys.size());
        } catch (Exception e) {
            Teras.LOGGER.warn("NpcCatalog: failed to save catalog: {}", e.toString());
        }
    }

    /**
     * The trailing name of a CustomNPCs/Pixelmon skin texture path — what the SmartRotom web renders.
     * The two known prefixes are handled explicitly, with the last path segment as the fallback.
     */
    public static String extractTextureName(String texture) {
        if (texture == null || texture.isEmpty()) return "";
        for (String prefix : new String[]{"pixelmon:textures/steve/", "customnpcs:textures/entity/humanmale/"}) {
            if (texture.contains(prefix)) {
                String[] parts = texture.split(prefix);
                return parts.length > 1 ? parts[1] : texture;
            }
        }
        String[] parts = texture.split("/");
        return parts[parts.length - 1];
    }

    private static String keyOf(NpcData data, int dialogId) {
        return (data.getUuid() != null ? data.getUuid() : "") + ":" + dialogId;
    }

    private static void ensureLoaded() {
        if (catalog != null) return;
        catalog = new HashMap<>();
        registeredKeys = new HashSet<>();
        Path file = path();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<Integer, List<NpcData>> loaded = GSON.fromJson(reader, CATALOG_TYPE);
            if (loaded != null) {
                catalog.putAll(loaded);
                catalog.forEach((dialogId, entries) -> entries.forEach(d -> {
                    if (d.getUuid() != null && !d.getUuid().isEmpty()) {
                        registeredKeys.add(d.getUuid() + ":" + dialogId);
                    }
                }));
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("NpcCatalog: failed to load catalog from disk: {}", e.toString());
        }
        Teras.LOGGER.info("NpcCatalog: loaded {} entries from disk", registeredKeys.size());
    }
}
