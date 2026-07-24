package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import es.boffmedia.teras.dungeon.piso.FloorDef;
import es.boffmedia.teras.dungeon.piso.RoomKeys;
import es.boffmedia.teras.dungeon.piso.RoomVariant;
import es.boffmedia.teras.dungeon.piso.VariantBag;
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
 * <b>There is no implicit fallback here.</b> A piso resolves its own rooms, plus whatever shared
 * sets it deliberately names in {@code hereda}, or it is not usable — caught at load by
 * {@link #missingTemplates} rather than mid-build.</p>
 *
 * <p>Selection is derived from the layout's base seed and the room's ordinal within its key, never
 * from world randomness: the same seed rebuilds the same floor down to each room's variant.</p>
 */
public final class RoomTemplates {
    private RoomTemplates() {}

    public record TemplateEntry(String name, ResourceLocation template, double weight,
                                Rotation rotation) {}

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
        // One footprint, no suffix: the 2×2 exit draws `exit`, never `exit_big` — see
        // RoomKeys.familyFor, which answers the reverse question the same way.
        if (room.type() == es.boffmedia.teras.dungeon.model.RoomType.EXIT) {
            return "exit";
        }
        String type = room.type().name().toLowerCase(Locale.ROOT);
        return RoomKeys.keyFor(type, room.shape());
    }

    /**
     * Deterministic pick for a room.
     *
     * @param ordinal this room's position among the rooms of the <i>same key</i>, in placement
     *                order — not its index on the floor. The bag deals one cycle per key, so a
     *                floor-wide index would skip entries and reintroduce the clustering the bag
     *                exists to remove
     */
    public static TemplateEntry select(FloorDef piso, Room room, long baseSeed, int ordinal) {
        String key = keyFor(room);
        List<RoomVariant> pool = RoomPools.pool(piso, key);
        if (pool.isEmpty()) {
            Teras.LOGGER.error("Dungeons: piso '{}' has no template for '{}' — the cell will be "
                    + "left empty", piso.id(), key);
            return new TemplateEntry(key, ResourceLocation.fromNamespaceAndPath(
                    Teras.MOD_ID, "dungeon/missing"), 1.0,
                    rotation(room.shape().baseRotation() % 360));
        }
        RoomVariant chosen = VariantBag.draw(pool, baseSeed, DungeonSeeds.fnv1a64(key), ordinal);
        // The shape's own turn: this is what lets one authored L serve all four orientations and
        // one 2x1 serve both.
        return entry(chosen, room.shape().baseRotation());
    }

    /**
     * Every variant of one key, unrotated and including the ones weighted to zero — the editor
     * pastes and saves templates in the orientation they are authored in, so showing them turned
     * would bake a rotation into the next save.
     */
    public static List<TemplateEntry> pool(FloorDef piso, String roomKey) {
        List<TemplateEntry> entries = new ArrayList<>();
        for (RoomVariant variant : RoomPools.declared(piso, roomKey)) {
            entries.add(entry(variant, 0));
        }
        return entries;
    }

    /**
     * The room keys this piso owes and cannot supply, as {@code key -> reason} strings. Empty means
     * the piso can build every room it might be asked for.
     *
     * <p>Run once at server start, because it is the only check that needs the game: what a piso
     * owes follows from its {@code formas}, but what it has is whatever is in its folders.</p>
     */
    public static List<String> missingTemplates(FloorDef piso, StructureTemplateManager manager) {
        List<String> missing = new ArrayList<>();
        for (String key : RoomPools.index().emptyKeys(piso)) {
            boolean switchedOff = !RoomPools.declared(piso, key).isEmpty();
            missing.add(key + (switchedOff
                    ? " -> every variant is weighted 0 in 'pesos'"
                    : " -> dungeon/" + piso.id() + "/" + key + "/ is empty"));
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
     * <p>Shared sets make this sharper: a {@code comun/} room is authored once and drawn by every
     * piso that inherits it, so a piso whose cell differs would place it clipped. Checking here
     * covers that too, since every piso is checked against every template it can actually draw.</p>
     *
     * <p>Either way the floor still builds, just wrongly: ceilings at the wrong height, and a
     * discard that clears the configured height and leaves whatever stood above it. Loud, and not
     * fatal — dropping the piso would take the whole dungeon offline over something an admin can
     * fix in one line.</p>
     */
    public static List<String> mismatchedTemplates(FloorDef piso, StructureTemplateManager manager,
                                                   int roomSize, int roomHeight) {
        List<String> wrong = new ArrayList<>();
        List<String> keys = new ArrayList<>(piso.requiredRooms());
        for (String optional : RoomKeys.OPTIONAL) {
            if (!RoomPools.pool(piso, optional).isEmpty()) {
                keys.add(optional);
            }
        }
        for (String key : keys) {
            RoomShape shape = RoomKeys.shapeFor(key);
            int wantX = shape.cellsWide() * roomSize;
            int wantZ = shape.cellsDeep() * roomSize;
            for (RoomVariant variant : RoomPools.pool(piso, key)) {
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
        List<String> keys = new ArrayList<>(
                RoomKeys.requiredFor(java.util.EnumSet.allOf(ShapeFamily.class)));
        keys.addAll(RoomKeys.OPTIONAL);
        return List.copyOf(keys);
    }

    /**
     * Whether this piso can furnish la sala del sello. Read at floor-plan time and handed to the
     * generator: a layout with an EXIT room the build cannot fill would be a barred doorway into
     * an empty cell, and a piso without the template keeps today's in-arena carve instead.
     */
    public static boolean hasExitRoom(FloorDef piso) {
        return !RoomPools.pool(piso, "exit").isEmpty();
    }

    /**
     * Whether this piso authored la sala de la Orden. Checked before the grace satellite is ever
     * appended, so a piso without the template simply never offers her and the Acreedor's half of
     * the arc still runs — the same "absence is a choice" rule the exit room established.
     */
    public static boolean hasOrdenRoom(FloorDef piso) {
        return !RoomPools.pool(piso, "orden").isEmpty();
    }

    private static TemplateEntry entry(RoomVariant variant, int degrees) {
        ResourceLocation id = ResourceLocation.tryParse(variant.template());
        if (id == null) {
            Teras.LOGGER.error("Dungeons: '{}' is not a valid template id", variant.template());
            id = ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon/missing");
        }
        return new TemplateEntry(variant.name(), id, variant.weight(), rotation(degrees % 360));
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
