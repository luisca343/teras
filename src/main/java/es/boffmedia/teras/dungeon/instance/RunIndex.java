package es.boffmedia.teras.dungeon.instance;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player to run id, so "which run is this player in?" is a lookup rather than a scan over every
 * party. It answers per hit, per right-click and once per player per second through
 * {@code DungeonHealth.isInRun}, which is what made the old stream over every party stop being
 * affordable.
 *
 * <p>Dropping is <b>by run id</b> rather than by walking the run's party, so a run whose party was
 * emptied on the way out still cleans up completely.</p>
 */
final class RunIndex {

    private final Map<UUID, Integer> membership = new HashMap<>();

    /** Points every member at {@code runId}. */
    void index(Collection<UUID> members, int runId) {
        for (UUID member : members) {
            membership.put(member, runId);
        }
    }

    /** The run {@code player} belongs to, or null. */
    Integer runIdOf(UUID player) {
        return membership.get(player);
    }

    /** One member walked out. */
    void remove(UUID player) {
        membership.remove(player);
    }

    /** Drops every membership pointing at {@code runId}. */
    void unindex(int runId) {
        membership.values().removeIf(id -> id == runId);
    }

    int size() {
        return membership.size();
    }

    void clear() {
        membership.clear();
    }
}
