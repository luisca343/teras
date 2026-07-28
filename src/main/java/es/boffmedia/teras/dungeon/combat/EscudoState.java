package es.boffmedia.teras.dungeon.combat;

/**
 * The rule for handing out shield hearts, and the only part of escudo that is arithmetic.
 *
 * <p>Split from {@link Escudo} for the reason {@link DodgeState} is split from {@code Dodge}: the
 * decision is testable and the plumbing is not. {@code Escudo} has to name {@code ServerPlayer} to
 * read and write absorption, and a class that names Minecraft cannot even be <i>loaded</i> by the unit
 * JVM — so a rule left inside it is a rule that quietly has no tests.</p>
 *
 * <p>Pure — no Minecraft.</p>
 */
public final class EscudoState {
    private EscudoState() {}

    /** Half-hearts per point of escudo, matching {@code contenedores}. */
    public static final float HEALTH_PER_POINT = 2f;

    /**
     * How much of a sheet's escudo is still owed, given what has already been paid out.
     *
     * <p>{@link Stat#ESCUDO} is a <b>grant</b> and not a capacity: equipping something that carries two
     * escudo hands you two hearts once. Paying only the excess over what a run has already paid is what
     * stops taking the piece off and putting it back from refilling them — which would turn any escudo
     * item into an infinite one, and "never healed back" is the whole character of the stat.</p>
     *
     * <p>Never negative, so losing the source does not claw back hearts already earned.</p>
     */
    public static double owed(double sheetEscudo, double alreadyGranted) {
        return Math.max(0, sheetEscudo - Math.max(0, alreadyGranted));
    }
}
