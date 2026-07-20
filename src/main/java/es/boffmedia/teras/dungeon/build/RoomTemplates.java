package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import es.boffmedia.teras.dungeon.model.SeededRng;
import es.boffmedia.teras.dungeon.piso.FloorDef;
import es.boffmedia.teras.dungeon.piso.RoomKeys;
import es.boffmedia.teras.dungeon.piso.RoomVariant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves a room to the template a <b>piso</b> supplies for it.
 *
 * <p>This replaced a theme-keyed pool map whose every lookup fell back to a {@code base} theme.
 * That fallback was the whole problem: it meant a "theme" could declare nothing and still build a
 * floor out of another theme's rooms, so every place was structurally identical by construction.
 * <b>There is no fallback here.</b> A piso resolves its own rooms or it is not usable, which is
 * caught at load by {@link #missingTemplates} rather than mid-build.</p>
 *
 * <p>Selection is derived from the layout's base seed and the room's placement index, never from
 * world randomness: the same seed rebuilds the same floor down to each room's variant.</p>
 */
public final class RoomTemplates {
    private RoomTemplates() {}

    public record TemplateEntry(ResourceLocation template, int weight, Rotation rotation) {}

    /**
     * The room key a room draws from — its type plus its shape <b>family</b>, never its orientation.
     * A vertical 2×1 and a horizontal one resolve to the same {@code _large} template; the four L
     * orientations resolve to the same {@code _l} one.
     *
     * <p>No chain of fallbacks: a 2×2 boss room asks for {@code boss_big} and gets it or nothing,
     * because a piso declaring BIG is <i>required</i> to have authored it. Settling for a
     * differently-furnished room of the right footprint was only ever needed because themes could
     * be incomplete.</p>
     */
    public static String keyFor(Room room) {
        String type = room.type().name().toLowerCase(Locale.ROOT);
        return RoomKeys.keyFor(type, room.shape());
    }

    /**
     * Deterministic weighted pick for a room; {@code roomIndex} is its position in placement order.
     *
     * <p>Draws are independent per room, which is fine while a piso ships one template per key. Once
     * pools carry several, this wants memory instead — independent weighted rolls put the same room
     * down four times in a row often enough to read as no variety at all (see DUNGEONS.md §11).</p>
     */
    public static TemplateEntry select(FloorDef piso, Room room, long baseSeed, int roomIndex) {
        List<RoomVariant> variants = piso.variants(keyFor(room));
        SeededRng rng = new SeededRng(DungeonSeeds.derive(baseSeed, 0x726F6F6DL + roomIndex));
        int total = variants.stream().mapToInt(RoomVariant::weight).sum();
        int roll = rng.between(1, Math.max(1, total));
        RoomVariant chosen = variants.get(variants.size() - 1);
        for (RoomVariant variant : variants) {
            roll -= variant.weight();
            if (roll <= 0) {
                chosen = variant;
                break;
            }
        }
        // The shape's own turn, plus whatever the variant asked for on top. This is what lets one
        // authored L serve all four orientations and one 2x1 serve both.
        return entry(chosen, room.shape().baseRotation());
    }

    /**
     * Every variant of one key, unrotated — the editor pastes and saves templates in the orientation
     * they are authored in, so showing them turned would bake a rotation into the next save.
     */
    public static List<TemplateEntry> pool(FloorDef piso, String roomKey) {
        List<TemplateEntry> entries = new ArrayList<>();
        for (RoomVariant variant : piso.variants(roomKey)) {
            entries.add(entry(variant, 0));
        }
        return entries;
    }

    /**
     * The templates this piso names that do not exist, as {@code key -> template} strings. Empty
     * means the piso can build every room it might be asked for.
     *
     * <p>Run once at server start, because it is the only check that needs the game: a piso passes
     * its structural validation with ids that resolve to nothing on disk.</p>
     */
    public static List<String> missingTemplates(FloorDef piso, StructureTemplateManager manager) {
        List<String> missing = new ArrayList<>();
        for (String key : piso.requiredRooms()) {
            for (RoomVariant variant : piso.variants(key)) {
                ResourceLocation id = ResourceLocation.tryParse(variant.template());
                if (id == null || manager.get(id).isEmpty()) {
                    missing.add(key + " -> " + variant.template());
                }
            }
        }
        return missing;
    }

    /**
     * Templates whose actual size disagrees with the configured cell, as human-readable lines.
     *
     * <p>The size a room was authored at is baked into its {@code .nbt}; the size a cell is comes
     * from {@code config.yml}. Nothing ties them together, and both drift independently — a config
     * file written before {@code alturaSala} changed keeps its old value forever (a default only
     * applies to a file that does not exist yet), and a template copied into the world's
     * {@code generated} folder keeps its old geometry and takes precedence over the jar.</p>
     *
     * <p>Either way the floor still builds, just wrongly: ceilings at the wrong height, and a
     * discard that clears the configured height and leaves whatever stood above it. Loud, and not
     * fatal — dropping the piso would take the whole dungeon offline over something an admin can
     * fix in one line.</p>
     */
    public static List<String> mismatchedTemplates(FloorDef piso, StructureTemplateManager manager,
                                                   int roomSize, int roomHeight) {
        List<String> wrong = new ArrayList<>();
        for (String key : piso.requiredRooms()) {
            RoomShape shape = RoomKeys.shapeFor(key);
            int wantX = shape.cellsWide() * roomSize;
            int wantZ = shape.cellsDeep() * roomSize;
            for (RoomVariant variant : piso.variants(key)) {
                ResourceLocation id = ResourceLocation.tryParse(variant.template());
                StructureTemplate template = id == null ? null : manager.get(id).orElse(null);
                if (template == null) {
                    continue;
                }
                var size = template.getSize();
                if (size.getX() != wantX || size.getY() != roomHeight || size.getZ() != wantZ) {
                    wrong.add(variant.template() + " is " + size.getX() + "x" + size.getY() + "x"
                            + size.getZ() + " but the cell is " + wantX + "x" + roomHeight + "x"
                            + wantZ);
                }
            }
        }
        return wrong;
    }

    /** Every room key in the vocabulary, for command completion. */
    public static List<String> knownPoolKeys() {
        return List.copyOf(RoomKeys.requiredFor(java.util.EnumSet.allOf(ShapeFamily.class)));
    }

    private static TemplateEntry entry(RoomVariant variant, int extraDegrees) {
        ResourceLocation id = ResourceLocation.tryParse(variant.template());
        if (id == null) {
            Teras.LOGGER.error("Dungeons: '{}' is not a valid template id", variant.template());
            id = ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon/missing");
        }
        return new TemplateEntry(id, variant.weight(),
                rotation((variant.rotation() + extraDegrees) % 360));
    }

    private static Rotation rotation(int degrees) {
        return switch (degrees) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
