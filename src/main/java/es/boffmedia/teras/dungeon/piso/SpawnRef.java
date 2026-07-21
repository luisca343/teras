package es.boffmedia.teras.dungeon.piso;

/**
 * One line of a piso's enemy table: what may fight here, and how often relative to the rest.
 *
 * <p>A ref names an enemy, it does not define one. Definitions — stats, behaviours, movement,
 * abilities — live in the shared bestiary, so the same {@code saqueador_cuevas} referenced by two
 * pisos is one enemy rather than two that drifted apart. {@code kind} is what says where to look:
 * absent, the id is a first-party variant; {@code entity} makes it a registered entity id (vanilla
 * or modded); {@code cnpc} a CustomNPCs clone by tab and name.</p>
 *
 * <p>The stat overrides exist because a plain entity id has no other way to be tuned: a spawned
 * {@code minecraft:slime} arrives with vanilla numbers and nothing in the table could say
 * otherwise. They are multipliers rather than absolutes so a piso never has to restate a
 * definition's stats to nudge one — and so an override composes with the tramo's dificultad
 * instead of overwriting it.</p>
 *
 * @param kind   where the id resolves, or null for the first-party bestiary
 * @param id     the enemy's id in whichever registry {@code kind} selects
 * @param tab    CustomNPCs tab; meaningless for the other kinds
 * @param peso   relative weight within its table, at least 1
 * @param elite  whether the tramo's dificultad should bias the draw <i>toward</i> this entry. The
 *               roster shift is meant to carry as much of a floor's difficulty as the stat curves
 *               do, and it cannot without knowing which entries are the hard ones
 * @param vida   health multiplier applied on top of the definition's, 1.0 for none
 * @param dano   damage multiplier, 1.0 for none
 * @param escala render scale multiplier, 1.0 for none
 */
public record SpawnRef(String kind,
                       String id,
                       int tab,
                       int peso,
                       boolean elite,
                       double vida,
                       double dano,
                       double escala) {

    public SpawnRef {
        // Clamped rather than rejected, for the same reason WeightedRef clamps: a zero weight in a
        // hand-edited table is a line the author meant to include, and silently dropping it out of
        // the draw is harder to notice than it appearing slightly too often.
        peso = Math.max(1, peso);
    }

    /** The common case: a bestiary enemy at a weight. */
    public static SpawnRef of(String id, int peso) {
        return new SpawnRef(null, id, 0, peso, false, 1.0, 1.0, 1.0);
    }

    /** A registered entity id — vanilla or any mod's, no dependency either way. */
    public static SpawnRef entity(String id, int peso) {
        return new SpawnRef("entity", id, 0, peso, false, 1.0, 1.0, 1.0);
    }

    /**
     * A CustomNPCs clone by tab and name. Preferred over {@link #entity} for anything that resembles
     * a vanilla monster on a Pixelmon server: a clone is not a vanilla monster, so it survives the
     * spawn-replacement that would otherwise delete a real silverfish or slime the instant it joins.
     */
    public static SpawnRef cnpc(String id, int tab, int peso) {
        return new SpawnRef("cnpc", id, tab, peso, false, 1.0, 1.0, 1.0);
    }

    public SpawnRef asElite() {
        return new SpawnRef(kind, id, tab, peso, true, vida, dano, escala);
    }

    public SpawnRef scaled(double vidaMul, double danoMul, double escalaMul) {
        return new SpawnRef(kind, id, tab, peso, elite, vidaMul, danoMul, escalaMul);
    }

    /** True when nothing here overrides the definition, so the spawner can skip the attribute work. */
    public boolean isPlain() {
        return vida == 1.0 && dano == 1.0 && escala == 1.0;
    }
}
