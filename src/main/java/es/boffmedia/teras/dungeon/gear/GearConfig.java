package es.boffmedia.teras.dungeon.gear;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file half of the gear catalog: {@code config/teras/dungeons/gear.json}, written on first run
 * and re-read by {@code /teras dungeon reload}. Kept apart from {@link GearDefs} so the catalog and
 * its merge rules stay plain Java and unit-testable.
 */
public final class GearConfig {
    private GearConfig() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons").resolve("gear.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(GearDefs.renderDefaults()));
                GearDefs.replaceAll(GearDefs.defaults());
                Teras.LOGGER.info("Dungeons: created default {}", path);
                return;
            }
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(path)) {
                root = GSON.fromJson(reader, JsonObject.class);
            }
            GearDefs.Merge merge = GearDefs.merge(root);
            merge.warnings().forEach(warning -> Teras.LOGGER.warn("Dungeons: {}", warning));
            GearDefs.replaceAll(merge.defs());
            Teras.LOGGER.info("Dungeons: gear loaded from {}", path);
            // The exact identifier each piece will be stamped with — the skin authoring loop is
            // edit/reload/look, and a path typo otherwise fails with no signal anywhere.
            for (GearDef def : merge.defs().values()) {
                if (def.hasSkin()) {
                    Teras.LOGGER.info("Dungeons: gear '{}' skin -> '{}' (type {})",
                            def.id(), def.skinId(), def.effectiveSkinType());
                }
            }
        } catch (Exception e) {
            // A broken file costs the tuning pass, never the items: the built-ins stand.
            GearDefs.replaceAll(GearDefs.defaults());
            Teras.LOGGER.warn("Dungeons: failed to load gear.json, using defaults: {}", e.toString());
        }
    }
}
