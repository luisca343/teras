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
    FENIX_MENOR(1),
    /**
     * On hit: slows the target. {@code magnitud} is seconds, {@code nivel} the effect level.
     *
     * <p>The floor-1 answer to a weapon that cannot out-damage anything: it does not kill faster,
     * it decides who reaches whom.</p>
     */
    VISCOSO(3),
    /**
     * On hit: knocks the target back {@code magnitud} blocks of impulse.
     *
     * <p>Answers the archer on its own terms — a shooter that keeps getting shoved is a shooter
     * that never gets its cooldown back — and is the reason a cosh is worth carrying next to a
     * sword that hits harder.</p>
     */
    EMPUJE(0.9),
    /**
     * While worn: fall damage is reduced by {@code magnitud} as a fraction, 1.0 removing it.
     *
     * <p>Cave floors are built with ledges the archers stand on, so the cost of going up there is
     * the way down. This is the piece that changes which routes exist rather than which fights are
     * winnable.</p>
     */
    CAIDA_SUAVE(1.0),
    /**
     * While worn: enemies within {@code radio} blocks are outlined, refreshed every couple of
     * seconds.
     *
     * <p>Cuevas sits at light level 7 and Infestadas at 4, so on floor 1 the most valuable thing a
     * piece of gear can give is <b>seeing</b>. It also makes the climbers' eye-glow into something
     * a player can act on rather than something that startles them.</p>
     *
     * <p>{@code magnitud} is how long each outline lasts, in seconds. It has to outlive the gap
     * between refreshes or the outline blinks, which is why the default is comfortably above it.</p>
     */
    LINTERNA(3),

    // --- gadgets: the abilities you spend rather than carry ----------------------------------
    //
    // Everything above is passive — it fires because something else happened. These fire because a
    // player pressed use, which until now nothing in the dungeon economy did: the only verb was
    // "swing", so every reward was a number that made swinging better.
    //
    // They are ordinary abilities on an ordinary piece of gear, distinguished only by
    // {@link #isActive}. That is the cheap half of the design. The other half is that they are
    // gated by the vanilla item cooldown and nothing else — a deliberate refusal of per-floor
    // charges, which need storage, a refill hook, a display and a rule for outside a run, and buy
    // one thing (scarcity) that a long enough cooldown already buys. Vanilla also draws a cooldown
    // for free. If a gadget ever needs to be genuinely finite, that is the moment for charges.
    /**
     * On use: everything hostile within {@code radio} is outlined for {@code magnitud} seconds,
     * through walls. The panic button for a dark floor — the problem is never brightness, it is not
     * knowing what is in the room with you.
     */
    BENGALA(18, 8, 25),
    /**
     * On use: {@code magnitud} damage and a hard shove to everything hostile within {@code radio}.
     * What it buys is not damage, it is a second of space.
     */
    PETARDO(6, 4, 20),
    /**
     * On use: slows everything hostile near where your aim lands, for {@code magnitud} seconds.
     * Area denial you place, rather than area damage centred on yourself.
     */
    FRASCO(6, 4, 15),
    /**
     * On use: pulls you to the first solid block along your aim, within {@code radio}, with
     * {@code magnitud} as the strength of the pull. Answers an archer with movement instead of
     * damage.
     */
    GARFIO(1.15, 16, 8);

    /** Effect level, for the abilities that apply one. */
    public static final String P_LEVEL = "nivel";

    /** Reach in blocks, for the abilities that have one. */
    public static final String P_RADIUS = "radio";

    /** Seconds before an active ability can be used again. */
    public static final String P_COOLDOWN = "recarga";

    private final double defaultMagnitude;
    private final double defaultRadius;
    private final int defaultCooldownSeconds;

    GearAbility(double defaultMagnitude) {
        this(defaultMagnitude, 0, 0);
    }

    GearAbility(double defaultMagnitude, double defaultRadius, int defaultCooldownSeconds) {
        this.defaultMagnitude = defaultMagnitude;
        this.defaultRadius = defaultRadius;
        this.defaultCooldownSeconds = defaultCooldownSeconds;
    }

    /**
     * Whether this fires on use rather than on an event.
     *
     * <p>Read by the gadget item to decide whether a right-click does anything, and by the audit to
     * insist the two agree: an active ability on a piece that is not a {@link GearKind#GADGET} can
     * never be triggered, and a gadget carrying only passives is a right-click that does nothing.
     * Both are the shape of fault this codebase keeps producing — authored, plausible, inert.</p>
     */
    public boolean isActive() {
        return this == BENGALA || this == PETARDO || this == FRASCO || this == GARFIO;
    }

    /** Reach in blocks: an area for the ones that burst, a range for the ones that are aimed. */
    public double defaultRadius() {
        return defaultRadius;
    }

    /** Seconds before it can be used again — the whole of a gadget's scarcity. */
    public int defaultCooldownSeconds() {
        return defaultCooldownSeconds;
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
