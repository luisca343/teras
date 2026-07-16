package es.boffmedia.teras.quests.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the JSON the SmartRotom backend reads. These are contract tests, not model tests: the
 * backend merges {@code /quests/all} with {@code /quests/user/{uuid}} as
 * {@code {...systemQuest, ...userQuest}}, so a field appearing on the wrong half silently overwrites
 * good data with a default — the failure mode that emptied the board in the first place.
 *
 * <p>No CustomNPCs or Minecraft runtime needed: the model package is deliberately free of both.</p>
 */
class QuestWireShapeTest {

    private static final Gson GSON = new Gson();

    private static QuestInfo definition() {
        return QuestInfo.definition(1, "Cazador novato", "log", "done", false, 0, -1, "CAPTURA",
                new QuestRequirement());
    }

    private static QuestProgress progress() {
        QuestProgress p = QuestProgress.of(1, QuestStatus.ACTIVE);
        p.setDialogId(12);
        p.setNpcName("Profesor");
        p.setObjectives(List.of(new QuestObjective("Pikachu", 3, 5)));
        p.setRewards(List.of(new QuestReward("minecraft:diamond", 2)));
        return p;
    }

    // ---- /quests/all : the cached catalog half ----

    @Test
    void catalogQuestCarriesTheDefinitionAndTheNotStartedStub() {
        JsonObject json = GSON.toJsonTree(definition()).getAsJsonObject();

        assertEquals(1, json.get("id").getAsInt());
        assertEquals("Cazador novato", json.get("name").getAsString());
        assertEquals("log", json.get("logText").getAsString());
        assertEquals("done", json.get("completeText").getAsString());
        assertEquals("CAPTURA", json.get("category").getAsString());
        assertTrue(json.has("requirements"));
        // 1.16.5 sent the stub status here; the backend replaces it from the user half.
        assertEquals("NOT_STARTED", json.get("status").getAsString());
    }

    @Test
    void catalogQuestOmitsThePerPlayerFields() {
        JsonObject json = GSON.toJsonTree(definition()).getAsJsonObject();

        // These belong to the user half. If they appeared here as nulls/empties, the backend's
        // {...systemQuest, ...userQuest} spread would still be fine — but Gson omitting them is what
        // reproduces 1.16.5 byte-for-byte, and what keeps the cached body small.
        assertFalse(json.has("objectives"), "objectives must come from /quests/user");
        assertFalse(json.has("rewards"), "rewards must come from /quests/user");
        assertFalse(json.has("npcName"), "npcName must come from /quests/user");
    }

    // ---- /quests/user/{uuid} : the live progress half ----

    @Test
    void progressCarriesOnlyThePerPlayerFields() {
        JsonObject json = GSON.toJsonTree(progress()).getAsJsonObject();

        assertEquals(1, json.get("id").getAsInt());
        assertEquals("ACTIVE", json.get("status").getAsString());
        assertEquals(12, json.get("dialogId").getAsInt());
        assertEquals("Profesor", json.get("npcName").getAsString());
        assertEquals(3, json.getAsJsonArray("objectives").get(0).getAsJsonObject().get("progress").getAsInt());
        assertEquals("minecraft:diamond",
                json.getAsJsonArray("rewards").get(0).getAsJsonObject().get("item").getAsString());
    }

    @Test
    void progressNeverLeaksDefinitionFieldsOverTheCachedOnes() {
        // The regression this guards: flattening QuestProgress/QuestInfo into one class would make
        // `repeatable` (a primitive boolean) serialize as false here. The backend spreads userQuest
        // LAST, so that false would overwrite the real value from the 4h-cached catalog.
        JsonObject json = GSON.toJsonTree(progress()).getAsJsonObject();

        assertFalse(json.has("repeatable"), "a primitive leaking here would overwrite the catalog");
        assertFalse(json.has("name"));
        assertFalse(json.has("logText"));
        assertFalse(json.has("completeText"));
        assertFalse(json.has("category"));
        assertFalse(json.has("requirements"));
        assertFalse(json.has("type"));
        assertFalse(json.has("nextQuest"));
    }

    // ---- the merge ----

    @Test
    void mergedQuestHasBothHalves() {
        JsonObject json = GSON.toJsonTree(definition().mergedWith(progress())).getAsJsonObject();

        assertEquals("Cazador novato", json.get("name").getAsString());   // definition
        assertEquals("ACTIVE", json.get("status").getAsString());          // progress wins
        assertEquals(12, json.get("dialogId").getAsInt());
        assertEquals("Profesor", json.get("npcName").getAsString());
        assertTrue(json.has("objectives"));
        assertTrue(json.has("rewards"));
    }

    @Test
    void mergingNullProgressKeepsTheDefinitionUnstarted() {
        // A player with no saved data still gets the catalog, each quest at NOT_STARTED.
        JsonObject json = GSON.toJsonTree(definition().mergedWith(null)).getAsJsonObject();

        assertEquals("NOT_STARTED", json.get("status").getAsString());
        assertEquals("Cazador novato", json.get("name").getAsString());
    }

    @Test
    void mergeDoesNotMutateTheCachedDefinition() {
        QuestInfo cached = definition();
        cached.mergedWith(progress());
        // buildMerged runs per request against one catalog; mutating it would leak one player's
        // progress into the next player's response.
        JsonObject json = GSON.toJsonTree(cached).getAsJsonObject();
        assertEquals("NOT_STARTED", json.get("status").getAsString());
        assertFalse(json.has("npcName"));
    }

    // ---- envelopes ----

    @Test
    void catalogIsWrappedInTheApiResponseEnvelope() {
        // The backend reads response.data.data.quests on this route.
        QuestCatalog catalog = new QuestCatalog(Map.of(1, definition()),
                Map.of("CAPTURA", List.of(1)), Map.of());
        JsonObject json = GSON.toJsonTree(ApiResponse.success(catalog)).getAsJsonObject();

        assertTrue(json.get("success").getAsBoolean());
        assertEquals("Success", json.get("message").getAsString());
        JsonObject data = json.getAsJsonObject("data");
        assertTrue(data.has("quests"));
        assertTrue(data.has("categories"));
        assertTrue(data.has("dialogs"));
        // 1.16.5 never sent npcs here; the backend defaults it to [].
        assertFalse(data.has("npcs"));
    }

    @Test
    void catalogKeysQuestsAndDialogsById() {
        QuestCatalog catalog = new QuestCatalog(Map.of(1, definition()),
                Map.of("CAPTURA", List.of(1)),
                Map.of(12, new DialogInfo(12, "Saludo", "¡Hola!", 1, new QuestRequirement())));
        JsonObject data = GSON.toJsonTree(ApiResponse.success(catalog))
                .getAsJsonObject().getAsJsonObject("data");

        assertTrue(data.getAsJsonObject("quests").has("1"), "quests keyed by quest id");
        assertTrue(data.getAsJsonObject("dialogs").has("12"), "dialogs keyed by dialog id");
        assertEquals(1, data.getAsJsonObject("categories").getAsJsonArray("CAPTURA").get(0).getAsInt());
    }

    @Test
    void dialogCarriesItsTextAndGivers() {
        // The whole point: this text is what "La Bitácora" renders and what went missing.
        DialogInfo dialog = new DialogInfo(12, "Saludo", "¡Hola, entrenador!", 1, new QuestRequirement());
        dialog.setNpcLocations(List.of(
                new NpcData("Profesor", 12, "steve.png", 100.5, 64, -20.5, "minecraft:overworld", "u")));
        JsonObject json = GSON.toJsonTree(dialog).getAsJsonObject();

        assertEquals("¡Hola, entrenador!", json.get("text").getAsString());
        assertEquals("Saludo", json.get("name").getAsString());
        assertEquals(1, json.get("questId").getAsInt());
        JsonObject giver = json.getAsJsonArray("npcLocations").get(0).getAsJsonObject();
        assertEquals("Profesor", giver.get("name").getAsString());
        assertEquals("steve.png", giver.get("skin").getAsString());
        assertEquals(100.5, giver.get("x").getAsDouble());
    }

    @Test
    void mergedResponseKeepsCategoryNames() {
        // Deliberate divergence from the backend, which does Object.values(categories) and throws the
        // names away. The board's Region type wants the {name: [questId]} map.
        UserQuestData merged = new UserQuestData(List.of(definition()),
                Map.of("CAPTURA", List.of(1)), List.of(), List.of());
        JsonObject json = GSON.toJsonTree(merged).getAsJsonObject();

        assertTrue(json.getAsJsonArray("quests").size() > 0);
        assertTrue(json.has("dialogs"));
        assertEquals(1, json.getAsJsonObject("categories").getAsJsonArray("CAPTURA").get(0).getAsInt());
    }
}
