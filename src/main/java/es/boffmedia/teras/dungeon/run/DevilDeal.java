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

    /**
     * The trade itself, with no question of <i>where</i> the player was standing — the pedestal
     * asks that before calling, and El Acreedor asks nothing at all because the player clicked him.
     */
    static void offer(RunEngine.ActiveFloor floor, ServerPlayer player, Room room,
                      boolean payWithHearts) {
        BlockPos pedestal = RunEngine.markerPos(floor, room, "deal");
        if (floor.devilClaimed.contains(room)) {
            return;
        }
        int hearts = DungeonsConfig.devilHeartPrice();
        int coins = CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), floor.run().stage());

        if (payWithHearts) {
            // Refusing at one heart left is not mercy, it is arithmetic: taking the modifier below
            // the player's current health would kill them at the pedestal.
            if (player.getMaxHealth() - hearts * 2 < 2.0f) {
                player.displayClientMessage(
                        Component.literal("§4No te queda suficiente vida que vender."), true);
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
            player.displayClientMessage(Component.literal("§cEl trato cuesta §f" + coins
                    + "§c monedas y la bolsa tiene §f" + floor.run().wallet().coins()
                    + "§c. También se paga con §4" + hearts + " corazones§c."), true);
            RunEngine.playAt(floor, pedestal, DungeonSound.PURCHASE_DENIED, 1.0f);
            return;
        }
        RunEngine.broadcastWallet(floor);
        claim(floor, player, room, pedestal, "§7−" + coins + " monedas");
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
