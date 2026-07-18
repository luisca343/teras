package es.boffmedia.teras.karts.reward;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.economy.EconomyStore;
import es.boffmedia.teras.karts.KartsConfig;
import es.boffmedia.teras.karts.engine.RaceResult;
import es.boffmedia.teras.karts.engine.RaceResults;
import es.boffmedia.teras.karts.store.LeaderboardStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pays out a finished race, files its records, and reports it.
 *
 * <p>Registered as a {@link RaceResults} listener during common setup, so a race does not have to
 * know that money or leaderboards exist.</p>
 */
public final class RaceRewards {
    private RaceRewards() {}

    /** Subscribes to finished races. Called once, from common setup. */
    public static void register() {
        RaceResults.addListener(RaceRewards::onRaceFinished);
    }

    private static void onRaceFinished(RaceResult result) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        recordLeaderboard(server, result);
        payOut(server, result);
        report(result);
    }

    /**
     * Credits each racer's winnings.
     *
     * <p>Goes through {@link EconomyStore#deposit}, the write-through funnel that mirrors the change
     * to starbank — race winnings are money the game creates, so this is exactly the path they
     * belong on, and none of the shop's skip-sync handling applies.</p>
     */
    private static void payOut(MinecraftServer server, RaceResult result) {
        Map<UUID, BigDecimal> earnings = PayoutCalculator.calculate(result, KartsConfig::payoutFor);
        earnings.forEach((playerId, amount) -> {
            if (amount.signum() <= 0) {
                return;
            }
            try {
                EconomyStore.deposit(playerId, amount);
                tell(server, playerId, "Has ganado " + amount.stripTrailingZeros().toPlainString()
                        + " por la carrera.", ChatFormatting.GOLD);
            } catch (Exception e) {
                Teras.LOGGER.error("Karts: failed to pay {} to {}", amount, playerId, e);
            }
        });
    }

    /** Files best times and best laps, and tells anyone who improved on their own. */
    private static void recordLeaderboard(MinecraftServer server, RaceResult result) {
        int topN = KartsConfig.leaderboardTopN();
        long now = System.currentTimeMillis();

        for (RaceResult.Placement placement : result.placements()) {
            if (placement.dnf()) {
                continue;
            }
            // A best lap stands on its own: it was driven, whatever happened to the rest of the race.
            if (placement.bestLapMs() > 0) {
                LeaderboardStore.submitLap(result.trackName(), new LeaderboardStore.Record(
                        placement.playerId(), placement.playerName(), placement.bestLapMs(),
                        1, "", now), topN);
            }

            // A total time is only comparable against the same distance. An elimination survivor is
            // credited with winning without covering it, and filing their short time as an N-lap
            // record would set a circuit record no full-distance race could ever beat.
            if (placement.lapsCompleted() < result.laps()) {
                continue;
            }
            Optional<LeaderboardStore.Record> previousRecord =
                    LeaderboardStore.trackRecord(result.trackName());

            LeaderboardStore.Record record = new LeaderboardStore.Record(
                    placement.playerId(), placement.playerName(), placement.timeMs(),
                    result.laps(), "", now);

            if (LeaderboardStore.submitTime(result.trackName(), record, topN)) {
                boolean beatTheTrack = previousRecord.isEmpty()
                        || placement.timeMs() < previousRecord.get().timeMs();
                tell(server, placement.playerId(),
                        beatTheTrack ? "¡Récord del circuito!" : "¡Nuevo récord personal!",
                        ChatFormatting.AQUA);
            }
        }
        LeaderboardStore.saveIfDirty();
    }

    /** Sends the result to the SmartRotom backend, if that is switched on. */
    private static void report(RaceResult result) {
        if (!KartsConfig.isBackendPostEnabled()) {
            return;
        }
        try {
            es.boffmedia.teras.util.net.SmartRotomService.saveRace(result);
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to report the race to the backend: {}", e.toString());
        }
    }

    private static void tell(MinecraftServer server, UUID playerId, String message,
                             ChatFormatting style) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message).withStyle(style));
        }
    }
}
