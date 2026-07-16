package es.boffmedia.teras.quests.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the requirement model's dedup/sentinel rules. These decide what the SmartRotom web is
 * told a quest is gated on, and they run without CustomNPCs because the model deliberately stores the
 * availability enums as strings.
 */
class QuestRequirementTest {

    @Test
    void dropsUnsetQuestAndDialogSentinels() {
        QuestRequirement req = new QuestRequirement();
        req.addQuest(-1);
        req.addDialog(-1);
        req.addQuest(7);
        req.addDialog(9);

        assertEquals(java.util.List.of(7), req.getRequiredQuests());
        assertEquals(java.util.List.of(9), req.getRequiredDialogs());
    }

    @Test
    void deduplicatesRepeatedQuestAndDialogIds() {
        QuestRequirement req = new QuestRequirement();
        req.addQuest(3);
        req.addQuest(3);
        req.addDialog(4);
        req.addDialog(4);

        assertEquals(1, req.getRequiredQuests().size());
        assertEquals(1, req.getRequiredDialogs().size());
    }

    @Test
    void keepsOnlyTheFirstGatePerFactionAndDropsUnsetOnes() {
        QuestRequirement req = new QuestRequirement();
        req.addFactionRequirement(-1, "AVAILABLE", "HOSTILE");
        req.addFactionRequirement(2, "AVAILABLE", "FRIENDLY");
        req.addFactionRequirement(2, "UNAVAILABLE", "HOSTILE");

        assertEquals(1, req.getFactionRequirements().size());
        FactionRequirement kept = req.getFactionRequirements().get(0);
        assertEquals(2, kept.getFactionId());
        assertEquals("FRIENDLY", kept.getFactionStance());
    }

    @Test
    void keepsOnlyTheFirstGatePerScoreboardObjectiveAndDropsBlankOnes() {
        QuestRequirement req = new QuestRequirement();
        req.addScoreboardRequirement("", "EQUAL", 1);
        req.addScoreboardRequirement(null, "EQUAL", 1);
        req.addScoreboardRequirement("kills", "EQUAL", 5);
        req.addScoreboardRequirement("kills", "LESS", 9);

        assertEquals(1, req.getScoreboardRequirements().size());
        ScoreboardRequirement kept = req.getScoreboardRequirements().get(0);
        assertEquals("kills", kept.getScoreboardObjective());
        assertEquals(5, kept.getScoreboardValue());
    }

    @Test
    void allowsDistinctFactionsAndObjectives() {
        QuestRequirement req = new QuestRequirement();
        req.addFactionRequirement(1, "AVAILABLE", "FRIENDLY");
        req.addFactionRequirement(2, "AVAILABLE", "HOSTILE");
        req.addScoreboardRequirement("a", "EQUAL", 1);
        req.addScoreboardRequirement("b", "EQUAL", 2);

        assertEquals(2, req.getFactionRequirements().size());
        assertEquals(2, req.getScoreboardRequirements().size());
        assertTrue(req.getRequiredQuests().isEmpty());
    }
}
