package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The way up: riding el ascensor out of the dungeon with everything the party is carrying.
 *
 * <h2>What it is for</h2>
 *
 * <p>A tramo-boundary floor has <b>two exits</b>, and that is the whole point of the room. The
 * trampilla takes you deeper with everything you hold and everything you stand to lose; the
 * ascensor cashes it in. Coins become ₽ exactly as they do at the end of a dungeon — leaving early
 * is a <i>completion</i>, not an abandonment — so the question the party argues over is real:
 * push one more tramo, or bank it.</p>
 *
 * <p>Mid-tramo floors have no lift, and that is deliberate. Once you drop into a tramo you play it
 * to its end, which is what makes a tramo a unit and keeps the decision at a place rather than at
 * any moment.</p>
 *
 * <h2>Why it is a hold and not a click</h2>
 *
 * <p>Ending the run ends it <b>for everybody</b>. A single click would let one member cash out the
 * other three's floor, which is not a decision, it is griefing — the same reasoning
 * {@link CurseMarket} already applies to a party-wide affliction, and this reuses its shape: a
 * three-second hold the server itself ticks, alive only while the holder stays in reach with the
 * cage in their sights, with everyone else warned by name while the bar fills. Three seconds is
 * long enough to shout.</p>
 *
 * <h2>Why it is not a character</h2>
 *
 * <p>La sala del sello already stages el Acreedor and la Orden, and that fork is what the room is
 * for. A third talking head at the same beat would dilute it, so the lift stays machinery: a panel
 * you hold, not somebody you ask.</p>
 */
final class ElevatorRide {

    /** Ticks of sustained attention before the party is taken out. Matches the curse market's. */
    private static final int HOLD_TICKS = 60;

    /** How far the holder may stand from the cage. Generous: the fixture is seven blocks wide. */
    private static final double HOLD_REACH = 6.0;

    /** How far the view ray may pass from the car's middle. */
    private static final double HOLD_AIM_SLACK = 3.0;

    /** How close a click has to land to the cage to count as pressing it. */
    private static final int RANGE = 4;

    private final Map<UUID, Long> holds = new HashMap<>();

    /**
     * Answers a right-click in la sala del sello. True when it was ours, so the caller stops.
     *
     * <p>Gated on the seal being open — which is to say on the boss being dead. The room is barred
     * until then and nobody can be standing here, so this is belt and braces rather than the rule
     * that keeps them out.</p>
     */
    boolean tryRide(RunEngine.ActiveFloor floor, ServerPlayer player, Room room, BlockPos clicked) {
        if (!floor.core.isTrapdoorOpen() || !RunEngine.hasMarker(floor, room, "ascensor")) {
            return false;
        }
        BlockPos cage = RunEngine.markerPos(floor, room, "ascensor");
        if (!RunEngine.isAtFixture(cage, clicked, RANGE)) {
            return false;
        }
        if (holds.containsKey(player.getUUID())) {
            // Already charging; tick() drives it and the client repeats clicks while held.
            return true;
        }
        holds.put(player.getUUID(), (long) floor.level().getServer().getTickCount());
        RunEngine.message(floor, "§b" + player.getName().getString()
                + " está llamando al ascensor. §7La expedición terminaría aquí…");
        return true;
    }

    /** Advances every open hold; runs once per server tick while the floor is live. */
    void tick(RunEngine.ActiveFloor floor) {
        if (holds.isEmpty()) {
            return;
        }
        Room room = exitRoom(floor);
        if (room == null) {
            holds.clear();
            return;
        }
        BlockPos cage = RunEngine.markerPos(floor, room, "ascensor");
        long tick = floor.level().getServer().getTickCount();
        Iterator<Map.Entry<UUID, Long>> it = holds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = floor.level().getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.serverLevel() != floor.level()) {
                it.remove();
                continue;
            }
            if (!engaged(player, cage)) {
                it.remove();
                player.displayClientMessage(Component.literal("§8El ascensor se queda esperando."),
                        true);
                continue;
            }
            long elapsed = tick - entry.getValue();
            if (elapsed >= HOLD_TICKS) {
                it.remove();
                commit(floor, player);
                return;
            }
            int filled = (int) (10 * elapsed / HOLD_TICKS);
            player.displayClientMessage(Component.literal("§bSubiendo §8[§b" + "▉".repeat(filled)
                    + "§8" + "▁".repeat(10 - filled) + "§8]"), true);
        }
    }

    /** Still at the cage: close enough, with the view ray passing by the car. */
    private static boolean engaged(ServerPlayer player, BlockPos cage) {
        Vec3 centre = Vec3.atBottomCenterOf(cage).add(0, 1.0, 0);
        Vec3 eye = player.getEyePosition();
        if (eye.distanceTo(centre) > HOLD_REACH) {
            return false;
        }
        Vec3 look = player.getViewVector(1.0F);
        double along = Math.max(0.0, centre.subtract(eye).dot(look));
        return eye.add(look.scale(along)).distanceTo(centre) <= HOLD_AIM_SLACK;
    }

    private static Room exitRoom(RunEngine.ActiveFloor floor) {
        for (Room room : floor.built().layout().rooms()) {
            if (room.type() == es.boffmedia.teras.dungeon.model.RoomType.EXIT) {
                return room;
            }
        }
        return null;
    }

    private void commit(RunEngine.ActiveFloor floor, ServerPlayer player) {
        holds.clear();
        // BEFORE the run ends, and this is the whole reason it is here as well as in beginAdvance.
        // The tramo is banked when the party LEAVES the floor, and there are two ways off a
        // boundary floor: down the trampilla and up the lift. Banking only on the descent meant
        // that clearing a tramo and then riding out — the exact thing the lift is for — anchored
        // nothing, and the party had to descend to be credited for a floor they had finished.
        RunEngine.bankTramo(floor);
        RunEngine.playAt(floor, player.blockPosition(), DungeonSound.SEAL_RESTORED, 0.8f);
        RunEngine.message(floor, "§bEl ascensor sube. §7La expedición vuelve a la superficie.");
        DungeonRunManager.extract(floor.level().getServer(), floor.run());
    }

    void forget() {
        holds.clear();
    }
}
