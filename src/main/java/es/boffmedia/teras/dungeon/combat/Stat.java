package es.boffmedia.teras.dungeon.combat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The combat sheet: every axis a player or an enemy has inside a run.
 *
 * <h2>Why these and not Minecraft's</h2>
 *
 * <p>Vanilla offers six attributes — damage, attack speed, armour, toughness, movement speed, max
 * health — and a roguelike cannot be built on them, because none of them is a <i>dial a run can be
 * about</i>. There is no luck to push, no cooldown rate to invest in, no poise to break. Every item
 * written against that vocabulary can only be a bigger number.</p>
 *
 * <p>These thirteen replace it wholesale inside a run (ROGUELIKE §4.1). Vanilla attributes still exist
 * on the entity and still move it around the world; they no longer decide a single point of damage.
 * (Twelve of the thirteen are on the panel — {@code aplomo} is read by the engine and not shown,
 * because poise is already drawn where it matters, on the enemy that breaks.)</p>
 *
 * <p>A drafted {@code codicia} — pickup pull and drop rate — was cut after shipping: the purse is
 * shared ({@link es.boffmedia.teras.dungeon.run.DungeonWallet}), so a personal dial on how much
 * money you pull is a stat about a resource you do not personally own.</p>
 *
 * <h2>The rule every stat has to pass</h2>
 *
 * <p><b>Two stats may not answer the same question.</b> {@code alcance} and {@code velocidad} both
 * change how often you connect, but one is about where you can stand and the other about how fast
 * the swing resolves, so both survive. A drafted {@code evasion} was cut for failing it: dodging is
 * a <i>verb</i> the player performs, and a stat that dodges for you takes the decision away from the
 * one input the whole loop is built around.</p>
 *
 * <h2>Additive or multiplicative by nature</h2>
 *
 * <p>Half of these answer "how much" and start at zero ({@link #DANO}, {@link #CRITICO},
 * {@link #SUERTE}); the other half answer "how fast" or "how many times" and start at one
 * ({@link #CADENCIA}, {@link #VELOCIDAD}, {@link #ENFRIAMIENTO}). {@link #base} carries that
 * difference so no caller has to remember it.</p>
 *
 * <p>Pure — no Minecraft — so the sheet and its arithmetic are testable without a server, the same
 * discipline {@code Afliccion} and {@code SatelliteOdds} already follow.</p>
 */
public enum Stat {

    // --- ataque -------------------------------------------------------------------------------

    /** Damage a connecting hit starts from, before crit and before the target's mitigation. */
    DANO("dano", 0, 0, 10_000),

    /**
     * Swing rate, as a multiplier on the weapon's own cadence.
     *
     * <p><b>Read by the panel and by nothing else yet.</b> The intent is that it also sets the combo
     * window — a faster weapon has to allow a faster chain, or its third hit lands after the window
     * shuts — but {@link SwingState#COMBO_WINDOW_TICKS} is still a constant, so this says nothing about
     * how the chain behaves today. Stated because the reverse claim stood here as fact.</p>
     *
     * <p>Named for what it is. It was {@code velocidad} until a playtest read that on the panel and
     * reasonably took it for movement speed — which now exists separately as {@link #VELOCIDAD}, and
     * would have been a permanent source of the same confusion sitting one row apart.</p>
     */
    CADENCIA("cadencia", 1, 0.1, 5),

    /** Chance for a hit to crit, 0 to 1. */
    CRITICO("critico", 0, 0, 1),

    /**
     * What a crit multiplies by. Split from {@link #CRITICO} because "crits more often" and "crits
     * harder" are two different builds, and a catalogue that cannot express the second has no answer
     * for a slow heavy weapon.
     */
    CONTUNDENCIA("contundencia", 1.5, 1, 10),

    /** Fraction of the target's armour ignored, 0 to 1. The counter to a floor's armour curve. */
    PENETRACION("penetracion", 0, 0, 1),

    /** Melee reach, or projectile range, in blocks added to the weapon's own. */
    ALCANCE("alcance", 0, -2, 32),

    // --- defensa ------------------------------------------------------------------------------

    /** Mitigation input. Never a flat subtraction — see {@link DamageMath#mitigation}. */
    ARMADURA("armadura", 0, 0, 10_000),

    /**
     * Maximum hearts, run-permanent. Integral: half a heart container is not a thing a player can
     * be told about, and the devil deal has always traded in whole ones.
     */
    CONTENEDORES("contenedores", 6, 1, 20),

    /** Temporary hearts that absorb first and are never healed back. Integral, for the same reason. */
    ESCUDO("escudo", 0, 0, 20),

    /**
     * Poise: how much stagger is absorbed before the guard breaks. What makes a heavy attack worth
     * committing to — without it a rebuilt melee loop is vanilla with different numbers.
     *
     * <p>Both sides carry it: an enemy's guard is what a heavy opens, and a player's is what an
     * unanswered swarm breaks. Zero means "not authored", and {@code CombatSheets.aplomoFor} falls back
     * to a health-derived value for anything that names none.</p>
     */
    APLOMO("aplomo", 0, 0, 1_000),

    // --- utilidad -----------------------------------------------------------------------------

    /**
     * How fast you move, as a multiple of a walking player. 1 is a player on foot; a wolf is
     * roughly 3.
     *
     * <p>Separate from {@link #CADENCIA} because they answer different questions — how often you
     * connect versus whether you can reach or leave — and because a floor built for ParCool
     * traversal wants a dial for the second one.</p>
     */
    VELOCIDAD("velocidad", 1, 0, 5),

    /** Cooldown rate for gadgets and the dodge. 2 means cooldowns tick twice as fast. */
    ENFRIAMIENTO("enfriamiento", 1, 0.1, 5),

    /**
     * Pool quality and roll weighting. Signed, because a curse that makes your luck <i>worse</i> is
     * a real thing to sell, and unlucky is a state a player can be in rather than an absence.
     *
     * <p>The only other stat allowed below zero is {@link #ALCANCE}, and for a different reason: it
     * is an offset onto the weapon's own reach, so a negative there shortens a swing rather than
     * describing a negative quantity. Everything else reads as nonsense below zero — negative
     * armour, negative hearts, negative cooldown rate — and is floored at it.</p>
     */
    SUERTE("suerte", 0, -10, 10);

    private final String key;
    private final double base;
    private final double min;
    private final double max;

    Stat(String key, double base, double min, double max) {
        this.key = key;
        this.base = base;
        this.min = min;
        this.max = max;
    }

    /** Config and wire key. */
    public String key() {
        return key;
    }

    /** What this stat reads as before anything modifies it. */
    public double base() {
        return base;
    }

    public double min() {
        return min;
    }

    /**
     * The ceiling.
     *
     * <p>Every stat has one, and that is a design position rather than defensive programming: ten
     * floors of ten pools is a great deal of stacking, and unbounded accumulation is precisely what
     * makes a late Isaac run stop being readable. A capped stat still lets a build be finished —
     * it just makes the next copy of the same reliquia a bad pick, which is the answer we want
     * anyway.</p>
     */
    public double max() {
        return max;
    }

    /** Whether fractions are meaningful, or the value is a count the player is shown. */
    public boolean integral() {
        return this == CONTENEDORES || this == ESCUDO;
    }

    public double clamp(double value) {
        double bounded = Math.min(max, Math.max(min, value));
        return integral() ? Math.rint(bounded) : bounded;
    }

    private static final Map<String, Stat> BY_KEY = new LinkedHashMap<>();

    static {
        for (Stat stat : values()) {
            BY_KEY.put(stat.key, stat);
        }
    }

    /** By key, or null — so a caller can use it as the test. */
    public static Stat byKey(String key) {
        return key == null ? null : BY_KEY.get(key.toLowerCase(Locale.ROOT));
    }

    public static boolean exists(String key) {
        return byKey(key) != null;
    }

    /** Every key, for an error message that has to list what it would have accepted. */
    public static String keys() {
        return String.join(", ", BY_KEY.keySet());
    }

    /**
     * The keys in {@code names} that are not stats, as lines to log.
     *
     * <p>Pure and taking the collection in, the shape {@code Afliccion.problems} established: a config
     * naming something that does not exist should be a line at boot rather than a silence. The live
     * instance of this is {@code GearDefs}, which warns on an unknown key in {@code gear.json}'s
     * {@code stats} block — and with nine first-party axes now authorable there, a typo that silently
     * did nothing is a real thing to catch.</p>
     */
    public static List<String> problems(Collection<String> names) {
        return names.stream()
                .filter(name -> !exists(name))
                .map(name -> "unknown stat '" + name + "'. Known: " + keys())
                .toList();
    }
}
