package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.piso.FloorDef;
import es.boffmedia.teras.dungeon.piso.RoomVariant;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Stamping a new piso out of an existing one.
 *
 * <p>This is what makes "a piso owns all thirteen rooms and borrows nothing" affordable. Copying is
 * an <b>authoring</b> act, not a runtime link: every template is written out as a real file in the
 * new piso's namespace, and from that moment the two are unrelated. Editing Cuevas afterwards does
 * nothing to Cuevas Infestadas, which is the entire difference between this and the block-palette
 * themes it replaced.</p>
 *
 * <p>Copies land in the world's {@code generated} structure folder — the same override path the room
 * editor saves through — so they take precedence over anything in the jar and can be edited in place
 * immediately.</p>
 */
public final class PisoAuthoring {
    private PisoAuthoring() {}

    /**
     * Deletes a piso's templates from the world's {@code generated} folder, so the jar's shipped
     * ones take over again.
     *
     * <p>Necessary because {@code generated} <b>shadows the jar</b>. A piso stamped or edited before
     * the templates changed shape keeps building the old geometry forever, and no amount of updating
     * the mod fixes it — the override wins every time. There is no way to see this in game: the
     * template exists, it is simply the wrong one.</p>
     *
     * <p>Removes the cached instance as well as the file; the manager keeps what it has already read
     * and would otherwise serve the deleted template for the rest of the session.</p>
     */
    public static Result purgeGenerated(MinecraftServer server, FloorDef piso) {
        StructureTemplateManager manager = server.getStructureManager();
        List<String> failed = new ArrayList<>();
        int removed = 0;
        for (String key : piso.requiredRooms()) {
            for (RoomVariant variant : piso.variants(key)) {
                ResourceLocation id = ResourceLocation.tryParse(variant.template());
                if (id == null) {
                    continue;
                }
                try {
                    java.nio.file.Path path =
                            manager.createAndValidatePathToGeneratedStructure(id, ".nbt");
                    if (java.nio.file.Files.deleteIfExists(path)) {
                        removed++;
                    }
                    manager.remove(id);
                } catch (Exception e) {
                    Teras.LOGGER.error("Dungeons: could not purge {}: {}", id, e.toString());
                    failed.add(key + " (" + e + ")");
                }
            }
        }
        return new Result(removed, failed);
    }

    /** What a copy did: templates written, and the ones that could not be. */
    public record Result(int copied, List<String> failed) {
        public boolean ok() {
            return failed.isEmpty();
        }
    }

    /**
     * Writes every room {@code target} owes as a copy of {@code source}'s equivalent.
     *
     * <p>Only the rooms the target's own {@code formas} require: a piso narrowed to two shapes is
     * not handed the L-rooms it will never generate, which is the point of narrowing.</p>
     *
     * <p>A room the target already has is left alone, so this is safe to re-run after widening a
     * piso's shapes — it fills the gap rather than overwriting authored work.</p>
     */
    public static Result copyRooms(MinecraftServer server, FloorDef source, FloorDef target) {
        return copyRooms(server, source, target, false);
    }

    /**
     * @param overwrite replace rooms the target already has. Needed when the shipped templates
     *                  change shape — a piso stamped before the room height or the key vocabulary
     *                  changed keeps building the old geometry forever, because copies live in the
     *                  world's {@code generated} folder and take precedence over the jar
     */
    public static Result copyRooms(MinecraftServer server, FloorDef source, FloorDef target,
                                   boolean overwrite) {
        StructureTemplateManager manager = server.getStructureManager();
        var blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<String> failed = new ArrayList<>();
        int copied = 0;

        for (String key : target.requiredRooms()) {
            ResourceLocation targetId = ResourceLocation.tryParse(
                    RoomVariant.conventional(target.id(), key).template());
            if (targetId == null) {
                failed.add(key + " (bad id)");
                continue;
            }
            if (!overwrite && manager.get(targetId).isPresent()) {
                continue;
            }
            // The source's first variant: a piso with several rooms for a key seeds the copy with
            // one of them, and the builder varies it from there.
            List<RoomVariant> variants = source.variants(key);
            ResourceLocation sourceId = ResourceLocation.tryParse(variants.get(0).template());
            StructureTemplate from = sourceId == null ? null : manager.get(sourceId).orElse(null);
            if (from == null) {
                failed.add(key + " (source has none)");
                continue;
            }
            try {
                CompoundTag tag = from.save(new CompoundTag());
                StructureTemplate to = manager.getOrCreate(targetId);
                to.load(blocks, tag);
                if (manager.save(targetId)) {
                    copied++;
                } else {
                    failed.add(key + " (could not write)");
                }
            } catch (Exception e) {
                Teras.LOGGER.error("Dungeons: could not copy {} -> {}: {}",
                        sourceId, targetId, e.toString());
                failed.add(key + " (" + e + ")");
            }
        }
        return new Result(copied, failed);
    }
}
