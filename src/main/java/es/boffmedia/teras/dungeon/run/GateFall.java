package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DoorCarver;
import es.boffmedia.teras.dungeon.build.DoorDressing;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Opens and closes doors over a few ticks instead of stamping them in whole.
 *
 * <p>The sound of a room sealing was always there; the sight of it was not. A doorway that goes from
 * air to a full grid of bars in one tick reads as blocks appearing, and the cue playing over it
 * reads as unrelated. Written top course first over a few ticks it reads as a gate falling — and
 * the order matters more than the duration: the last course to land is the one at head height, so
 * the room closes on you rather than under you.</p>
 *
 * <p>The sala del sello's reveal is the same idea run backwards. It is the one door in a floor that
 * is a reward, and appearing whole is what made it read as a hole in the wall; carved outward from
 * the centre, it reads as the rock giving way.</p>
 *
 * <p>One of these per floor, drained from the move scan, which already runs every tick.</p>
 */
final class GateFall {

    /** One scheduled write, and when it lands. {@code room} is what a cancel matches on. */
    private record Step(long due, Room room, Runnable write) {}

    private final List<Step> pending = new ArrayList<>();

    /**
     * Schedules every walkable doorway of {@code room} shut. The top course is written immediately,
     * so a party watching the doorway sees it start closing on the same tick as the cue.
     */
    void drop(RunEngine.ActiveFloor floor, Room room, BlockState state, long now) {
        int height = DungeonsConfig.doorHeight();
        int step = Math.max(1, DungeonsConfig.sealCloseTicks() / Math.max(1, height));
        for (DoorEdge door : floor.built.layout().doorsOf(room)) {
            if (!door.kind().walkable()) {
                continue;
            }
            boolean minSide = DoorCarver.ownsNearPlane(door, room);
            for (int row = height; row >= 1; row--) {
                int course = row;
                at(now + (long) (height - row) * step, now, room,
                        () -> DoorCarver.fillDoorwayPlane(floor.level, floor.built.origin(), door,
                                state, floor.built.roomSize(), DungeonsConfig.doorWidth(),
                                height, minSide, course));
            }
        }
    }

    /**
     * Opens the grand door of the sala del sello, a column at a time from the centre out, and hangs
     * its frame on the wall first so the arch is there before the rock inside it goes.
     */
    void reveal(RunEngine.ActiveFloor floor, DoorDressing.Opening opening,
                es.boffmedia.teras.dungeon.model.DoorStyle style, int ticks, long now) {
        DoorDressing.dress(floor.level, opening, style);
        int centre = (opening.low() + opening.high()) / 2;
        int reach = Math.max(1, opening.high() - centre);
        int step = Math.max(1, ticks / (reach + 1));
        for (int along = opening.low(); along <= opening.high(); along++) {
            int column = along;
            at(now + (long) Math.abs(along - centre) * step, now, null, () -> {
                for (int layer = 0; layer < 2; layer++) {
                    for (int h = 1; h <= opening.height(); h++) {
                        BlockPos pos = opening.at(layer, column, h);
                        floor.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            });
        }
    }

    /** Lands every write that is due. */
    void tick(RunEngine.ActiveFloor floor, long now) {
        if (pending.isEmpty()) {
            return;
        }
        for (Iterator<Step> it = pending.iterator(); it.hasNext();) {
            Step step = it.next();
            if (step.due() > now) {
                continue;
            }
            step.write().run();
            it.remove();
        }
    }

    /**
     * Forgets anything still falling into {@code room}'s doorways.
     *
     * <p>The window is small — a room is sealed for the length of a fight — but it is not zero: a
     * wave killed inside those few ticks would open the doors and then have a course land in one of
     * them, leaving a room that is cleared and still has a bar across its exit.</p>
     */
    void cancel(Room room) {
        pending.removeIf(step -> step.room() == room);
    }

    /** Immediate when it is already due, which is how the first course of a fall lands on cue. */
    private void at(long due, long now, Room room, Runnable write) {
        if (due <= now) {
            write.run();
            return;
        }
        pending.add(new Step(due, room, write));
    }
}
