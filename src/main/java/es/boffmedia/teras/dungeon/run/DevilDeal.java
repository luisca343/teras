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
        if (floor.devilClaimed.contains(room)) {
            return true;
        }
        int hearts = DungeonsConfig.devilHeartPrice();
        int coins = CoinDrops.scaleToStage(DungeonsConfig.devilCoinPrice(), floor.run().stage());

        if (payWithHearts) {
            // Refusing at one heart left is not mercy, it is arithmetic: taking the modifier below
            // the player's current health would kill them at the pedestal.
            if (player.getMaxHealth() - hearts * 2 < 2.0f) {
                player.displayClientMessage(
                        Component.literal("§4No te queda suficiente vida que vender."), true);
                return true;
            }
            PlayerRunState state = floor.run().stateOf(player.getUUID());
            state.addHpDebt(hearts * 2);
            Afflictions.apply(floor.run(), player);
            claim(floor, player, room, pedestal,
                    "§4−" + hearts + " corazones hasta el final de la partida");
            return true;
        }
        if (!floor.run().wallet().trySpend(coins)) {
            player.displayClientMessage(Component.literal("§cEl trato cuesta " + coins
                    + " monedas, o agáchate y haz clic para pagar con " + hearts + " corazones."), true);
            RunEngine.playAt(floor, pedestal, DungeonSound.PURCHASE_DENIED, 1.0f);
            return true;
        }
        RunEngine.broadcastWallet(floor);
        claim(floor, player, room, pedestal, "§7−" + coins + " monedas");
        return true;
    }

    private static void claim(RunEngine.ActiveFloor floor, ServerPlayer player, Room room,
                              BlockPos pedestal, String priceLabel) {
        floor.devilClaimed.add(room);
        RunEngine.rollLootAt(floor, pedestal, DungeonsConfig.devilLootTable());
        RunEngine.playAt(floor, pedestal, DungeonSound.DEVIL_DEAL, 1.0f);
        DungeonTitles.send(player, "§5Trato cerrado", priceLabel);
        RunEngine.message(floor, "§5" + player.getName().getString() + " ha cerrado un trato.");
    }
}
