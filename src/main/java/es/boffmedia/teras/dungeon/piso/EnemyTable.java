package es.boffmedia.teras.dungeon.piso;

import java.util.ArrayList;
import java.util.List;

/**
 * What fights in a piso, and in what proportion. <b>Relative only</b> — the counts here are the
 * piso's own shape of encounter, and the tramo's {@link Dificultad} scales them by depth. That
 * separation is what makes the shared piso catalog workable: the same Cuevas is correctly tuned in
 * tramo 1 of one dungeon and tramo 4 of another, because difficulty is a property of position in a
 * run rather than of place.
 *
 * @param countMin    smallest wave, before dificultad
 * @param countMax    largest wave, before dificultad. Also the bar a room's floor {@code spawn}
 *                    marker count is audited against, since the spawner cycles positions and a wave
 *                    larger than the markers stacks enemies on one block
 * @param oleada      the fighting roster, weighted
 * @param ambientales atmosphere: spawned on room entry, <b>excluded from the kill ledger</b>, swept
 *                    with the floor. A bat is scenery, and a bat inside the ledger seals a room
 *                    until the party has hunted down every one
 */
public record EnemyTable(int countMin,
                         int countMax,
                         List<SpawnRef> oleada,
                         List<AmbientRef> ambientales) {

    /**
     * A flavour spawn and how many of it. Weightless on purpose: ambient mobs are not drawn against
     * each other, they are all placed — a cave has bats <i>and</i> drips, it does not roll between
     * them. {@code tab} carries the CustomNPCs clone tab when {@code kind} is {@code cnpc}, so an
     * ambient bat can be a clone (which Pixelmon leaves alone) rather than a vanilla mob.
     */
    public record AmbientRef(String kind, String id, int tab, int cantidad) {
        public AmbientRef {
            cantidad = Math.max(1, cantidad);
        }
    }

    public EnemyTable {
        countMin = Math.max(0, countMin);
        countMax = Math.max(countMin, countMax);
        oleada = oleada == null ? List.of() : List.copyOf(oleada);
        ambientales = ambientales == null ? List.of() : List.copyOf(ambientales);
    }

    /** The table a piso that declared none falls back to: nothing. */
    public static final EnemyTable EMPTY = new EnemyTable(0, 0, List.of(), List.of());

    public boolean isEmpty() {
        return oleada.isEmpty();
    }

    /**
     * The roster as the draw should see it at this depth: elite entries have their weight raised so
     * the mix shifts toward them, which is the axis meant to carry as much of a floor's difficulty
     * as the stat curves do.
     */
    public List<SpawnRef> rosterAt(double dificultad) {
        double eliteWeight = Dificultad.eliteWeight(dificultad);
        List<SpawnRef> shifted = new ArrayList<>(oleada.size());
        for (SpawnRef ref : oleada) {
            if (!ref.elite()) {
                shifted.add(ref);
                continue;
            }
            int weight = (int) Math.max(1, Math.round(ref.peso() * eliteWeight));
            shifted.add(new SpawnRef(ref.kind(), ref.id(), ref.tab(), weight, true,
                    ref.vida(), ref.dano(), ref.escala()));
        }
        return shifted;
    }

    /**
     * Wave size at this depth, before the room's own multipliers (cell count, a challenge's later
     * waves). Rounded rather than truncated so a small dificultad is not swallowed entirely.
     */
    public int countMinAt(double dificultad) {
        return (int) Math.max(0, Math.round(countMin * Dificultad.count(dificultad)));
    }

    public int countMaxAt(double dificultad) {
        return Math.max(countMinAt(dificultad),
                (int) Math.round(countMax * Dificultad.count(dificultad)));
    }

    /** Why this table cannot be used, or empty. A piso with no roster simply never fights. */
    public List<String> problems(String pisoId) {
        List<String> problems = new ArrayList<>();
        for (SpawnRef ref : oleada) {
            if (ref.id() == null || ref.id().isBlank()) {
                problems.add("piso '" + pisoId + "' has an enemy entry with no id");
            }
        }
        for (AmbientRef ref : ambientales) {
            if (ref.id() == null || ref.id().isBlank()) {
                problems.add("piso '" + pisoId + "' has an ambient entry with no id");
            }
        }
        return problems;
    }
}
