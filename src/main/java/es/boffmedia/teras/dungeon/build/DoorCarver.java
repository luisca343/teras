package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Writes doorway tunnels — the two back-to-back wall layers on a shared cell boundary: the last
 * block column of one cell and the first of the next. One implementation serves the materializer
 * (initial carve) and the run engine (sealing a room in combat, reopening it on clear); the same
 * math in two places is how the legacy paster ended up with two size conventions.
 */
public final class DoorCarver {
    private DoorCarver() {}

    public static void fillDoorway(ServerLevel level, BlockPos origin, DoorEdge door,
                                   BlockState state, int roomSize, int doorWidth, int doorHeight) {
        GridPos cell = door.cell();
        int inset = (roomSize - doorWidth) / 2;
        int baseX = origin.getX() + cell.x() * roomSize;
        int baseZ = origin.getZ() + cell.y() * roomSize;
        for (int w = 0; w < doorWidth; w++) {
            for (int h = 1; h <= doorHeight; h++) {
                for (int depth = 0; depth < 2; depth++) {
                    BlockPos pos = door.dir() == GridDir.EAST
                            ? new BlockPos(baseX + roomSize - 1 + depth,
                                    origin.getY() + h, baseZ + inset + w)
                            : new BlockPos(baseX + inset + w,
                                    origin.getY() + h, baseZ + roomSize - 1 + depth);
                    level.setBlock(pos, state, 2);
                }
            }
        }
    }

    /**
     * Writes one wall layer of a doorway, and optionally one course of it.
     *
     * <p>A gate is written into the sealing room's <b>own</b> wall column rather than through both
     * layers. Filling the whole tunnel is why sealing used to read as the hole getting plugged: with
     * one layer you are standing in a doorway with a gate in front of you, the frame around it is
     * still visible, and from the corridor outside you can see that the room next door is shut
     * rather than that the wall grew back.</p>
     *
     * @param minSide whether the room doing the sealing is the edge's {@code from} — the min-cell
     *                side, which owns wall layer 0. The neighbour owns layer 1
     * @param row     the single course to write, or 0 for all of them. This is what lets a gate fall
     *                top course first instead of appearing whole
     */
    public static void fillDoorwayPlane(ServerLevel level, BlockPos origin, DoorEdge door,
                                        BlockState state, int roomSize, int doorWidth,
                                        int doorHeight, boolean minSide, int row) {
        GridPos cell = door.cell();
        int inset = (roomSize - doorWidth) / 2;
        int baseX = origin.getX() + cell.x() * roomSize;
        int baseZ = origin.getZ() + cell.y() * roomSize;
        int depth = minSide ? 0 : 1;
        for (int w = 0; w < doorWidth; w++) {
            for (int h = 1; h <= doorHeight; h++) {
                if (row != 0 && h != row) {
                    continue;
                }
                BlockPos pos = door.dir() == GridDir.EAST
                        ? new BlockPos(baseX + roomSize - 1 + depth,
                                origin.getY() + h, baseZ + inset + w)
                        : new BlockPos(baseX + inset + w,
                                origin.getY() + h, baseZ + roomSize - 1 + depth);
                level.setBlock(pos, state, 2);
            }
        }
    }

    /**
     * Writes only the top row of a doorway — the lintel course.
     *
     * <p>For the curse door's spikes: the opening has to stay walkable, so they hang from the
     * header rather than filling the tunnel. With the default three-block height that leaves two
     * to duck through, which is what makes it read as a mouth instead of a wall.</p>
     */
    public static void fillDoorwayRow(ServerLevel level, BlockPos origin, DoorEdge door,
                                      BlockState state, int roomSize, int doorWidth, int doorHeight) {
        GridPos cell = door.cell();
        int inset = (roomSize - doorWidth) / 2;
        int baseX = origin.getX() + cell.x() * roomSize;
        int baseZ = origin.getZ() + cell.y() * roomSize;
        for (int w = 0; w < doorWidth; w++) {
            for (int depth = 0; depth < 2; depth++) {
                BlockPos pos = door.dir() == GridDir.EAST
                        ? new BlockPos(baseX + roomSize - 1 + depth,
                                origin.getY() + doorHeight, baseZ + inset + w)
                        : new BlockPos(baseX + inset + w,
                                origin.getY() + doorHeight, baseZ + roomSize - 1 + depth);
                level.setBlock(pos, state, 2);
            }
        }
    }

    /**
     * Clears a shallow approach corridor into the boss room in front of a just-opened seal door, so
     * no authored arena prop can stand between the party and the way down (the "guarantee access"
     * pass). Mirrors the opening's geometry — {@code width} centered on the middle of the shared
     * face, {@code height} tall — shifted {@code depth} columns into whichever side holds the boss,
     * read from the edges' {@code from}/{@code to} rooms ({@code from} is always the min-cell side).
     *
     * <p>Serves both reveals: a grand door passes its two face edges and centers on their seam; a
     * fallback single edge centers on its one cell. Boss rooms are at least a cell deep, so a
     * three-block reach never punches through the far wall.</p>
     *
     * <p>The opening itself is no longer carved here — {@code GateFall.reveal} opens it a column at
     * a time over {@code DoorDressing.Opening.grand}, which is now the single derivation of where
     * the grand door is. This clears in front of it and nothing else.</p>
     */
    public static void clearSealApproach(ServerLevel level, BlockPos origin,
                                         java.util.List<DoorEdge> faceEdges,
                                         int roomSize, int width, int height, int depth) {
        if (faceEdges.isEmpty()) {
            return;
        }
        DoorEdge first = faceEdges.get(0);
        GridDir dir = first.dir();
        boolean bossOnMinSide = first.from().type() == RoomType.BOSS;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (DoorEdge edge : faceEdges) {
            minX = Math.min(minX, edge.cell().x());
            minZ = Math.min(minZ, edge.cell().y());
        }
        int span = faceEdges.size();
        int half = width / 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        if (dir == GridDir.EAST) {
            int baseX = origin.getX() + minX * roomSize;
            int centerZ = origin.getZ() + minZ * roomSize + (span * roomSize) / 2;
            for (int d = 1; d <= depth; d++) {
                int x = bossOnMinSide ? baseX + roomSize - 1 - d : baseX + roomSize + d;
                for (int h = 1; h <= height; h++) {
                    for (int w = -half; w < width - half; w++) {
                        level.setBlock(new BlockPos(x, origin.getY() + h, centerZ + w), air, 2);
                    }
                }
            }
        } else {
            int baseZ = origin.getZ() + minZ * roomSize;
            int centerX = origin.getX() + minX * roomSize + (span * roomSize) / 2;
            for (int d = 1; d <= depth; d++) {
                int z = bossOnMinSide ? baseZ + roomSize - 1 - d : baseZ + roomSize + d;
                for (int h = 1; h <= height; h++) {
                    for (int w = -half; w < width - half; w++) {
                        level.setBlock(new BlockPos(centerX + w, origin.getY() + h, z), air, 2);
                    }
                }
            }
        }
    }

    /**
     * Whether {@code pos} lies in the exact block volume {@link #fillDoorway} writes for
     * {@code door} — the test the secret-wall interaction uses, so what opens is precisely what
     * was filled, never a lookalike block elsewhere in the wall.
     */
    public static boolean doorwayContains(BlockPos origin, DoorEdge door, BlockPos pos,
                                          int roomSize, int doorWidth, int doorHeight) {
        int h = pos.getY() - origin.getY();
        if (h < 1 || h > doorHeight) {
            return false;
        }
        GridPos cell = door.cell();
        int inset = (roomSize - doorWidth) / 2;
        int baseX = origin.getX() + cell.x() * roomSize;
        int baseZ = origin.getZ() + cell.y() * roomSize;
        if (door.dir() == GridDir.EAST) {
            return (pos.getX() == baseX + roomSize - 1 || pos.getX() == baseX + roomSize)
                    && pos.getZ() >= baseZ + inset && pos.getZ() < baseZ + inset + doorWidth;
        }
        return (pos.getZ() == baseZ + roomSize - 1 || pos.getZ() == baseZ + roomSize)
                && pos.getX() >= baseX + inset && pos.getX() < baseX + inset + doorWidth;
    }

    /**
     * Whether {@code room} is the min-cell side of {@code door} — the side that owns wall layer 0.
     *
     * <p>Identity, not type: a multi-cell room is one object referenced from every cell it occupies,
     * so this is exact even where two rooms of the same type meet.</p>
     */
    public static boolean ownsNearPlane(DoorEdge door, Room room) {
        return door.from() == room;
    }

    /**
     * Opens every walkable doorway of {@code room} (OPEN, BOSS and CURSE edges — never the secret
     * cracks), clearing both wall layers.
     *
     * <p>Both, deliberately, where a gate only ever fills one: opening has to undo whatever is
     * there, and being generous about it costs nothing. A gate left behind in the far layer would
     * be a doorway that looks open from one room and shut from the other.</p>
     */
    public static void setRoomDoors(ServerLevel level, BuiltDungeon built, Room room, BlockState state) {
        for (DoorEdge door : built.layout().doorsOf(room)) {
            if (!door.kind().walkable()) {
                continue;
            }
            fillDoorway(level, built.origin(), door, state,
                    built.roomSize(), DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
            if (door.kind() == DoorKind.CURSE && state.isAir()) {
                // Reopening a curse door writes air over the whole tunnel, fangs included. Without
                // this the spikes survive exactly until the first fight next door — and a warning
                // that quietly disappears is worse than never having been there.
                fillDoorwayRow(level, built.origin(), door, spikeState(), built.roomSize(),
                        DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
            }
        }
    }

    /**
     * The fangs over a curse doorway.
     *
     * <p>Dripstone has its tip pointed down explicitly: its default state hangs <i>upward</i>, so
     * the lintel would sprout stalagmites growing into the ceiling. Any other configured block is
     * used as it comes.</p>
     */
    public static BlockState spikeState() {
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse(DungeonsConfig.spikeBlock()));
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            block = net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE;
        }
        BlockState state = block.defaultBlockState();
        if (block instanceof net.minecraft.world.level.block.PointedDripstoneBlock) {
            state = state
                    .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION,
                            net.minecraft.core.Direction.DOWN)
                    .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.THICKNESS,
                            net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP);
        }
        return state;
    }
}
