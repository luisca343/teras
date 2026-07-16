package es.boffmedia.teras.quests;

import com.google.gson.Gson;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.quests.model.ApiResponse;
import es.boffmedia.teras.quests.model.PlayerQuestProgress;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Serializes the quest payloads for both transports (the mcef bridge and the HTTP API), so the wire
 * shapes live in one place.
 *
 * <p>Plain {@link Gson} on purpose: its default of <b>omitting nulls</b> is what makes the
 * {@code QuestProgress}/{@code QuestInfo} split produce the two different bodies 1.16.5 sent. Enabling
 * {@code serializeNulls()} would silently break the backend's merge by emitting
 * {@code objectives: null} over good data.</p>
 *
 * <p>CustomNPCs-coupled via {@link QuestService}: only reachable behind
 * {@link QuestBridge#isAvailable()}.</p>
 */
public final class QuestJson {
    private QuestJson() {}

    private static final Gson GSON = new Gson();

    /** Empty catalog body, for when the quest system can't answer. */
    public static final String EMPTY_CATALOG =
            "{\"success\":true,\"message\":\"Success\",\"data\":{\"quests\":{},\"categories\":{},\"dialogs\":{}}}";
    /** Empty progress body (the {@code /quests/user} route is unwrapped). */
    public static final String EMPTY_PROGRESS = "{\"quests\":{}}";
    /** Empty merged body, for the in-game page. */
    public static final String EMPTY_MERGED =
            "{\"quests\":[],\"categories\":{},\"dialogs\":[],\"npcs\":[]}";

    /** {@code GET /quests/all} — wrapped in the {@code ApiResponse} envelope the backend unwraps. */
    public static String catalogJson() {
        try {
            return GSON.toJson(ApiResponse.success(QuestService.buildCatalog()));
        } catch (Exception e) {
            Teras.LOGGER.error("Failed building the quest catalog", e);
            return EMPTY_CATALOG;
        }
    }

    /**
     * {@code GET /quests/user/{uuid}} — <b>not</b> wrapped: the backend reads {@code response.data.quests}
     * on this route (vs {@code response.data.data} on /quests/all). Empty when the player is unknown,
     * so the caller can 404.
     */
    public static Optional<String> progressJson(UUID uuid) {
        try {
            return QuestService.buildProgress(uuid).map(QuestJson::progressBody);
        } catch (Exception e) {
            Teras.LOGGER.error("Failed building quest progress for {}", uuid, e);
            return Optional.of(EMPTY_PROGRESS);
        }
    }

    private static String progressBody(PlayerQuestProgress progress) {
        return GSON.toJson(Map.of("quests", progress.quests(), "categories", progress.categories()));
    }

    /** The mcef {@code getMisiones} — catalog and progress already merged, for the in-game page. */
    public static String mergedJson(UUID uuid) {
        try {
            return GSON.toJson(QuestService.buildMerged(uuid));
        } catch (Exception e) {
            Teras.LOGGER.error("Failed building merged quests for {}", uuid, e);
            return EMPTY_MERGED;
        }
    }
}
