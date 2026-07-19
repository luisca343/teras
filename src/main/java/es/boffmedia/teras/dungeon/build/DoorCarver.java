package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
     * Sets every walkable doorway of {@code room} (OPEN and BOSS edges — never the secret cracks)
     * to {@code state}: air to open, the seal block while combat runs.
     */
    public static void setRoomDoors(ServerLevel level, BuiltDungeon built, Room room, BlockState state) {
        for (DoorEdge door : built.layout().doorsOf(room)) {
            if (door.kind() == DoorKind.OPEN || door.kind() == DoorKind.BOSS) {
                fillDoorway(level, built.origin(), door, state,
                        built.roomSize(), DungeonsConfig.doorWidth(), DungeonsConfig.doorHeight());
            }
        }
    }
}
