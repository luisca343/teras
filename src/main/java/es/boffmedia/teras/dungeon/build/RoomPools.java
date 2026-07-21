package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.piso.FloorDef;
import es.boffmedia.teras.dungeon.piso.RoomPoolIndex;
import es.boffmedia.teras.dungeon.piso.RoomVariant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;

/**
 * The live {@link RoomPoolIndex} — what is actually on disk right now, cached.
 *
 * <p>Discovery walks every structure source the server has ({@code listTemplates} flat-maps the jar
 * and the world's {@code generated} folder alike, with {@code generated} still shadowing the jar
 * file for file), which is not something to do per room during a build. It is rebuilt at server
 * start, on {@code /teras dungeon reload}, and immediately after the room editor writes a template
 * — the last so that a room saved in game is drawable without reloading anything.</p>
 *
 * <p>All the reasoning about pools lives in {@link RoomPoolIndex}, which is pure and tested; this is
 * only the shim that feeds it and holds the result.</p>
 */
public final class RoomPools {
    private RoomPools() {}

    private static RoomPoolIndex index = RoomPoolIndex.EMPTY;

    /** Re-reads the folder layout. Cheap enough to call on every reload, too slow to call per room. */
    public static void rebuild(StructureTemplateManager manager) {
        if (manager == null) {
            return;
        }
        List<String> paths;
        try (var templates = manager.listTemplates()) {
            paths = templates
                    .filter(id -> RoomPoolIndex.NAMESPACE.equals(id.getNamespace()))
                    .filter(id -> id.getPath().startsWith(RoomPoolIndex.ROOT))
                    .map(ResourceLocation::getPath)
                    .toList();
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not list room templates: {}", e.toString());
            return;
        }
        index = RoomPoolIndex.of(paths);
        Teras.LOGGER.info("Dungeons: {} room template(s) discovered across {} set(s)",
                paths.size(), index.sets().size());
    }

    public static RoomPoolIndex index() {
        return index;
    }

    /** What generation may draw for this key. */
    public static List<RoomVariant> pool(FloorDef piso, String roomKey) {
        return index.pool(piso, roomKey);
    }

    /**
     * Everything the key holds, including variants weighted to zero. The editor and {@code sala
     * listar} use this: a switched-off room must still be openable, or turning one off would be a
     * one-way door.
     */
    public static List<RoomVariant> declared(FloorDef piso, String roomKey) {
        return index.declared(piso, roomKey);
    }
}
