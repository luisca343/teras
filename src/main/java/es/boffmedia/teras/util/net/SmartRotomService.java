package es.boffmedia.teras.util.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.model.TeamMember;
import es.boffmedia.teras.dex.DexStatus;
import es.boffmedia.teras.dex.api.DexScan;
import es.boffmedia.teras.model.world.CajaGrant;
import es.boffmedia.teras.model.world.ObjetoMC;
import es.boffmedia.teras.model.world.PokemonSpec;
import es.boffmedia.teras.util.TerasConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    /**
     * Mirrors one Pokédex registration to the backend.
     *
     * <p><b>Call this from an engine's dex-change listener and nowhere else</b> ({@code dex.*.*DexSync}):
     * that event is the one place a registration is known to have happened exactly once. See
     * {@code docs/DEX.md}.</p>
     */
    public static void registerPokedex(UUID playerId, DexScan scan, DexStatus status) {
        if (playerId == null || scan == null || status == null) {
            return;
        }
        if (scan.dex() <= 0) {
            // The backend keys registrations by national dex number; a custom species has none.
            Teras.LOGGER.warn("Skipping Pokédex registration for {}: species has no national dex number "
                    + "(form={}, palette={})", playerId, scan.form(), scan.palette());
            return;
        }
        HttpText.postJson(TerasConfig.getApiUrl() + "/smartrotom/pokemon/register",
                registrationBody(TerasConfig.getId(), playerId, scan, status));
    }

    /**
     * The request body for {@link #registerPokedex}, split out (like {@link #parseBalance}) so the wire
     * contract can be asserted without a Minecraft runtime, config, or network. These field names are
     * the contract.
     */
    static String registrationBody(String server, UUID playerId, DexScan scan, DexStatus status) {
        return GSON.toJson(new PokedexRegistration(server, playerId.toString(), scan.dex(),
                scan.form(), scan.palette(), status.wireValue()));
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

    /** How many times {@link #confirmCaja} POSTs before giving up, and the pause between tries. The
     *  backend's 5-minute reservation TTL is long enough to swallow these retries. */
    private static final int CONFIRM_ATTEMPTS = 3;
    private static final long CONFIRM_RETRY_DELAY_MS = 1_000L;

    /**
     * A {@code /caja/reserve} result: what the player is owed ({@link #grant}) plus the
     * {@link #reservationId} that {@link #confirmCaja} spends. {@code reservationId} is {@code null}
     * exactly when nothing was owed — do not confirm in that case.
     */
    public record Reservation(String reservationId, CajaGrant grant) {}

    /**
     * Soft-locks what {@code playerId} is owed from {@code source} (narrowed to {@code ids} when
     * non-empty) <b>without spending it</b>, returning the grant and the reservation to confirm once
     * delivered. <b>Blocks</b> — call from {@link es.boffmedia.teras.Teras#EXECUTOR}.
     *
     * <p>Returns {@code null} on any transport/parse failure (grant nothing, nothing was locked). A
     * non-null {@link Reservation} with a {@code null} {@code reservationId} (and empty grant) means the
     * reserve succeeded and the player was owed nothing — also grant nothing, but not an error.</p>
     *
     * <p>Unlike every other payload in this class, the body carries <b>no {@code server} field</b>.
     * That route is on the backend's {@code MinecraftMiddleware} exclude list precisely because the
     * mod sends none, and its DTO rejects unknown properties — sending one is a measured 400. {@code ids}
     * is sent only when non-empty; the DTO's {@code ids} is optional and mine omits it.</p>
     */
    public static Reservation reserveCaja(UUID playerId, String source, List<Integer> ids) {
        if (playerId == null || !HttpText.isValidIdentifier(source)) {
            Teras.LOGGER.warn("DarCaja: refusing to reserve with uuid={} source='{}'", playerId, source);
            return null;
        }
        JsonObject body = new JsonObject();
        body.addProperty("uuid", playerId.toString());
        body.addProperty("source", source);
        if (ids != null && !ids.isEmpty()) {
            JsonArray idArray = new JsonArray();
            ids.forEach(idArray::add);
            body.add("ids", idArray);
        }
        String response = HttpText.postJsonAuthed(
                TerasConfig.getApiUrl() + "/smartrotom/caja/reserve", GSON.toJson(body));
        if (response == null) {
            return null;
        }
        return parseReservation(response);
    }

    /**
     * Finalizes (spends) a reservation. <b>Blocks</b> — call from {@link es.boffmedia.teras.Teras#EXECUTOR},
     * after the grant has landed on an online player.
     *
     * <p>Idempotent server-side: a replay, or a confirm of an already-spent/expired reservation, spends
     * nothing. Retries {@link #CONFIRM_ATTEMPTS} times over a few seconds because the only dupe window is
     * "delivered but confirm never landed": a lost confirm lets the still-owed rows be re-delivered on a
     * re-claim after the TTL. Returns {@code true} iff the API answered 2xx (transport success).</p>
     */
    public static boolean confirmCaja(UUID playerId, String reservationId) {
        if (playerId == null || reservationId == null || reservationId.isBlank()) {
            return false;
        }
        JsonObject body = new JsonObject();
        body.addProperty("uuid", playerId.toString());
        body.addProperty("reservationId", reservationId);
        String json = GSON.toJson(body);
        for (int attempt = 1; attempt <= CONFIRM_ATTEMPTS; attempt++) {
            String response = HttpText.postJsonAuthed(
                    TerasConfig.getApiUrl() + "/smartrotom/caja/confirm", json);
            if (response != null) {
                return true;
            }
            if (attempt < CONFIRM_ATTEMPTS) {
                try {
                    Thread.sleep(CONFIRM_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * A {@code /caja/reserve} body into a {@link Reservation}, or {@code null} if it isn't one. Reuses
     * {@link #parseGrant} for {@code objetos}/{@code pokemon} (identical shapes) and reads
     * {@code reservationId} off the <b>root</b> — a string, or JSON null when nothing was owed.
     */
    static Reservation parseReservation(String body) {
        CajaGrant grant = parseGrant(body);
        if (grant == null) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonElement id = root.get("reservationId");
            String reservationId = (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString())
                    ? id.getAsString() : null;
            return new Reservation(reservationId, grant);
        } catch (JsonParseException e) {
            return null;
        }
    }

    /**
     * The {@code objetos} and {@code pokemon} arrays out of a caja response, or {@code null} if the
     * body isn't one.
     *
     * <p>Both are read off the <b>root</b>: that route opts out of the API's global
     * {@code {success, statusCode, data}} envelope. Reading {@code data} would find nothing — and
     * silently grant nothing on every claim. {@code objetos} being a JSON array is the test for "this
     * is a caja response"; {@code pokemon} may be absent (an older backend, or an item-only source) and
     * is treated as empty, which keeps a mine claim working against either shape.</p>
     */
    static CajaGrant parseGrant(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement objetos = root.get("objetos");
            if (objetos == null || !objetos.isJsonArray()) {
                return null;
            }
            return new CajaGrant(parseObjetos(objetos.getAsJsonArray()), parsePokemon(root.get("pokemon")));
        } catch (JsonParseException | NumberFormatException e) {
            return null;
        }
    }

    /** Entries missing an {@code id} are dropped; a missing {@code cantidad} is left at 0 to be clamped. */
    private static List<ObjetoMC> parseObjetos(JsonArray objetos) {
        List<ObjetoMC> result = new ArrayList<>();
        for (JsonElement element : objetos) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement id = entry.get("id");
            if (id == null || !id.isJsonPrimitive() || id.getAsString().isEmpty()) {
                continue;
            }
            result.add(new ObjetoMC(id.getAsString(), intOrDefault(entry.get("cantidad"), 0)));
        }
        return result;
    }

    /** Entries missing a {@code spec} are dropped; a missing {@code cantidad} means one. */
    private static List<PokemonSpec> parsePokemon(JsonElement pokemon) {
        List<PokemonSpec> result = new ArrayList<>();
        if (pokemon == null || !pokemon.isJsonArray()) {
            return result;
        }
        for (JsonElement element : pokemon.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement spec = entry.get("spec");
            if (spec == null || !spec.isJsonPrimitive() || spec.getAsString().isBlank()) {
                continue;
            }
            result.add(new PokemonSpec(spec.getAsString(), intOrDefault(entry.get("cantidad"), 1)));
        }
        return result;
    }

    private static int intOrDefault(JsonElement value, int fallback) {
        return (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
                ? value.getAsInt() : fallback;
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

    /** Wire shape of {@code /smartrotom/pokemon/register}; see {@link #registerPokedex}. */
    private record PokedexRegistration(String server, String uuid, int pokemonId, String form,
                                       String palette, int status) {}

    private record BattleReportBody(String server, String uuid, String logro, boolean victoria,
                                    String name1, String name2,
                                    List<TeamMember> team1, List<TeamMember> team2, String replay) {}

    /** Caller-facing battle report; {@code server} is added by {@link #saveBattle}. */
    public record BattleReport(String uuid, String logro, boolean victoria,
                               String name1, String name2,
                               List<TeamMember> team1, List<TeamMember> team2, String replay) {}
}
