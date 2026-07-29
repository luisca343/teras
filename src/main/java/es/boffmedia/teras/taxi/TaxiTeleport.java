package es.boffmedia.teras.taxi;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.Set;

/**
 * Moving a player to a stop, and deciding whether that is safe to do at all.
 *
 * <p>The arrival is re-checked at travel time, not trusted from creation time: a stop authored on
 * open ground a year ago can be under a new building, flooded, or over a hole someone dug. The
 * dungeon system already paid for this lesson — a player put down where there is no floor simply
 * falls and dies, and it reads as the teleport having worked.</p>
 *
 * <p>Every refusal happens <b>before</b> the player is touched, so a trip that fails leaves the game
 * exactly as it was. That is what lets the SmartRotom taxi charge the fare <i>after</i> this call
 * instead of before it: a refusal here is proof the player did not travel, so nobody is billed for a
 * trip that did not happen (docs/TAXI.md).</p>
 */
public final class TaxiTeleport {
    private TaxiTeleport() {}

    /** How far above and below the stored point we will look for solid ground. */
    private static final int SEARCH_RADIUS = 4;

    /** Why a trip could not happen. {@code OK} is the only value that moves anybody. */
    public enum Result {
        OK,
        /** The player is not on the server. */
        OFFLINE,
        /** Nowhere safe to stand at or near the stop. */
        UNSAFE,
        /** The player is inside a dungeon run; pulling them out would strand the run's bookkeeping. */
        BUSY
    }

    /**
     * Puts {@code player} at {@code stop}. <b>Server thread only</b> — this is game state.
     *
     * @return what happened; anything other than {@link Result#OK} means nothing was changed
     */
    public static Result travel(ServerPlayer player, TaxiStop stop) {
        if (player == null) {
            return Result.OFFLINE;
        }
        if (es.boffmedia.teras.dungeon.run.DungeonHealth.isInRun(player)) {
            // A run owns where its party is standing: its journal, its sealing and its return
            // bookkeeping all assume nobody leaves except through the run's own exits.
            return Result.BUSY;
        }
        ServerLevel level = player.server.overworld();
        Double groundY = safeGroundY(level, stop);
        if (groundY == null) {
            return Result.UNSAFE;
        }
        // A passenger still on a mount would leave it behind or drag it along, depending on the
        // vehicle; neither is what a taxi does.
        player.stopRiding();
        player.teleportTo(level, stop.x(), groundY, stop.z(), stop.yaw(), stop.pitch());
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0F;
        return Result.OK;
    }

    /**
     * Whether a stop could be created here — the same test travel will apply later, so an admin is
     * told at creation rather than a passenger finding out at arrival.
     */
    public static boolean isSafeToCreate(ServerLevel level, TaxiStop stop) {
        return safeGroundY(level, stop) != null;
    }

    /**
     * The Y a player can stand on at this stop, or {@code null} if there is none.
     *
     * <p>Searches the stored Y first, then outward: a stop is usually still fine, and when it is not,
     * a metre of drift is the common case (a re-laid path, a new slab).</p>
     */
    private static Double safeGroundY(ServerLevel level, TaxiStop stop) {
        int x = (int) Math.floor(stop.x());
        int z = (int) Math.floor(stop.z());
        int origin = (int) Math.floor(stop.y());
        for (int offset = 0; offset <= SEARCH_RADIUS; offset++) {
            // Down first: a stop swallowed by a new floor is more common than one buried from below.
            Double below = standableAt(level, x, origin - offset, z);
            if (below != null) {
                return below;
            }
            if (offset > 0) {
                Double above = standableAt(level, x, origin + offset, z);
                if (above != null) {
                    return above;
                }
            }
        }
        return null;
    }

    /** Blocks a passenger must never be dropped into, whatever the geometry says. */
    private static final Set<String> DEADLY = Set.of("minecraft:lava", "minecraft:fire",
            "minecraft:soul_fire", "minecraft:magma_block", "minecraft:cactus", "minecraft:campfire",
            "minecraft:soul_campfire", "minecraft:sweet_berry_bush", "minecraft:wither_rose",
            "minecraft:powder_snow");

    /** {@code y} if a player fits standing there with solid ground under them, else {@code null}. */
    private static Double standableAt(ServerLevel level, int x, int y, int z) {
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 1) {
            return null;
        }
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = feet.above();
        BlockPos ground = feet.below();

        if (!isPassable(level, feet) || !isPassable(level, head)) {
            return null;
        }
        BlockState below = level.getBlockState(ground);
        if (below.getCollisionShape(level, ground).isEmpty()) {
            return null; // Nothing to stand on: this is the fall-to-your-death case.
        }
        if (isDeadly(level, ground) || isDeadly(level, feet) || isDeadly(level, head)) {
            return null;
        }
        return y + 0.01D;
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        FluidState fluid = state.getFluidState();
        // Standing in water is survivable and sometimes intended (a dock); lava never is.
        return fluid.isEmpty() || !fluid.is(net.minecraft.tags.FluidTags.LAVA);
    }

    private static boolean isDeadly(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return DEADLY.contains(id) || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }
}
