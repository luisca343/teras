package es.boffmedia.teras.quests.model;

/**
 * A faction gate on a dialog/quest. Ported from 1.16.5, but the two CustomNPCs enums
 * ({@code EnumAvailabilityFactionType} / {@code EnumAvailabilityFaction}) are stored as their
 * {@code name()} strings rather than the enum types, so this package stays free of CustomNPCs
 * classes and is safe to load when the mod is absent. Gson serialized the enums by name anyway, so
 * the JSON the SmartRotom web reads is unchanged.
 */
public class FactionRequirement {
    private final int factionId;
    private final String factionAvailable;
    private final String factionStance;

    public FactionRequirement(int factionId, String factionAvailable, String factionStance) {
        this.factionId = factionId;
        this.factionAvailable = factionAvailable;
        this.factionStance = factionStance;
    }

    public int getFactionId() { return factionId; }
    public String getFactionAvailable() { return factionAvailable; }
    public String getFactionStance() { return factionStance; }
}
