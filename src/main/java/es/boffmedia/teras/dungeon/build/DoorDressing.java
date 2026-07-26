package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorStyle;
import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.RoomType;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the frame around a doorway.
 *
 * <p>The opening is untouched — {@code anchoPuerta} × {@code altoPuerta}, exactly where
 * {@link DoorCarver} cuts it. Widening it would move the reserved apron in every room ever
 * authored, so the whole improvement is spent on the two surfaces that are free:</p>
 *
 * <ul>
 *   <li>the <b>wall plane</b>, which {@code RoomAudit} exempts from the apron rule, so a ring drawn
 *       there costs no floor space at all; and</li>
 *   <li>the two <b>columns beside the opening</b> — one outside the doorway band on each side —
 *       which are not reserved, so a jamb standing one block proud of the wall costs two tiles of
 *       footprint that nothing was allowed to walk through anyway.</li>
 * </ul>
 *
 * <p>Both faces of a door get the same style, chosen by the more special of the two rooms
 * ({@link DoorStyle#winner}), so the frame is a sign read from the corridor and a place-marker read
 * from inside. Secret and super-secret doorways are never dressed anywhere in here: a frame on a
 * secret is a secret with a sign over it.</p>
 */
public final class DoorDressing {
    private DoorDressing() {}

    /** How far the relief stands out of the wall. One block: enough for shape, cheap enough to be free. */
    private static final int RELIEF = 1;

    /**
     * Parsed block states, by the id string that produced them. The dressing writes on the order of
     * a hundred blocks per door and a floor has dozens of doors, all drawn from a handful of ids —
     * so this is parsed once per id per server rather than once per block.
     */
    private static final Map<String, BlockState> CACHE = new HashMap<>();

    /**
     * One opening in world coordinates, with the wall it pierces reduced to two numbers.
     *
     * <p>Everything a frame needs to know, and nothing about how the opening was decided — which is
     * what lets the cell doorway and the grand door between two 2×2s share every line of drawing
     * code. They differ only in where their span comes from: one is centred in a cell, the other on
     * the seam between two.</p>
     *
     * @param dir       the face the wall lies on: EAST means the wall runs along Z, SOUTH along X
     * @param wall      the coordinate of wall layer 0 — the min-cell side's own column. Layer 1 is
     *                  the neighbour's, one step further along {@code dir}
     * @param low,high  the opening's span along the wall, inclusive
     * @param baseY     the floor of the cell; the opening occupies {@code baseY+1 .. baseY+height}
     */
    public record Opening(GridDir dir, int wall, int low, int high, int baseY, int height) {

        /** The doorway {@link DoorCarver} cuts for one edge, centred in its cell. */
        public static Opening of(BlockPos origin, DoorEdge door, int roomSize,
                                 int doorWidth, int doorHeight) {
            GridPos cell = door.cell();
            int inset = (roomSize - doorWidth) / 2;
            int baseX = origin.getX() + cell.x() * roomSize;
            int baseZ = origin.getZ() + cell.y() * roomSize;
            boolean east = door.dir() == GridDir.EAST;
            int wall = (east ? baseX : baseZ) + roomSize - 1;
            int alongBase = (east ? baseZ : baseX) + inset;
            return new Opening(door.dir(), wall, alongBase, alongBase + doorWidth - 1,
                    origin.getY(), doorHeight);
        }

        /**
         * The grand door: one wide opening centred on the seam between the two cells of a full
         * shared face, which is what makes it symmetric across both rooms rather than two openings
         * with a pillar between them.
         */
        public static Opening grand(BlockPos origin, List<DoorEdge> faceEdges, int roomSize,
                                    int width, int height) {
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            for (DoorEdge edge : faceEdges) {
                minX = Math.min(minX, edge.cell().x());
                minZ = Math.min(minZ, edge.cell().y());
            }
            boolean east = faceEdges.get(0).dir() == GridDir.EAST;
            int wall = origin.get(east ? net.minecraft.core.Direction.Axis.X
                    : net.minecraft.core.Direction.Axis.Z)
                    + (east ? minX : minZ) * roomSize + roomSize - 1;
            int seam = (east ? origin.getZ() + (minZ + 1) * roomSize
                    : origin.getX() + (minX + 1) * roomSize);
            int half = width / 2;
            return new Opening(faceEdges.get(0).dir(), wall, seam - half, seam + width - half - 1,
                    origin.getY(), height);
        }

        /** One block of the opening's cross-section, in world space. */
        public BlockPos at(int wallOffset, int along, int y) {
            return dir == GridDir.EAST
                    ? new BlockPos(wall + wallOffset, baseY + y, along)
                    : new BlockPos(along, baseY + y, wall + wallOffset);
        }
    }

    /**
     * The style a doorway wears, or null when it must not be dressed at all.
     *
     * @param pisoStyle the piso's own ordinary frame, or null for the shipped fallback
     */
    public static DoorStyle styleOf(DoorEdge door, DoorStyle pisoStyle) {
        if (!dressed(door)) {
            return null;
        }
        return styleOf(DoorStyle.winner(door.from().type(), door.to().type()), pisoStyle);
    }

    /** The style for a door into {@code type}: the dungeon-wide one, or the piso's ordinary frame. */
    public static DoorStyle styleOf(RoomType type, DoorStyle pisoStyle) {
        DoorStyle special = DungeonsConfig.doorStyle(type);
        if (special != null) {
            return special;
        }
        return pisoStyle != null ? pisoStyle : DungeonsConfig.doorStyle();
    }

    /**
     * Whether this doorway is dressed at build time.
     *
     * <p>Three are not, for two reasons. The secrets must not exist as far as anyone can see. The
     * sala del sello is revealed by the boss's death, so its frame is written at the reveal — a
     * framed wall would announce the chamber a whole fight early.</p>
     */
    private static boolean dressed(DoorEdge door) {
        return switch (door.kind()) {
            case OPEN, BOSS, CURSE, DEVIL, GRACIA -> true;
            case SECRET_CRACK, HIDDEN, SELLO -> false;
        };
    }

    /**
     * Writes both faces of one opening.
     *
     * <p>Runs after the templates are pasted and after {@link DoorCarver} has cut the opening, so it
     * always wins over whatever a room authored in those blocks. That is the trade the frame makes:
     * a consistent door language costs the author the wall immediately around an opening, which is
     * the one place they were already forbidden to build anything load-bearing.</p>
     */
    public static void dress(ServerLevel level, Opening opening, DoorStyle style) {
        if (style == null) {
            return;
        }
        // Each face is drawn into its own room, so a frame is never half in one room and half in
        // the other — layer 0 belongs to the min-cell side, layer 1 to its neighbour.
        face(level, opening, style, 0, -RELIEF);
        face(level, opening, style, 1, RELIEF);
    }

    private static void face(ServerLevel level, Opening opening, DoorStyle style,
                             int layer, int out) {
        BlockState marco = state(style.marco());
        BlockState acento = state(style.acento());
        BlockState umbral = state(style.umbral());
        int low = opening.low() - 1;
        int high = opening.high() + 1;
        int keystone = (opening.low() + opening.high()) / 2;
        int lintel = opening.height() + 1;

        // The ring, in the wall plane: threshold under the opening, jambs beside it, lintel over it,
        // and the accent at the corners and the keystone.
        for (int along = low; along <= high; along++) {
            boolean edge = along == low || along == high;
            put(level, opening.at(layer, along, 0), edge ? acento : umbral);
            put(level, opening.at(layer, along, lintel), edge || along == keystone ? acento : marco);
            if (edge) {
                for (int h = 1; h <= opening.height(); h++) {
                    put(level, opening.at(layer, along, h), marco);
                }
            }
        }
        if (!DungeonsConfig.doorRelief()) {
            // Flat mode still gets its lamps, in the wall plane over the ring's corners. Losing the
            // relief is a look; losing the light would be a floor that navigates differently.
            if (style.lit()) {
                BlockState luz = state(style.luz());
                put(level, opening.at(layer, low, lintel + 1), luz);
                put(level, opening.at(layer, high, lintel + 1), luz);
            }
            return;
        }

        // The relief, one block into the room: two jambs, a beam across them, and the lamps.
        int jambTop = style.alta() ? opening.height() + 1 : opening.height();
        int beam = jambTop + 1;
        for (int h = 1; h <= jambTop; h++) {
            put(level, opening.at(layer + out, low, h), marco);
            put(level, opening.at(layer + out, high, h), marco);
        }
        for (int along = low; along <= high; along++) {
            put(level, opening.at(layer + out, along, beam), marco);
        }
        if (style.alta()) {
            // The step: a second, narrower course over the beam. Only the boss and the sala del
            // sello get it, and it is the whole difference between a door and a gate you remember.
            for (int along = opening.low(); along <= opening.high(); along++) {
                put(level, opening.at(layer + out, along, beam + 1), acento);
            }
        }
        if (style.lit()) {
            BlockState luz = state(style.luz());
            put(level, opening.at(layer + out, low, beam + 1), luz);
            put(level, opening.at(layer + out, high, beam + 1), luz);
        }
    }

    /**
     * Writes the gate of a doorway that is held shut for the whole floor — the pacto's and the
     * Orden's. Both wall layers, because these are not a portcullis that falls: they are a wall with
     * a mark on it, and a wall you can already see the far room through is a promise half spent. The
     * mark goes in the centre of each face.
     */
    public static void gate(ServerLevel level, Opening opening, DoorStyle style) {
        BlockState panel = state(style.porton());
        BlockState marca = style.marca().isBlank() ? panel : state(style.marca());
        int midAlong = (opening.low() + opening.high()) / 2;
        int midH = (opening.height() + 1) / 2;
        for (int layer = 0; layer < 2; layer++) {
            for (int along = opening.low(); along <= opening.high(); along++) {
                for (int h = 1; h <= opening.height(); h++) {
                    put(level, opening.at(layer, along, h),
                            along == midAlong && h == midH ? marca : panel);
                }
            }
        }
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        if (state != null) {
            level.setBlock(pos, state, 2);
        }
    }

    /**
     * A configured block id, with its state properties if it carries any — a copper bulb has to be
     * written {@code [lit=true]} or it is a lamp that is off, which is exactly the sort of thing
     * that looks like a texture bug rather than a config one.
     *
     * <p>An id that does not resolve logs once and is skipped rather than substituted. A frame with
     * a hole in it is a visible mistake somebody fixes; a frame silently built out of stone is a
     * palette that quietly never happened.</p>
     */
    private static BlockState state(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return CACHE.computeIfAbsent(id, key -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), key, false)
                        .blockState();
            } catch (Exception e) {
                Teras.LOGGER.warn("Dungeons: door block '{}' does not resolve, so it is left out of "
                        + "the frame: {}", key, e.toString());
                return null;
            }
        });
    }

    /**
     * A configured id, or {@code fallback} when it does not resolve. Shared with the seal and the
     * crack so every block a door is built out of goes through one parser and one cache — and so
     * all of them accept state properties, not just the ones in the frame.
     */
    public static BlockState parse(String id, BlockState fallback) {
        BlockState state = state(id);
        return state == null ? fallback : state;
    }

    /** Dropped when the server stops, so a config edit between worlds is never served from here. */
    public static void clearCache() {
        CACHE.clear();
    }
}
