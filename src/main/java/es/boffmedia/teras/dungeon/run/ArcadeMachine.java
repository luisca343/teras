package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * The arcade room: feed the machine coins, pull, mostly lose. Isaac's beggar, priced against a
 * purse the whole party shares — which is the point of putting it here rather than giving everyone
 * their own wallet. Somebody is always feeding the machine with the team's potion money.
 *
 * <p>The roll uses the level's own randomness rather than the floor seed. A seeded gamble is not a
 * gamble: two runs of the same seed would pay out identically and the room would be a lookup
 * table.</p>
 */
public final class ArcadeMachine {
    private ArcadeMachine() {}

    private static final int COOLDOWN_TICKS = 10;

    /** How close a click has to be to the machine's marker to count as pulling it. */
    private static final int RANGE = 2;

    static boolean tryPlay(RunEngine.ActiveFloor floor, ServerPlayer player, Room room,
                           BlockPos clicked) {
        BlockPos machine = RunEngine.markerPos(floor, room, "arcade");
        if (!RunEngine.isAtFixture(machine, clicked, RANGE)) {
            return false;
        }
        if (floor.arcadeBroken.contains(room)) {
            player.displayClientMessage(Component.literal("§8La máquina está rota."), true);
            return true;
        }
        if (!RunEngine.fixtureReady(floor, player, "arcade", COOLDOWN_TICKS)) {
            return true;
        }
        int price = CoinDrops.scaleToStage(DungeonsConfig.arcadePrice(), floor.run().stage());
        if (!floor.run().wallet().trySpend(price)) {
            player.displayClientMessage(Component.literal(
                    "§cLa máquina traga " + price + " monedas — no las tenéis."), true);
            RunEngine.playAt(floor, machine, DungeonSound.PURCHASE_DENIED, 1.0f);
            return true;
        }
        RunEngine.playAt(floor, machine, DungeonSound.ARCADE_PLAY, 1.0f);
        RunEngine.broadcastWallet(floor);
        payOut(floor, player, room, machine);
        return true;
    }

    /** Weighted outcomes, worst first. The jackpot is rare enough to be worth telling the party. */
    private static void payOut(RunEngine.ActiveFloor floor, ServerPlayer player, Room room,
                               BlockPos machine) {
        RandomSource random = floor.level().random;
        int roll = random.nextInt(100);
        int stage = floor.run().stage();
        if (roll < 40) {
            player.displayClientMessage(Component.literal("§8La máquina se traga las monedas."), true);
        } else if (roll < 70) {
            int coins = CoinDrops.scaleToStage(2 + random.nextInt(7), stage);
            CoinDrops.spawnCoins(floor.level(), machine, coins);
            player.displayClientMessage(Component.literal("§7Migajas: +" + coins), true);
        } else if (roll < 84) {
            int coins = CoinDrops.scaleToStage(15 + random.nextInt(16), stage);
            CoinDrops.spawnCoins(floor.level(), machine, coins);
            RunEngine.playAt(floor, machine, DungeonSound.ARCADE_WIN, 1.0f);
            player.displayClientMessage(Component.literal("§ePremio: +" + coins), true);
        } else if (roll < 96) {
            RunEngine.rollLootAt(floor, machine, DungeonsConfig.treasureLootTable());
            RunEngine.playAt(floor, machine, DungeonSound.ARCADE_WIN, 1.2f);
            player.displayClientMessage(Component.literal("§eLa máquina escupe algo."), true);
        } else {
            int coins = CoinDrops.scaleToStage(50 + random.nextInt(50), stage);
            CoinDrops.spawnCoins(floor.level(), machine, coins);
            RunEngine.rollLootAt(floor, machine, DungeonsConfig.treasureLootTable());
            RunEngine.playAt(floor, machine, DungeonSound.ARCADE_WIN, 1.5f);
            DungeonTitles.send(player, "§6¡Bote!", "§7+" + coins + " monedas");
            RunEngine.message(floor, "§6" + player.getName().getString()
                    + " ha reventado la máquina: +" + coins + " monedas.");
        }
        if (random.nextInt(100) < DungeonsConfig.arcadeBreakChancePct()) {
            floor.arcadeBroken.add(room);
            RunEngine.playAt(floor, machine, DungeonSound.ARCADE_BREAK, 1.0f);
            RunEngine.message(floor, "§8La máquina de la sala arcade se ha roto.");
        }
    }
}
