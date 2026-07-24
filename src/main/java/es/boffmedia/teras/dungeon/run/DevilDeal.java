package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The room behind the barred door. It sits on the minimap from the moment the floor is drawn and
 * cannot be entered until the boss falls — at which point the bars come down and there is one
 * pedestal inside holding one item.
 *
 * <p>Two prices, which is the whole design: coins, or two of your maximum hearts for the rest of
 * the run. Isaac charges in hearts because hearts are scarce there; here they are scarce because
 * of the health lockdown, so the choice is real — pay the party's money, or make yourself
 * permanently easier to kill on the floors still to come.</p>
 */
public final class DevilDeal {
    private DevilDeal() {}

    private static final int RANGE = 2;

    static boolean tryClaim(RunEngine.ActiveFloor floor, ServerPlayer player, Room room,
                            BlockPos clicked, boolean payWithHearts) {
        BlockPos pedestal = RunEngine.markerPos(floor, room, "deal");
        if (!RunEngine.isAtFixture(pedestal, clicked, RANGE)) {
            return false;
        }
        offer(floor, player, room, payWithHearts);
        return true;
    }

    /** What a loan of this floor's cash price will cost when it comes due. */
    static int loanFace(int stage) {
        int coins = CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), stage);
        return coins + coins * DungeonsConfig.debtInterestPct() / 100;
    }

    /** What standing debt costs to clear early — less than face, which is the reason to come back. */
    static int settlePrice(int deuda) {
        return Math.max(1, deuda - deuda * DungeonsConfig.debtSettleDiscountPct() / 100);
    }

    /**
     * Taking the goods on credit. The third price, and the only one that follows the party off the
     * floor: the purse is untouched, the debt is the run's, and two floors of not paying brings a
     * Cobrador for it (PISOS §63b, §6.4).
     *
     * <p>Run-level rather than per-player because the cash price already comes out of the shared
     * purse — a deferred coin price is the same obligation, deferred — and because it lets the
     * collector hunt the party instead of one member.</p>
     */
    static void borrow(RunEngine.ActiveFloor floor, ServerPlayer player, Room room) {
        BlockPos pedestal = RunEngine.markerPos(floor, room, "deal");
        if (floor.devilClaimed.contains(room)) {
            deny(floor, player, pedestal, "§5El trato ya está cerrado. No hay un segundo.");
            return;
        }
        // One loan at a time. Compounding debt is a different game, and a party that can borrow
        // twice can borrow forever — the pressure comes from the collector, not from the sum.
        if (floor.run().deuda() > 0) {
            deny(floor, player, pedestal, "§5Ya me debes §f" + floor.run().deuda()
                    + "§5 monedas. Una deuda cada vez.");
            return;
        }
        int face = loanFace(floor.run().stage());
        floor.run().borrow(face);
        claim(floor, player, room, pedestal, "§5Debes " + face + " monedas");
    }

    /**
     * Paying the debt off at his pedestal, at a discount for doing it before he has to ask. This is
     * <b>not</b> a deal: it closes nothing, commits nothing, and leaves the fork exactly where it
     * was — settling an account is not the same as striking a bargain, and treating it as one would
     * let a party buy their way out of the Orden's eligibility with money they already owed.
     */
    static void settle(RunEngine.ActiveFloor floor, ServerPlayer player, Room room) {
        BlockPos pedestal = RunEngine.markerPos(floor, room, "deal");
        int deuda = floor.run().deuda();
        if (deuda <= 0) {
            deny(floor, player, pedestal, "§5No me debes nada. Todavía.");
            return;
        }
        int price = settlePrice(deuda);
        if (!floor.run().wallet().trySpend(price)) {
            deny(floor, player, pedestal, "§cSaldar cuesta §f" + price
                    + "§c monedas y la bolsa tiene §f" + floor.run().wallet().coins() + "§c.");
            return;
        }
        floor.run().settleDebt(deuda);
        RunEngine.broadcastWallet(floor);
        RunEngine.playAt(floor, pedestal, DungeonSound.PURCHASE, 1.0f);
        DungeonTitles.send(player, "§5Cuenta saldada", "§7−" + price + " monedas");
        RunEngine.message(floor, "§5La deuda queda saldada. §7Nadie vendrá a cobrarla.");
    }

    /**
     * The trade itself, with no question of <i>where</i> the player was standing — the pedestal
     * asks that before calling, and El Acreedor asks nothing at all because the player clicked him.
     */
    static void offer(RunEngine.ActiveFloor floor, ServerPlayer player, Room room,
                      boolean payWithHearts) {
        BlockPos pedestal = RunEngine.markerPos(floor, room, "deal");
        if (floor.devilClaimed.contains(room)) {
            deny(floor, player, pedestal, "§5El trato ya está cerrado. No hay un segundo.");
            return;
        }
        int hearts = DungeonsConfig.devilHeartPrice();
        int coins = CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), floor.run().stage());

        if (payWithHearts) {
            // Refusing at one heart left is not mercy, it is arithmetic: taking the modifier below
            // the player's current health would kill them at the pedestal.
            if (player.getMaxHealth() - hearts * 2 < 2.0f) {
                deny(floor, player, pedestal, "§4No te queda suficiente vida que vender.");
                return;
            }
            PlayerRunState state = floor.run().stateOf(player.getUUID());
            state.addHpDebt(hearts * 2);
            // Trading flesh is the one purity signal the party gives away on purpose: it forfeits
            // la Orden's bonus this floor and marks the run as one that will sell (PISOS §63c).
            floor.soldHearts = true;
            Afflictions.apply(floor.run(), player);
            claim(floor, player, room, pedestal,
                    "§4−" + hearts + " corazones hasta el final de la partida");
            return;
        }
        if (!floor.run().wallet().trySpend(coins)) {
            // Says what is missing, never how to click: the same refusal is read at a pedestal, in
            // El Acreedor's own dialogue and from a chat line, and only one of those is a sneak-click.
            deny(floor, player, pedestal, "§cEl trato cuesta §f" + coins
                    + "§c monedas y la bolsa tiene §f" + floor.run().wallet().coins()
                    + "§c. También se paga con §4" + hearts + " corazones§c.");
            return;
        }
        RunEngine.broadcastWallet(floor);
        claim(floor, player, room, pedestal, "§7−" + coins + " monedas");
    }

    /**
     * A refusal, said twice on purpose. A CustomNPCs dialogue closes the instant an option runs its
     * command, so the action bar is all the player sees of the answer and it is gone in seconds —
     * the chat line is what is still there when they wonder why nothing happened.
     */
    private static void deny(RunEngine.ActiveFloor floor, ServerPlayer player, BlockPos pedestal,
                             String reason) {
        player.displayClientMessage(Component.literal(reason), true);
        player.sendSystemMessage(Component.literal(reason));
        RunEngine.playAt(floor, pedestal, DungeonSound.PURCHASE_DENIED, 1.0f);
    }

    private static void claim(RunEngine.ActiveFloor floor, ServerPlayer player, Room room,
                              BlockPos pedestal, String priceLabel) {
        floor.devilClaimed.add(room);
        // The ledger remembers: one deal makes him a regular visitor (+45 % from here on), and it
        // means the party did NOT refuse him this floor, so la Orden stays unearned.
        floor.run().recordAcreedorDeal();
        // And her door shuts: both stood open, only one is walked through.
        RunEngine.closeTheFork(floor, es.boffmedia.teras.dungeon.model.DoorKind.DEVIL);
        RunEngine.rollLootAt(floor, pedestal, DungeonsConfig.devilLootTable());
        RunEngine.playAt(floor, pedestal, DungeonSound.DEVIL_DEAL, 1.0f);
        DungeonTitles.send(player, "§5Trato cerrado", priceLabel);
        RunEngine.message(floor, "§5" + player.getName().getString() + " ha cerrado un trato.");
    }
}
