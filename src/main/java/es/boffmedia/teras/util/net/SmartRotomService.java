package es.boffmedia.teras.util.net;

import com.google.gson.Gson;
import es.boffmedia.teras.battle.model.TeamMember;
import es.boffmedia.teras.util.TerasConfig;

import java.util.List;
import java.util.UUID;

/**
 * Client for the SmartRotom battle endpoints. Every payload carries a top-level {@code server} =
 * {@link TerasConfig#getId()}, which the backend's {@code MinecraftMiddleware} requires (it 403s
 * otherwise, before routing). Calls are fire-and-forget over {@link HttpText#postJson}.
 */
public final class SmartRotomService {
    private SmartRotomService() {}

    private static final Gson GSON = new Gson();

    public static void defeatTrainer(UUID playerId, int money) {
        if (playerId == null || money <= 0) {
            return;
        }
        HttpText.postJson(TerasConfig.getApiUrl() + "/smartrotom/starbank/trainerdefeat",
                GSON.toJson(new TrainerDefeat(TerasConfig.getId(), playerId.toString(), money)));
    }

    public static void saveBattle(BattleReport report) {
        if (report == null) {
            return;
        }
        BattleReportBody body = new BattleReportBody(TerasConfig.getId(), report.uuid(), report.logro(),
                report.victoria(), report.name1(), report.name2(),
                report.team1(), report.team2(), report.replay());
        HttpText.postJson(TerasConfig.getApiUrl() + "/smartrotom/achievement/battle-achievement",
                GSON.toJson(body));
    }

    private record TrainerDefeat(String server, String uuid, int money) {}

    private record BattleReportBody(String server, String uuid, String logro, boolean victoria,
                                    String name1, String name2,
                                    List<TeamMember> team1, List<TeamMember> team2, String replay) {}

    /** Caller-facing battle report; {@code server} is added by {@link #saveBattle}. */
    public record BattleReport(String uuid, String logro, boolean victoria,
                               String name1, String name2,
                               List<TeamMember> team1, List<TeamMember> team2, String replay) {}
}
