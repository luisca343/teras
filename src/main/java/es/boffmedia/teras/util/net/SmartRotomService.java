package es.boffmedia.teras.util.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import es.boffmedia.teras.battle.model.TeamMember;
import es.boffmedia.teras.util.TerasConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
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

    // NOTE: no NPC push. 1.16.5 POSTed the catalog to /smartrotom/misiones/npcs, but NPC data now
    // reaches the backend inside the quest catalog itself (dialogs[].npcLocations on GET /quests/all),
    // so a separate push would be a second, divergent source of the same data. Its body never matched
    // the backend's UpdateNPCsDto anyway. See docs/QUESTS.md.

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

    // ---- starbank (economy) ----------------------------------------------------------------
    //
    // ⚠️ UNVERIFIED WIRE CONTRACT. The shapes below are reconstructed from the 1.16.5 Wungill plugin
    // (BancoQuery / WungillEconomy) and have NOT been confirmed against the current backend. Two known
    // discrepancies, both resolved here in favour of the newer evidence:
    //
    //   1. Wungill sent the server id as `idMundo`; `defeatTrainer` above (written against the current
    //      backend) sends it as top-level `server`, which MinecraftMiddleware requires or it 403s.
    //      These calls follow `server`.
    //   2. Wungill posted a redundant `starbank/actualizar` alongside every operation, carrying the
    //      *pre*-mutation balance. That looks like a bug rather than contract, so it is not reproduced.
    //
    // Confirm against SmartRotom before enabling in production; see WUNGILL_MIGRATION.md.

    /** Starbank operations, mirroring the 1.16.5 {@code BancoQuery.operacion} values. */
    private enum Op { DEPOSITAR, RETIRAR, SET }

    /**
     * Reads the authoritative balance. <b>Blocks</b> — call from {@link es.boffmedia.teras.Teras#EXECUTOR}.
     * Returns {@code null} when the balance cannot be established (transport failure, unparseable
     * body), which callers must treat as "unknown", never as zero.
     *
     * <p>1.16.5 {@code getDineroFromBBDD} hit {@code GET banco/{uuid}/{server}} and did a bare
     * {@code Double.parseDouble} on the body, so an error page became a {@code NumberFormatException}.
     * Here a non-numeric body is a {@code null} instead.</p>
     */
    public static BigDecimal fetchBalance(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        String body = HttpText.getAuthed(
                TerasConfig.getApiUrl() + "/banco/" + playerId + "/" + TerasConfig.getId());
        if (body == null) {
            return null;
        }
        return parseBalance(body);
    }

    /**
     * The balance out of a starbank response body, or {@code null} if it isn't one. Accepts a bare
     * number (what 1.16.5 consumed) or a JSON object carrying {@code balance} / {@code dinero} /
     * {@code data}, since the current backend's shape is unconfirmed.
     */
    static BigDecimal parseBalance(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException ignored) {
            // Not a bare number — fall through to the JSON shapes.
        }
        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isNumber()) {
                return parsed.getAsBigDecimal();
            }
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject obj = parsed.getAsJsonObject();
            for (String key : new String[] {"balance", "dinero", "data"}) {
                JsonElement value = obj.get(key);
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    return value.getAsBigDecimal();
                }
            }
            return null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    public static void deposit(UUID playerId, BigDecimal amount) {
        postBanco(Op.DEPOSITAR, playerId, amount);
    }

    public static void withdraw(UUID playerId, BigDecimal amount) {
        postBanco(Op.RETIRAR, playerId, amount);
    }

    public static void setBalance(UUID playerId, BigDecimal amount) {
        postBanco(Op.SET, playerId, amount);
    }

    private static void postBanco(Op op, UUID playerId, BigDecimal amount) {
        if (playerId == null || amount == null) {
            return;
        }
        HttpText.postJson(TerasConfig.getApiUrl() + "/smartrotom/starbank/" + op.name().toLowerCase(Locale.ROOT),
                GSON.toJson(new BancoBody(TerasConfig.getId(), playerId.toString(), op.name(), amount)));
    }

    private record BancoBody(String server, String uuid, String operacion, BigDecimal cantidad) {}

    private record TrainerDefeat(String server, String uuid, int money) {}

    private record BattleReportBody(String server, String uuid, String logro, boolean victoria,
                                    String name1, String name2,
                                    List<TeamMember> team1, List<TeamMember> team2, String replay) {}

    /** Caller-facing battle report; {@code server} is added by {@link #saveBattle}. */
    public record BattleReport(String uuid, String logro, boolean victoria,
                               String name1, String name2,
                               List<TeamMember> team1, List<TeamMember> team2, String replay) {}
}
