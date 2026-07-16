package es.boffmedia.teras.quests.model;

/** A quest's state for one player, as reported to the SmartRotom web. Ported 1:1 from 1.16.5. */
public enum QuestStatus {
    ACTIVE,
    COMPLETED,
    FAILED,
    NOT_STARTED,
    AVAILABLE,
    LOCKED
}
