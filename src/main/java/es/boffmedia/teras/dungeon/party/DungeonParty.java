package es.boffmedia.teras.dungeon.party;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A pre-run group: who descends together when the leader talks to the entrance NPC. Social only —
 * once a run starts, {@code DungeonRun}'s party map is the authority and this object is dissolved;
 * re-forming for the next run is one invite away.
 */
public final class DungeonParty {

    private final UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    DungeonParty(UUID leader) {
        this.leader = leader;
        members.add(leader);
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    /** Members in join order, leader first. */
    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    void add(UUID player) {
        members.add(player);
    }

    void remove(UUID player) {
        members.remove(player);
    }
}
