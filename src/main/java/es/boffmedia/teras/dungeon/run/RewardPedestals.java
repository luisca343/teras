package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rewards you take off a stand, rather than items dropped on the floor.
 *
 * <h2>What this replaces</h2>
 *
 * <p>{@code roomDiscovered} rolled a loot table once — the moment the <b>first</b> player crossed
 * the threshold — and dropped the results as loose item entities on the marker. Four consequences,
 * all of them silent:</p>
 *
 * <ul>
 *   <li>a player still fighting two rooms back arrived to a bare pedestal;</li>
 *   <li>one player could walk over the pile and take all of it;</li>
 *   <li>item entities despawn after five minutes, so "we'll come back for it" quietly failed;</li>
 *   <li>stage advance sweeps the floor, so coming back was never safe anyway.</li>
 * </ul>
 *
 * <p>A claimed pedestal fixes all four at once, and — the part that mattered for design — turns
 * "who gets this" into a per-room setting instead of two separate mechanisms.</p>
 *
 * <h2>When each policy rolls</h2>
 *
 * <p>{@link ClaimPolicy.Kind#ONE_OF_N} rolls <b>when the pedestal arms</b> and shows the result, so
 * the party can see what it is deciding about. {@link ClaimPolicy.Kind#PER_PLAYER} rolls <b>at
 * claim</b>, freshly per player: nobody can see another's luck, and there is nothing to race for.
 * That falls out of the two policies rather than being a third rule to remember.</p>
 */
public final class RewardPedestals {

    /** How close a click has to be to count, matching the shop's and the devil's pedestals. */
    private static final int RANGE = 2;

    /** One stand, its policy, and what it hands over. */
    private static final class Pedestal {
        final Room room;
        final BlockPos pos;
        final ClaimPolicy policy;
        final String lootTable;
        /** Pre-rolled for ONE_OF_N, empty for PER_PLAYER, which rolls per claim. */
        final List<ItemStack> reserved = new ArrayList<>();
        UUID itemDisplay;
        UUID textDisplay;

        Pedestal(Room room, BlockPos pos, ClaimPolicy policy, String lootTable) {
            this.room = room;
            this.pos = pos;
            this.policy = policy;
            this.lootTable = lootTable;
        }
    }

    private final Map<Room, Pedestal> byRoom = new LinkedHashMap<>();

    /**
     * Stands a reward up in {@code room}, on its {@code loot} marker.
     *
     * <p>A room with no marker keeps the old loose-item roll at a calculated position, with the
     * warning that already existed — a template that has not been given a pedestal should degrade,
     * not break.</p>
     */
    public void arm(RunEngine.ActiveFloor floor, Room room, ClaimPolicy.Kind kind,
                    String lootTable) {
        BlockPos marker = RunEngine.markerPos(floor, room, "loot");
        if (marker == null) {
            if (!byRoom.containsKey(room)) {
                RunEngine.rollLootAt(floor, RunEngine.fallbackLootPos(floor, room), lootTable);
            }
            return;
        }
        armAt(floor, room, kind, lootTable, marker);
    }

    /**
     * The same, at a position of the caller's choosing.
     *
     * <p>The boss room needs it: it carries {@code boss} and {@code trapdoor} markers and no
     * {@code loot} one, because its reward used to be thrown on the ground beside the hole.</p>
     */
    public void armAt(RunEngine.ActiveFloor floor, Room room, ClaimPolicy.Kind kind,
                      String lootTable, BlockPos marker) {
        if (byRoom.containsKey(room)) {
            return;
        }
        Pedestal pedestal = new Pedestal(room, marker, new ClaimPolicy(kind), lootTable);
        if (kind == ClaimPolicy.Kind.ONE_OF_N) {
            pedestal.reserved.addAll(RunEngine.rollLoot(floor, lootTable));
            if (pedestal.reserved.isEmpty()) {
                // A one-of-N stand is rolled up front and shows what it holds, so an empty roll
                // would put a pedestal in front of the party that gives nothing when clicked —
                // indistinguishable from a bug. Better no stand than a lying one. (A table that
                // rolls empty often is a content problem: `boss` is 55/97 weight `minecraft:empty`,
                // so more than half of all boss kills stand up nothing at all.)
                Teras.LOGGER.debug("Dungeons: {} rolled an empty reward from {}", room, lootTable);
                return;
            }
        }
        byRoom.put(room, pedestal);
        show(floor, pedestal);
    }

    /**
     * Takes a reward if the click was at a live pedestal.
     *
     * @return whether the click was this system's business, so the caller can cancel the event
     */
    public boolean tryClaim(RunEngine.ActiveFloor floor, ServerPlayer player, BlockPos clicked) {
        for (Pedestal pedestal : byRoom.values()) {
            if (!RunEngine.isAtFixture(pedestal.pos, clicked, RANGE)) {
                continue;
            }
            claim(floor, player, pedestal);
            return true;
        }
        return false;
    }

    private void claim(RunEngine.ActiveFloor floor, ServerPlayer player, Pedestal pedestal) {
        if (!floor.run().party().containsKey(player.getUUID())) {
            return;
        }
        if (!pedestal.policy.claim(player.getUUID())) {
            player.displayClientMessage(Component.literal(
                    pedestal.policy.kind() == ClaimPolicy.Kind.ONE_OF_N
                            ? "§8Alguien se lo ha llevado ya."
                            : "§8Ya has cogido el tuyo."), true);
            return;
        }
        List<ItemStack> reward = pedestal.policy.kind() == ClaimPolicy.Kind.ONE_OF_N
                ? pedestal.reserved
                : RunEngine.rollLoot(floor, pedestal.lootTable);
        for (ItemStack stack : reward) {
            RunEngine.giveOrDrop(player, stack.copy());
        }
        RunEngine.playAt(floor, pedestal.pos, DungeonSound.PURCHASE, 1.0f);
        if (pedestal.policy.kind() == ClaimPolicy.Kind.ONE_OF_N) {
            RunEngine.message(floor, "§6" + player.getName().getString() + " se ha llevado el premio.");
        }
        refresh(floor, pedestal);
    }

    /** Clears every stand's displays. Called with the floor, and on a stage advance. */
    public void despawn(RunEngine.ActiveFloor floor) {
        for (Pedestal pedestal : byRoom.values()) {
            hide(floor.level(), pedestal);
        }
        byRoom.clear();
    }

    private void show(RunEngine.ActiveFloor floor, Pedestal pedestal) {
        ItemStack icon = pedestal.reserved.isEmpty()
                ? new ItemStack(net.minecraft.world.item.Items.CHEST)
                : pedestal.reserved.get(0).copy();
        var item = DungeonDisplays.spawnItem(floor.level(), pedestal.pos, 1.2, icon);
        var text = DungeonDisplays.spawnText(floor.level(), pedestal.pos, 1.9, label(floor, pedestal));
        pedestal.itemDisplay = item == null ? null : item.getUUID();
        pedestal.textDisplay = text == null ? null : text.getUUID();
    }

    private void refresh(RunEngine.ActiveFloor floor, Pedestal pedestal) {
        hide(floor.level(), pedestal);
        if (pedestal.policy.spent(floor.run().party().size())) {
            // Spent stands go quiet rather than vanishing: an empty pedestal still says a reward
            // was here and somebody took it, where nothing at all reads as a room that never had one.
            var text = DungeonDisplays.spawnText(floor.level(), pedestal.pos, 1.9,
                    Component.literal("§8Vacío"));
            pedestal.textDisplay = text == null ? null : text.getUUID();
            return;
        }
        show(floor, pedestal);
    }

    private void hide(ServerLevel level, Pedestal pedestal) {
        DungeonDisplays.discard(level, pedestal.itemDisplay);
        DungeonDisplays.discard(level, pedestal.textDisplay);
        // And by tag, because an id lookup answers null for an entity in an unloaded chunk — the
        // same blindness that left shop pedestals standing in later floors.
        DungeonDisplays.sweep(level, pedestal.pos, 3);
        pedestal.itemDisplay = null;
        pedestal.textDisplay = null;
    }

    private Component label(RunEngine.ActiveFloor floor, Pedestal pedestal) {
        if (pedestal.policy.kind() == ClaimPolicy.Kind.ONE_OF_N) {
            return Component.literal("§6Uno solo §7· clic para reclamar");
        }
        int party = Math.max(1, floor.run().party().size());
        return Component.literal("§aPara cada uno §7· " + pedestal.policy.claims() + "/" + party);
    }

    /** Diagnostic, for the debug command. */
    public int size() {
        return byRoom.size();
    }

    static {
        Teras.LOGGER.debug("Dungeons: reward pedestals ready");
    }
}
