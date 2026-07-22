package es.boffmedia.teras.dungeon.gear;

/**
 * What a piece of gear does beyond its stat line — the choice of hook, and the shipped value of each
 * hook's numbers.
 *
 * <p>The numbers themselves live on {@link AbilityDef} so a piece can retune them from
 * {@code gear.json}; what is here is the <b>default</b> each falls back to and the <b>parameter
 * names</b> each understands. Both used to be scattered: a magnitude in the catalog, a wither level
 * of 1 written into the effect call, a shockwave radius of 2.5 written into the method. An ability's
 * numbers now live in one place, named.</p>
 *
 * <p>This enum stays the registry of implemented behaviour. Config composes from it — retunes,
 * combines, gives one piece several — but cannot add to it: a genuinely new behaviour is a new
 * constant here plus its hook in {@link GearEvents}. Same truth as {@code mecanica}, and worth
 * restating because "any capability from config" is what people expect and it is not achievable.</p>
 *
 * <p>Two are run-scoped by nature — {@link #BOTIN} credits the party purse and {@link #FENIX_MENOR}
 * writes {@code PlayerRunState} — and no-op outside a dungeon. The rest work wherever the gear is
 * carried: it is kept after a run, and a reward that stops working when you leave is a reward
 * players learn not to chase.</p>
 */
public enum GearAbility {
    NINGUNA(0),
    /** On hit: heals the attacker for a fraction of the damage dealt. */
    VAMPIRISMO(0.05),
    /** On hit: sets the target on fire. {@code magnitud} is seconds. */
    QUEMAZON(3),
    /**
     * On hit: applies wither. {@code magnitud} is seconds, {@code nivel} the effect level — the
     * second number the old one-magnitude shape had nowhere to put, so it was hard-coded to I.
     */
    DESGARRO(3),
    /**
     * On kill: damages and knocks back everything around the corpse. {@code magnitud} is the
     * fraction of the victim's maximum health dealt, {@code radio} the reach — the latter also
     * previously hard-coded.
     */
    ONDA(0.15),
    /** On kill: extra dungeon coins into the shared purse. {@code magnitud} is the flat amount. */
    BOTIN(2),
    /** While worn: reflects a fraction of incoming melee damage back at the attacker. */
    ESPINAS(0.15),
    /** While worn: grants the one-shot revive charm once per floor entry. */
    FENIX_MENOR(1);

    /** Effect level, for the abilities that apply one. */
    public static final String P_LEVEL = "nivel";

    /** Reach in blocks, for the abilities that have one. */
    public static final String P_RADIUS = "radio";

    private final double defaultMagnitude;

    GearAbility(double defaultMagnitude) {
        this.defaultMagnitude = defaultMagnitude;
    }

    /**
     * What {@code magnitud} falls back to when a piece does not set one.
     *
     * <p>Here rather than only in the catalog so a piece can carry an ability with no numbers at all
     * and still behave sensibly — which is what makes {@code "habilidades": ["ONDA"]} a legal and
     * useful thing to write in {@code gear.json}.</p>
     */
    public double defaultMagnitude() {
        return defaultMagnitude;
    }
}
