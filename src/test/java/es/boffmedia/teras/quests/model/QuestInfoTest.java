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
 * Tests for the quest DTO's dirty-check and its JSON contract. {@link QuestInfo#equals} decides
 * whether {@code misiones.json} is rewritten on every dialog open, so "definition changed" vs
 * "per-player state changed" has to stay exactly right.
 */
class QuestInfoTest {

    private static final Gson GSON = new Gson();

    private static QuestInfo definition() {
        QuestInfo info = new QuestInfo();
        info.setId(1);
        info.setName("Cazador novato");
        info.setNpcName("Profesor");
        info.setSkin("steve.png");
        info.setCategory("CAPTURA");
        info.setLogText("log");
        info.setCompleteText("done");
        info.setX(10);
        info.setY(64);
        info.setZ(-20);
        return info;
    }

    @Test
    void identicalDefinitionsAreEqual() {
        assertEquals(definition(), definition());
        assertEquals(definition().hashCode(), definition().hashCode());
    }

    @Test
    void perPlayerStateIsIgnoredSoItDoesNotRewriteTheFile() {
        QuestInfo a = definition();
        QuestInfo b = definition();
        b.setStatus(QuestStatus.COMPLETED);
        b.setObjectives(List.of(new QuestObjective("Pikachu", 3, 5)));
        b.setRewards(List.of(new QuestReward("minecraft:diamond", 2)));

        assertEquals(a, b, "status/objectives/rewards are per-player and must not count as a change");
    }

    @Test
    void definitionChangesAreDetected() {
        QuestInfo moved = definition();
        moved.setX(999);
        assertFalse(definition().equals(moved), "an NPC that moved is a definition change");

        QuestInfo renamed = definition();
        renamed.setName("Otro nombre");
        assertFalse(definition().equals(renamed));

        QuestInfo recategorised = definition();
        recategorised.setCategory("DERROTA");
        assertFalse(definition().equals(recategorised));
    }

    @Test
    void nullIsNotEqualAndUnknownQuestCountsAsChanged() {
        // MisionesStore.put() compares against a null "known" value on first sight; that must be a
        // change, otherwise a brand-new quest would never be written.
        assertFalse(definition().equals(null));
    }

    @Test
    void serializesTheFieldNamesTheWebReads() {
        QuestInfo info = definition();
        info.setStatus(QuestStatus.ACTIVE);
        info.setObjectives(List.of(new QuestObjective("Pikachu", 3, 5)));
        info.setRewards(List.of(new QuestReward("minecraft:diamond", 2)));

        JsonObject json = GSON.toJsonTree(info).getAsJsonObject();

        assertEquals(1, json.get("id").getAsInt());
        assertEquals("Cazador novato", json.get("name").getAsString());
        assertEquals("Profesor", json.get("npcName").getAsString());
        assertEquals("steve.png", json.get("skin").getAsString());
        assertEquals("ACTIVE", json.get("status").getAsString());
        assertEquals(3, json.getAsJsonArray("objectives").get(0).getAsJsonObject().get("progress").getAsInt());
        assertEquals("minecraft:diamond",
                json.getAsJsonArray("rewards").get(0).getAsJsonObject().get("item").getAsString());
    }

    @Test
    void misionesResponseKeepsTheLegacyEnvelopeShape() {
        // The 1.16.5 client decoded this into MisionesJugador { misiones, categorias }.
        MisionesResponse response = new MisionesResponse(List.of(definition()), Map.of("CAPTURA", 1));
        JsonObject json = GSON.toJsonTree(response).getAsJsonObject();

        assertTrue(json.has("misiones"));
        assertTrue(json.has("categorias"));
        assertEquals(1, json.getAsJsonArray("misiones").size());
        assertEquals(1, json.getAsJsonObject("categorias").get("CAPTURA").getAsInt());
    }
}
