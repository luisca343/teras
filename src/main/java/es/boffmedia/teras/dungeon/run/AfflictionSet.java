package es.boffmedia.teras.dungeon.run;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a run — or one player in it — is carrying.
 *
 * <p><b>No cap.</b> A party may take as many as it likes, and the run gets as hard as it lets it.
 * The ceiling is survival, not a number in the code, which is the point: the difficulty of a late
 * floor should be something the players authored.</p>
 *
 * <p>A set rather than a list: taking the same affliction twice is not twice as bad, it is the same
 * drawback, and stacking one would quietly double an effect nobody expected to compound.</p>
 *
 * <p>Pure, so the accounting is testable without a server. Two of these exist per run — one on
 * {@link es.boffmedia.teras.dungeon.instance.DungeonRun} for the party's, one on
 * {@link PlayerRunState} for each member's own.</p>
 */
public final class AfflictionSet {

    private final Set<String> ids = new LinkedHashSet<>();

    /** True when it was not already carried — false means the offer should not have been made. */
    public boolean add(Afliccion afliccion) {
        return afliccion != null && ids.add(afliccion.id());
    }

    /** True when it was carried and is now shed. The curse room's half of the currency. */
    public boolean remove(Afliccion afliccion) {
        return afliccion != null && ids.remove(afliccion.id());
    }

    public boolean has(Afliccion afliccion) {
        return afliccion != null && ids.contains(afliccion.id());
    }

    public int size() {
        return ids.size();
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    /** In the order taken, which is the order the HUD shows them. */
    public List<Afliccion> carried() {
        List<Afliccion> out = new ArrayList<>();
        for (String id : ids) {
            Afliccion afliccion = Afliccion.byId(id);
            if (afliccion != null) {
                out.add(afliccion);
            }
        }
        return out;
    }

    public List<String> ids() {
        return List.copyOf(ids);
    }

    /** Everything of this scope that is not carried yet — what a curse room may offer. */
    public List<Afliccion> offerable(Afliccion.Scope scope) {
        return Afliccion.all().stream()
                .filter(a -> a.scope() == scope)
                .filter(a -> !has(a))
                .toList();
    }

    public void clear() {
        ids.clear();
    }
}
