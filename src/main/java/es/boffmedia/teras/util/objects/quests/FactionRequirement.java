package es.boffmedia.teras.util.objects.quests;

import noppes.npcs.constants.EnumAvailabilityFaction;
import noppes.npcs.constants.EnumAvailabilityFactionType;

public class FactionRequirement {
    private int factionId;
    private EnumAvailabilityFactionType factionAvailable;
    private EnumAvailabilityFaction factionStance;

    public FactionRequirement(int factionId, EnumAvailabilityFactionType factionAvailable, EnumAvailabilityFaction factionStance) {
        this.factionId = factionId;
        this.factionAvailable = factionAvailable;
        this.factionStance = factionStance;
    }

    public int getFactionId() {
        return factionId;
    }

    public void setFactionId(int factionId) {
        this.factionId = factionId;
    }

    public EnumAvailabilityFactionType getFactionAvailable() {
        return factionAvailable;
    }

    public void setFactionAvailable(EnumAvailabilityFactionType factionAvailable) {
        this.factionAvailable = factionAvailable;
    }

    public EnumAvailabilityFaction getFactionStance() {
        return factionStance;
    }

    public void setFactionStance(EnumAvailabilityFaction factionStance) {
        this.factionStance = factionStance;
    }
}
