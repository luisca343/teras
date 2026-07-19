package es.boffmedia.teras.dungeon.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Data markers inside room templates: structure blocks in DATA mode whose metadata string declares
 * what stands at that position — {@code spawn:<table>}, {@code loot:<table>}, {@code boss},
 * {@code trapdoor}, {@code shopslot:<n>}, {@code door:<n|s|e|w>}. They replace the legacy paster's
 * hidden conventions (doors carved a fixed 5 blocks under the paste origin). Extraction reads the
 * template, so markers are known before — and independent of — world placement; the materializer
 * airs the physical marker blocks out after pasting.
 */
public final class TemplateMarkers {
    private TemplateMarkers() {}

    public record Marker(String tag, BlockPos pos) {
        public String kind() {
            int colon = tag.indexOf(':');
            return colon < 0 ? tag : tag.substring(0, colon);
        }

        public String argument() {
            int colon = tag.indexOf(':');
            return colon < 0 ? "" : tag.substring(colon + 1);
        }
    }

    /** Markers of {@code template} placed at {@code origin} with {@code settings}, world positions. */
    public static List<Marker> extract(StructureTemplate template, StructurePlaceSettings settings,
                                       BlockPos origin) {
        List<Marker> markers = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info
                : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
            if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) {
                continue;
            }
            String metadata = info.nbt().getString("metadata").trim().toLowerCase(Locale.ROOT);
            if (!metadata.isEmpty()) {
                markers.add(new Marker(metadata, info.pos()));
            }
        }
        return markers;
    }
}
