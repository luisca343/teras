package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.SeededRng;
import es.boffmedia.teras.dungeon.piso.DecorTables;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a {@code decoracion:*} marker into what its piso's table says grows there.
 *
 * <p>The marker carries its surface — {@code decoracion:techo} — and the piso answers with a
 * weighted table for that surface, so one authored room varies between runs without a second
 * template and a ceiling can never sprout a floor mushroom. The roll is seeded from the floor and
 * the block position, so a printed seed decorates identically every time.</p>
 */
public final class Decorator {
    private Decorator() {}

    /** The marker prefix and the three surfaces it can name. */
    public static final String PREFIX = "decoracion:";

    /** True for {@code decoracion:techo} and the like — the markers this class owns. */
    public static boolean isDecorMarker(String kind) {
        return kind.startsWith(PREFIX);
    }

    /**
     * The block a decoration marker becomes, or air when the piso declares no table for that surface
     * (the marker is then simply cleared, exactly as an unhandled marker would be).
     *
     * @param baseSeed the floor's seed, so the choice is reproducible
     */
    public static BlockState blockFor(ServerLevel level, DecorTables tables, String markerKind,
                                      BlockPos pos, long baseSeed) {
        String surface = markerKind.substring(PREFIX.length());
        var table = tables.forSurface(surface);
        if (table.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        SeededRng rng = new SeededRng(DungeonSeeds.derive(baseSeed,
                0xDEC0L ^ (long) pos.hashCode()));
        DecorTables.DecorRef ref = pick(table, rng);
        if (ref.isStructure()) {
            // Placing a structure is a separate paste, not a block swap, so the marker loop handles
            // it directly. Reaching here means a table mixed the two; the block path cannot honour a
            // structure, so it clears rather than guess.
            return Blocks.AIR.defaultBlockState();
        }
        return parse(level, ref.bloque());
    }

    /** Whether the rolled decoration at {@code pos} is a structure rather than a block. */
    public static String structureFor(DecorTables tables, String markerKind, BlockPos pos,
                                      long baseSeed) {
        String surface = markerKind.substring(PREFIX.length());
        var table = tables.forSurface(surface);
        if (table.isEmpty()) {
            return null;
        }
        SeededRng rng = new SeededRng(DungeonSeeds.derive(baseSeed,
                0xDEC0L ^ (long) pos.hashCode()));
        DecorTables.DecorRef ref = pick(table, rng);
        return ref.isStructure() ? ref.estructura() : null;
    }

    private static DecorTables.DecorRef pick(java.util.List<DecorTables.DecorRef> table,
                                             SeededRng rng) {
        int total = 0;
        for (DecorTables.DecorRef ref : table) {
            total += ref.peso();
        }
        int roll = rng.between(1, Math.max(1, total));
        for (DecorTables.DecorRef ref : table) {
            roll -= ref.peso();
            if (roll <= 0) {
                return ref;
            }
        }
        return table.get(table.size() - 1);
    }

    /**
     * A block id with an optional state, e.g. {@code minecraft:pointed_dripstone[vertical_direction=up]}.
     * A bad id clears rather than fails the floor: a mistyped decoration block should cost that one
     * marker, not leave a run waiting on a floor that never lands.
     */
    private static BlockState parse(ServerLevel level, String id) {
        try {
            return BlockStateParser.parseForBlock(
                    level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), id, false)
                    .blockState();
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: decoracion block '{}' did not parse — cleared: {}",
                    id, e.toString());
            return Blocks.AIR.defaultBlockState();
        }
    }
}
