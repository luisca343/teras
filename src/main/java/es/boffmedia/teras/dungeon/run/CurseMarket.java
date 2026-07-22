package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The curse room's trading floor: drawbacks for coins, and coins to be rid of one.
 *
 * <h2>What it replaces</h2>
 *
 * <p>The room used to charge you for arriving — coins off the party purse, or six magic damage if
 * the purse was short — and drop a reward on a pedestal. There was nothing to decide. You could not
 * decline, could not see the price before it was taken, and it played the same whether you were
 * flush or broke.</p>
 *
 * <p>Now the price is at the door (a heart, paid under visible spikes) and everything inside is
 * optional. Each offer pedestal shows <b>both halves of the trade</b> — what you get, and what you
 * will live with — so the argument happens before anyone touches anything.</p>
 *
 * <h2>Why accepting for the party takes three seconds</h2>
 *
 * <p>Four of the six afflictions land on everyone. A single click would let one member commit the
 * other three instantly and irreversibly, which is not drama, it is griefing. So a party offer
 * charges instead of firing: the first click opens a three-second hold that the server itself
 * ticks, alive for as long as the holder stays in reach with the stand in their sights. The holder
 * gets a progress bar, everyone else gets a warning naming them and what they are about to accept,
 * and three seconds is long enough to shout. Personal afflictions commit on one click — they are
 * only ever your own problem.</p>
 *
 * <p>The hold is deliberately not measured by the client's repeated use packets. Their four-tick
 * cadence is nominal at best — aim drifting a block, a usable item in hand, another client mod —
 * and any break in the chain reset the bar, which in practice meant spam-clicking it full.</p>
 */
public final class CurseMarket {

    /** Ticks of sustained attention before a party-wide affliction is accepted. */
    private static final int HOLD_TICKS = 60;
    /** How far the holder may stand from the stand before the hold drops. */
    private static final double HOLD_REACH = 5.0;
    /**
     * How far the view ray may pass from the stand's middle. Generous enough to cover aiming at
     * the floating icon or the label rather than the pedestal block itself.
     */
    private static final double HOLD_AIM_SLACK = 1.5;
    private static final int RANGE = 1;
    private static final int DISPLAY_SWEEP_RADIUS = 3;

    private static final class Stand {
        final BlockPos pos;
        /** Null on the purge stand, which sells the opposite of an offer. */
        final AfflictionMarket.Offer offer;
        UUID itemDisplay;
        UUID textDisplay;
        UUID detailDisplay;
        boolean spent;

        Stand(BlockPos pos, AfflictionMarket.Offer offer) {
            this.pos = pos;
            this.offer = offer;
        }
    }

    /** One player's progress through a hold. */
    private record Hold(BlockPos stand, long startedTick) {}

    private final List<Stand> offers = new ArrayList<>();
    private Stand purge;
    private final Map<UUID, Hold> holds = new HashMap<>();

    /**
     * Stands the market up when the curse room is discovered.
     *
     * <p>A room with no {@code oferta} markers gets no market rather than a fallback: an offer has
     * to be somewhere a player can read it, and the middle of the floor is not that place. The
     * marker audit requires them, so this is a template that was hand-edited past the checks.</p>
     */
    void open(RunEngine.ActiveFloor floor, Room room) {
        if (!offers.isEmpty() || purge != null) {
            return;
        }
        List<BlockPos> slots = markers(floor, room, "oferta");
        BlockPos purgePos = markers(floor, room, "purga").stream().findFirst().orElse(null);
        if (slots.isEmpty() && purgePos == null) {
            return;
        }
        List<AfflictionSet> personal = new ArrayList<>();
        for (UUID member : floor.run().party().keySet()) {
            personal.add(floor.run().stateOf(member).afflictions());
        }
        long seed = DungeonSeeds.derive(floor.built().layout().baseSeed(),
                DungeonSeeds.fnv1a64("curse_market:" + room.anchor().x() + ":" + room.anchor().y()));
        List<AfflictionMarket.Offer> drawn = AfflictionMarket.draw(
                floor.run().afflictions(), personal,
                Math.min(slots.size(), DungeonsConfig.marketSlots()),
                DungeonsConfig.marketReward(), floor.run().stage(), seed);
        for (int i = 0; i < drawn.size(); i++) {
            Stand stand = new Stand(slots.get(i), drawn.get(i));
            offers.add(stand);
            show(floor.level(), stand);
        }
        if (purgePos != null) {
            purge = new Stand(purgePos, null);
            show(floor.level(), purge);
        }
    }

    /**
     * Handles a click at one of these stands.
     *
     * @return whether the click was this market's business, so the caller can cancel the event
     */
    boolean tryUse(RunEngine.ActiveFloor floor, ServerPlayer player, BlockPos clicked) {
        if (purge != null && RunEngine.isAtFixture(purge.pos, clicked, RANGE)) {
            tryPurge(floor, player);
            return true;
        }
        for (Stand stand : offers) {
            if (!RunEngine.isAtFixture(stand.pos, clicked, RANGE)) {
                continue;
            }
            tryAccept(floor, player, stand);
            return true;
        }
        return false;
    }

    private void tryAccept(RunEngine.ActiveFloor floor, ServerPlayer player, Stand stand) {
        if (stand.spent) {
            player.displayClientMessage(Component.literal("§8Ya está cerrado ese trato."), true);
            return;
        }
        Afliccion afliccion = stand.offer.afliccion();
        if (afliccion.scope() == Afliccion.Scope.PERSONAL) {
            commit(floor, player, stand);
            return;
        }
        Hold held = holds.get(player.getUUID());
        if (held != null && held.stand().equals(stand.pos)) {
            // Already charging; tick() is driving it, and the client repeats clicks while held.
            return;
        }
        holds.put(player.getUUID(),
                new Hold(stand.pos, floor.level().getServer().getTickCount()));
        RunEngine.message(floor, "§5" + player.getName().getString() + " va a aceptar §d"
                + afliccion.nombre() + "§5 para todo el grupo…");
    }

    /** Advances every open hold; runs once per server tick while the floor is live. */
    void tick(RunEngine.ActiveFloor floor) {
        if (holds.isEmpty()) {
            return;
        }
        long tick = floor.level().getServer().getTickCount();
        Iterator<Map.Entry<UUID, Hold>> it = holds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Hold> entry = it.next();
            Hold held = entry.getValue();
            Stand stand = offerAt(held.stand());
            ServerPlayer player = floor.level().getServer().getPlayerList()
                    .getPlayer(entry.getKey());
            if (stand == null || stand.spent || player == null || !player.isAlive()
                    || player.serverLevel() != floor.level()) {
                it.remove();
                continue;
            }
            if (!engaged(player, stand.pos)) {
                it.remove();
                player.displayClientMessage(Component.literal("§8El trato queda en el aire."), true);
                continue;
            }
            long elapsed = tick - held.startedTick();
            if (elapsed >= HOLD_TICKS) {
                it.remove();
                commit(floor, player, stand);
                continue;
            }
            int filled = (int) (10 * elapsed / HOLD_TICKS);
            player.displayClientMessage(Component.literal("§5Aceptando §d"
                    + stand.offer.afliccion().nombre() + " §8[§d" + "▉".repeat(filled) + "§8"
                    + "▁".repeat(10 - filled) + "§8]"), true);
        }
    }

    /** Still at the stand: close enough, with the view ray passing by its column. */
    private static boolean engaged(ServerPlayer player, BlockPos standPos) {
        Vec3 centre = Vec3.atBottomCenterOf(standPos).add(0, 1.0, 0);
        Vec3 eye = player.getEyePosition();
        if (eye.distanceTo(centre) > HOLD_REACH) {
            return false;
        }
        Vec3 look = player.getViewVector(1.0F);
        double along = Math.max(0.0, centre.subtract(eye).dot(look));
        return eye.add(look.scale(along)).distanceTo(centre) <= HOLD_AIM_SLACK;
    }

    private Stand offerAt(BlockPos pos) {
        for (Stand stand : offers) {
            if (stand.pos.equals(pos)) {
                return stand;
            }
        }
        return null;
    }

    private void commit(RunEngine.ActiveFloor floor, ServerPlayer player, Stand stand) {
        Afliccion afliccion = stand.offer.afliccion();
        boolean taken = afliccion.scope() == Afliccion.Scope.PARTY
                ? floor.run().afflictions().add(afliccion)
                : floor.run().stateOf(player.getUUID()).afflictions().add(afliccion);
        if (!taken) {
            // Two players holding the same party offer: the second one's hold completes against a
            // set that already has it. Nobody should be paid twice for one drawback.
            stand.spent = true;
            refresh(floor.level(), stand);
            return;
        }
        stand.spent = true;
        floor.run().wallet().add(stand.offer.reward());
        RunEngine.broadcastWallet(floor);
        RunEngine.playAt(floor, stand.pos, DungeonSound.DEVIL_DEAL, 1.0f);
        RunEngine.message(floor, "§5El grupo acepta §d" + afliccion.nombre() + "§5 — §e+"
                + stand.offer.reward() + "⛁§5. " + afliccion.descripcion());
        DungeonTitles.send(player, "§5" + afliccion.nombre(), "§7" + afliccion.descripcion());
        refresh(floor.level(), stand);
        RunEngine.syncAfflictions(floor);
    }

    private void tryPurge(RunEngine.ActiveFloor floor, ServerPlayer player) {
        AfflictionSet party = floor.run().afflictions();
        AfflictionSet mine = floor.run().stateOf(player.getUUID()).afflictions();
        int carried = party.size() + mine.size();
        if (carried == 0) {
            player.displayClientMessage(Component.literal(
                    "§8No cargas con ninguna aflicción."), true);
            return;
        }
        int price = AfflictionMarket.purgePrice(DungeonsConfig.purgePrice(), carried);
        if (!floor.run().wallet().trySpend(price)) {
            player.displayClientMessage(Component.literal("§cCuesta " + price + " monedas — tenéis "
                    + floor.run().wallet().coins() + "."), true);
            RunEngine.playAt(floor, purge.pos, DungeonSound.PURCHASE_DENIED, 1.0f);
            return;
        }
        // Personal first: it is the one the clicking player is actually living with, and spending
        // the party's coins to fix your own problem should at least fix yours.
        Afliccion shed = mine.carried().isEmpty()
                ? party.carried().get(party.carried().size() - 1)
                : mine.carried().get(mine.carried().size() - 1);
        if (!mine.remove(shed)) {
            party.remove(shed);
        }
        RunEngine.broadcastWallet(floor);
        RunEngine.playAt(floor, purge.pos, DungeonSound.PURCHASE, 1.0f);
        RunEngine.message(floor, "§d" + shed.nombre() + "§5 se desvanece §7(−" + price + "⛁)");
        refresh(floor.level(), purge);
        RunEngine.syncAfflictions(floor);
    }

    private void show(ServerLevel level, Stand stand) {
        ItemStack icon;
        Component label;
        if (stand.offer == null) {
            icon = new ItemStack(Items.MILK_BUCKET);
            label = Component.literal("§dPurgar §7· clic para quitarte una");
        } else if (stand.spent) {
            icon = new ItemStack(Items.BARRIER);
            label = Component.literal("§8Cerrado");
        } else {
            icon = new ItemStack(ItemInit.MONEDA_MAZMORRA.get(), 1);
            label = Component.literal("§d" + stand.offer.afliccion().nombre() + " §8· §e+"
                    + stand.offer.reward() + "⛁");
        }
        Entity item = DungeonDisplays.spawnItem(level, stand.pos, 1.2, icon);
        Entity text = DungeonDisplays.spawnText(level, stand.pos, 1.9, label);
        stand.itemDisplay = item == null ? null : item.getUUID();
        stand.textDisplay = text == null ? null : text.getUUID();
        if (stand.offer != null && !stand.spent) {
            // The second line is what makes the pedestal a decision rather than a lever: the name
            // of an affliction says nothing about what living with it is like.
            Entity detail = DungeonDisplays.spawnText(level, stand.pos, 2.2,
                    Component.literal("§7" + stand.offer.afliccion().descripcion()));
            stand.detailDisplay = detail == null ? null : detail.getUUID();
        }
    }

    private void refresh(ServerLevel level, Stand stand) {
        hide(level, stand);
        show(level, stand);
    }

    private void hide(ServerLevel level, Stand stand) {
        DungeonDisplays.discard(level, stand.itemDisplay);
        DungeonDisplays.discard(level, stand.textDisplay);
        DungeonDisplays.discard(level, stand.detailDisplay);
        // And by tag: an id lookup answers null for an entity in an unloaded chunk, which is how
        // shop pedestals used to survive into the next floor.
        DungeonDisplays.sweep(level, stand.pos, DISPLAY_SWEEP_RADIUS);
        stand.itemDisplay = null;
        stand.textDisplay = null;
        stand.detailDisplay = null;
    }

    /** Everything this market put up, for a stage advance and for teardown. */
    void despawnDisplays(RunEngine.ActiveFloor floor) {
        for (Stand stand : offers) {
            hide(floor.level(), stand);
        }
        if (purge != null) {
            hide(floor.level(), purge);
        }
        offers.clear();
        purge = null;
        holds.clear();
    }

    private static List<BlockPos> markers(RunEngine.ActiveFloor floor, Room room, String kind) {
        List<BlockPos> positions = new ArrayList<>();
        for (TemplateMarkers.Marker marker : floor.built().markers().getOrDefault(room, List.of())) {
            if (marker.kind().equals(kind)) {
                positions.add(marker.pos());
            }
        }
        return positions;
    }

    /** Diagnostic, for the debug command. */
    public int size() {
        return offers.size() + (purge == null ? 0 : 1);
    }
}
