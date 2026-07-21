package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * A floor standing in a world: where it is, at which cell pitch it was built (captured here so a
 * later config change can never desync a discard from what was placed), and the markers each room
 * contributed for the run stage to consume. The legacy command pasted at the player's feet with no
 * record and no way back — this is the record.
 */
public record BuiltDungeon(
        int id,
        ResourceKey<Level> dimension,
        BlockPos origin,
        DungeonLayout layout,
        es.boffmedia.teras.dungeon.piso.FloorPlan plan,
        int roomSize,
        int roomHeight,
        Map<Room, List<TemplateMarkers.Marker>> markers) {

    /**
     * The place this floor is. Carried on the built floor rather than looked up again at each
     * consumer: the piso is drawn from a weighted list, so a second lookup is a second draw and two
     * consumers can disagree about what floor the party is standing on.
     */
    public es.boffmedia.teras.dungeon.piso.FloorDef piso() {
        return plan.piso();
    }

    /** The tramo's multiplier for this floor, or the baseline when the floor was built bare. */
    public double dificultad() {
        return plan == null ? 1.0 : plan.dificultad();
    }

    /** World position of a grid cell's minimum corner. */
    public BlockPos cellOrigin(GridPos cell) {
        return origin.offset(cell.x() * roomSize, 0, cell.y() * roomSize);
    }

    /**
     * {@code pos} pulled to at least {@code margin} blocks inside the room's bounding box on X/Z
     * (Y untouched). Marker positions come from templates, and templates are authored — since the
     * room editor, in-game — so a marker one block from a wall is a matter of time: without the
     * clamp the trapdoor frame carves into the wall, loot pops inside a block, a boss spawns
     * embedded. Correct markers pass through unchanged.
     */
    public BlockPos clampInside(Room room, BlockPos pos, int margin) {
        int minCx = Integer.MAX_VALUE;
        int minCy = Integer.MAX_VALUE;
        int maxCx = Integer.MIN_VALUE;
        int maxCy = Integer.MIN_VALUE;
        for (GridPos cell : room.cells()) {
            minCx = Math.min(minCx, cell.x());
            maxCx = Math.max(maxCx, cell.x());
            minCy = Math.min(minCy, cell.y());
            maxCy = Math.max(maxCy, cell.y());
        }
        int x = Math.clamp(pos.getX(),
                origin.getX() + minCx * roomSize + margin,
                origin.getX() + (maxCx + 1) * roomSize - 1 - margin);
        int z = Math.clamp(pos.getZ(),
                origin.getZ() + minCy * roomSize + margin,
                origin.getZ() + (maxCy + 1) * roomSize - 1 - margin);
        return x == pos.getX() && z == pos.getZ() ? pos : new BlockPos(x, pos.getY(), z);
    }

    /**
     * Walkable center of a room's whole footprint, for teleports, loot and the trapdoor. For a
     * rectangle that is the middle of its bounding box — where a 2×2 boss chamber's hole belongs,
     * rather than the corner quadrant its anchor cell would give.
     *
     * <p>L-shapes have no interior middle (their bounding-box center lands in the quadrant they do
     * not own), so they take the center of their first real cell. Note that is not always the
     * anchor: {@code L_BOTTOM_RIGHT}'s offsets start at (1,0), so it never claims its own anchor
     * cell and centering on it used to point at a cell belonging to someone else.</p>
     */
    public BlockPos roomCenter(Room room) {
        List<GridPos> cells = room.cells();
        if (room.shape().isLShaped()) {
            return cellOrigin(cells.get(0)).offset(roomSize / 2, 1, roomSize / 2);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (GridPos cell : cells) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
        }
        return new BlockPos(
                origin.getX() + minX * roomSize + ((maxX - minX + 1) * roomSize) / 2,
                origin.getY() + 1,
                origin.getZ() + minY * roomSize + ((maxY - minY + 1) * roomSize) / 2);
    }

    /**
     * Where the party lands: the room's {@code inicio} marker, or its center when none was
     * authored.
     *
     * <p>The center is only correct while the middle of a room happens to be empty floor, which is
     * an accident of the placeholder boxes rather than a property of a room. A start chamber built
     * around a central feature — a plinth, an outcrop, a pillar — teleports the party <i>inside</i>
     * it, and a player standing in a block is pushed out in whichever direction the collision
     * resolves. The marker lets the builder say where arrival belongs; the fallback keeps every
     * template authored before it working.</p>
     */
    public BlockPos partySpawn(Room room) {
        for (TemplateMarkers.Marker marker : markers.getOrDefault(room, List.of())) {
            if (marker.kind().equals("inicio")) {
                return clampInside(room, marker.pos(), 1);
            }
        }
        return roomCenter(room);
    }
}
