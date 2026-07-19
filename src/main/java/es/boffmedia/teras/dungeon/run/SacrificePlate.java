package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.model.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Isaac's sacrifice room: stand on the spikes, bleed, and each time you do the odds of the payout
 * climb. Stepping off ends it; stepping back on continues where the room left off.
 *
 * <p>The health it takes is a real cost here in a way it would not have been a stage ago — the
 * lockdown ({@link DungeonHealth}) means nothing but a bought potion gives it back, so the room is
 * a genuine wager of a scarce resource against a reward that gets likelier the deeper you go. The
 * room pays out once and then goes quiet: it is a gamble, not a faucet.</p>
 */
public final class SacrificePlate {
    private SacrificePlate() {}

    /** Long enough that standing on the plate is a decision per step, not a stream of damage. */
    private static final int COOLDOWN_TICKS = 25;

    static void step(RunEngine.ActiveFloor floor, ServerPlayer player, Room room) {
        if (!RunEngine.fixtureReady(floor, player, "sacrifice", COOLDOWN_TICKS)) {
            return;
        }
        BlockPos plate = RunEngine.markerPos(floor, room, "sacrifice");
        int steps = floor.sacrificeSteps.merge(room, 1, Integer::sum);

        player.hurt(player.damageSources().magic(), DungeonsConfig.sacrificeDamage());
        RunEngine.playAt(floor, plate, DungeonSound.SACRIFICE, 0.8f);

        int chance = DungeonsConfig.sacrificeBaseChancePct()
                + (steps - 1) * DungeonsConfig.sacrificeStepChancePct();
        if (floor.level().random.nextInt(100) >= chance) {
            player.displayClientMessage(Component.literal(
                    "§4Las púas beben… §7(" + Math.min(100, chance
                            + DungeonsConfig.sacrificeStepChancePct()) + "% al siguiente)"), true);
            return;
        }

        floor.sacrificeSpent.add(room);
        int coins = CoinDrops.scaleToStage(floor.level().random.nextInt(
                Math.max(1, DungeonsConfig.sacrificeCoinsMax() - DungeonsConfig.sacrificeCoinsMin() + 1))
                + DungeonsConfig.sacrificeCoinsMin(), floor.run().stage());
        CoinDrops.spawnCoins(floor.level(), plate, coins);
        if (floor.level().random.nextInt(100) < 25) {
            RunEngine.rollLootAt(floor, plate, DungeonsConfig.treasureLootTable());
        }
        if (floor.level().random.nextInt(100) < 15) {
            CoinDrops.spawnCharges(floor.level(),
                    net.minecraft.world.phys.Vec3.atCenterOf(plate), 1);
        }
        RunEngine.playAt(floor, plate, DungeonSound.SACRIFICE_REWARD, 1.0f);
        DungeonTitles.send(player, "§4Sacrificio aceptado", "§7+" + coins + " monedas");
        RunEngine.message(floor, "§4El altar acepta la ofrenda de "
                + player.getName().getString() + ".");
    }
}
