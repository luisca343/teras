package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.dungeon.encounter.SpawnTables;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.piso.FloorDef;
import es.boffmedia.teras.dungeon.piso.RoomAudit;
import es.boffmedia.teras.dungeon.piso.RoomKeys;
import es.boffmedia.teras.dungeon.piso.RoomVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a piso's templates off disk and hands them to {@link RoomAudit}.
 *
 * <p>The split is the usual one: {@code RoomAudit} owns the rules and is pure, this owns the
 * {@code StructureTemplate} decoding and is not. Everything Minecraft-shaped — which block counts
 * as air, how a DATA structure block spells its metadata — is resolved here, once.</p>
 */
public final class RoomAuditor {
    private RoomAuditor() {}

    /** One template's findings, keyed for reporting. */
    public record Result(String roomKey, ResourceLocation template, List<RoomAudit.Finding> findings) {
        public boolean clean() {
            return findings.isEmpty();
        }

        public long errors() {
            return findings.stream().filter(f -> f.level() == RoomAudit.Level.ERROR).count();
        }
    }

    /** Audits every variant of every room the piso owes; empty findings mean the room is clean. */
    public static List<Result> audit(FloorDef piso, StructureTemplateManager manager,
                                     int roomSize, int roomHeight, int doorWidth, int doorHeight) {
        List<Result> results = new ArrayList<>();
        for (String key : piso.requiredRooms()) {
            RoomShape shape = RoomKeys.shapeFor(key);
            for (RoomVariant variant : piso.variants(key)) {
                ResourceLocation id = ResourceLocation.tryParse(variant.template());
                StructureTemplate template = id == null ? null : manager.get(id).orElse(null);
                if (template == null) {
                    // Missing templates are piso info's business; auditing nothing would only
                    // report every rule as passing.
                    continue;
                }
                results.add(new Result(key, id, audit(template, shape, key,
                        roomSize, roomHeight, doorWidth, doorHeight, waveMax(piso))));
            }
        }
        return results;
    }

    /**
     * One template's findings, held to {@code waveMax} spawn markers. Split out so the editor can
     * run the same check the moment a room is saved: an author who is told at save has the room open
     * and the mistake in front of them, and one who is told by a later {@code piso auditar} has to
     * go back and find it again. {@link #waveMax(FloorDef)} is what a caller passes.
     */
    public static List<RoomAudit.Finding> audit(StructureTemplate template, RoomShape shape,
                                                String roomKey, int roomSize, int roomHeight,
                                                int doorWidth, int doorHeight, int waveMax) {
        return RoomAudit.audit(read(template, shape), roomKey, roomSize, roomHeight,
                doorWidth, doorHeight, waveMax);
    }

    /**
     * The largest wave a room of {@code piso} may be asked to hold — the bar its floor spawn marker
     * count is held to, because the spawner cycles positions and a wave larger than the markers
     * stacks enemies on one block.
     *
     * <p>It is the piso's own {@code countMax}, since a wave now comes from the piso's table rather
     * than the global stage curve. A piso that declares no table of its own falls back to the widest
     * {@code enemies.json} stage, so an unmigrated server audits against the number it actually
     * spawns.</p>
     */
    public static int waveMax(FloorDef piso) {
        if (piso != null && !piso.enemigos().isEmpty()) {
            return piso.enemigos().countMax();
        }
        int max = 0;
        for (int stage = 1; stage <= 12; stage++) {
            SpawnTables.StageTable table = SpawnTables.stageTable(stage);
            if (table != null) {
                max = Math.max(max, table.countMax());
            }
        }
        return max;
    }

    /**
     * A template as the audit sees it: the set of positions holding something, and the markers.
     *
     * <p>The block list comes from the saved NBT rather than {@code filterBlocks}, which selects
     * blocks <i>matching</i> a given one and so cannot enumerate everything — asking it for air and
     * inverting reads every floor in the game as missing. The palette is consulted by name so air
     * and structure void are excluded: structure void is how a template says "leave whatever is
     * already here", which over an L's empty quadrant is right and over a floor is a hole.</p>
     */
    private static RoomAudit.Room read(StructureTemplate template, RoomShape shape) {
        net.minecraft.nbt.CompoundTag tag = template.save(new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.ListTag palette =
                tag.getList("palette", net.minecraft.nbt.CompoundTag.TAG_COMPOUND);
        boolean[] empty = new boolean[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            String name = palette.getCompound(i).getString("Name");
            empty[i] = name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                    || name.equals("minecraft:void_air") || name.equals("minecraft:structure_void");
        }

        java.util.Set<RoomAudit.Pos> solid = new java.util.HashSet<>();
        net.minecraft.nbt.ListTag blocks =
                tag.getList("blocks", net.minecraft.nbt.CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            net.minecraft.nbt.CompoundTag block = blocks.getCompound(i);
            int state = block.getInt("state");
            if (state >= 0 && state < empty.length && empty[state]) {
                continue;
            }
            net.minecraft.nbt.ListTag pos =
                    block.getList("pos", net.minecraft.nbt.CompoundTag.TAG_INT);
            if (pos.size() >= 3) {
                solid.add(new RoomAudit.Pos(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
            }
        }

        Map<String, List<RoomAudit.Pos>> markers = new LinkedHashMap<>();
        for (TemplateMarkers.Marker marker : TemplateMarkers.extract(template,
                new StructurePlaceSettings(), BlockPos.ZERO)) {
            markers.computeIfAbsent(marker.kind(), k -> new ArrayList<>())
                    .add(new RoomAudit.Pos(marker.pos().getX(), marker.pos().getY(),
                            marker.pos().getZ()));
        }
        return new RoomAudit.Room(shape, solid, markers, template.getSize().getY());
    }
}
