package es.boffmedia.teras.util.objects.quests;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.file.FileHelper;

import java.io.File;
import java.io.BufferedReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;

public class NpcCatalog {
    private static final String PATH = "config/teras/npc_catalog.json";

    private static Map<Integer, List<NpcData>> catalog;
    private static Set<String> registeredKeys; // "uuid:dialogId" for O(1) dedup
    private static boolean dirty = false;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    // Register a newly discovered NPC-dialog. Returns true if it was new.
    // Used by InitEvent — no-op (and fast) if this NPC+dialog is already known.
    public static boolean register(int dialogId, NpcData data) {
        if (data.getUuid() == null || data.getUuid().isEmpty()) return false;
        ensureLoaded();
        String key = data.getUuid() + ":" + dialogId;
        if (registeredKeys.contains(key)) return false;
        registeredKeys.add(key);
        catalog.computeIfAbsent(dialogId, k -> new ArrayList<>()).add(data);
        dirty = true;
        return true;
    }

    // Update (or add) an NPC-dialog entry with fresh coordinates from a live scan.
    // Used by UpdateNPCs — always reflects current position even if NPC moved.
    public static void update(int dialogId, NpcData data) {
        if (data.getUuid() == null || data.getUuid().isEmpty()) return;
        ensureLoaded();
        String key = data.getUuid() + ":" + dialogId;
        List<NpcData> list = catalog.computeIfAbsent(dialogId, k -> new ArrayList<>());
        if (registeredKeys.contains(key)) {
            list.replaceAll(existing -> {
                String existingKey = (existing.getUuid() != null ? existing.getUuid() : "") + ":" + dialogId;
                return existingKey.equals(key) ? data : existing;
            });
        } else {
            registeredKeys.add(key);
            list.add(data);
        }
        dirty = true;
        saveIfDirty();
    }

    public static Map<Integer, List<NpcData>> getCatalog() {
        ensureLoaded();
        return catalog;
    }

    public static void saveIfDirty() {
        if (!dirty) return;
        FileHelper.writeFile(PATH, catalog);
        dirty = false;
        Teras.getLogger().info("NpcCatalog: saved " + registeredKeys.size() + " NPC-dialog entries");
    }

    // -------------------------------------------------------------------------
    // Texture utility — shared by UpdateNPCs and CustomNPCsEvents
    // -------------------------------------------------------------------------

    public static String extractTextureName(String texture) {
        if (texture == null || texture.isEmpty()) return "";
        if (texture.contains("pixelmon:textures/steve/")) {
            String[] parts = texture.split("pixelmon:textures/steve/");
            return parts.length > 1 ? parts[1] : texture;
        }
        if (texture.contains("customnpcs:textures/entity/humanmale/")) {
            String[] parts = texture.split("customnpcs:textures/entity/humanmale/");
            return parts.length > 1 ? parts[1] : texture;
        }
        // Fallback: just the last path segment
        String[] parts = texture.split("/");
        return parts[parts.length - 1];
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void ensureLoaded() {
        if (catalog != null) return;
        catalog = new HashMap<>();
        registeredKeys = new HashSet<>();
        File file = new File(PATH);
        if (!file.exists()) return;
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<Integer, List<NpcData>>>() {}.getType();
            BufferedReader reader = Files.newBufferedReader(file.toPath());
            Map<Integer, List<NpcData>> loaded = gson.fromJson(reader, type);
            reader.close();
            if (loaded != null) {
                catalog.putAll(loaded);
                for (Map.Entry<Integer, List<NpcData>> entry : catalog.entrySet()) {
                    for (NpcData d : entry.getValue()) {
                        if (d.getUuid() != null && !d.getUuid().isEmpty()) {
                            registeredKeys.add(d.getUuid() + ":" + entry.getKey());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Teras.getLogger().warn("NpcCatalog: failed to load catalog from disk: " + e.getMessage());
        }
        Teras.getLogger().info("NpcCatalog: loaded " + registeredKeys.size() + " entries from disk");
    }
}
