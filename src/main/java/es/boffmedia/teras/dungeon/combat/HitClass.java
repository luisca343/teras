package es.boffmedia.teras.dungeon.combat;

/**
 * What kind of damage a hit is, which decides how much of it {@link CombatEngine} owns.
 *
 * <h2>Why "has an attacker" was the wrong test</h2>
 *
 * <p>The engine's first shape asked one question — is there a living attacker? — and treated every
 * yes as a melee swing, replacing the incoming amount with {@link DamageMath}'s. That is wrong for
 * seven damage sites the dungeon already ships: an enemy's bolt, a volley, an ability's splash, both
 * thorns paths, a gadget's charge and a hammer's on-kill shockwave all carry a living attacker and
 * none of them is a swing. Every one of them lost the amount it was authored with and gained the
 * attacker's full {@code daño} instead — a bolt hitting for the shooter's sword damage — and the two
 * with a <i>player</i> attacker were worse than a wrong number: they ran the verb block, so a
 * passive proc consumed an armed heavy and advanced the light chain.</p>
 *
 * <p>So the question is asked in three parts instead, and the answer decides between replacing an
 * amount, scaling one, and leaving it entirely alone.</p>
 *
 * <p>Pure — no Minecraft. The three facts arrive as booleans so the truth table is testable, which
 * matters more here than anywhere else in the package: the arithmetic was always tested and it was
 * this <i>classification</i> that was wrong.</p>
 */
public enum HitClass {

    /**
     * A swing: the attacker's own body, landing directly, under an attack damage type.
     *
     * <p>The only class the engine fully owns — the incoming amount is discarded and
     * {@link DamageMath} answers instead, the attacker's verb decides what it was worth, and poise
     * is chipped.</p>
     */
    MELEE,

    /**
     * Something an attacker caused without swinging: a projectile, an explosion, a reflected hit, a
     * splash, an authored proc.
     *
     * <p>The amount is <b>kept and scaled</b> through the mitigation curve rather than replaced, so
     * the number the content author wrote still decides how hard it hits and armour and penetration
     * still matter. No verb runs and no poise is chipped: a proc the player did not aim must not
     * spend the heavy they wound up, and a bolt is not a stagger tool unless something says it
     * is.</p>
     */
    ATTRIBUTED,

    /**
     * Nobody hit you: fall damage, lava, the void — and every <i>price</i> the dungeon charges.
     *
     * <p>Left completely untouched, and that exemption is load-bearing rather than incidental. The
     * sacrifice plate's bite, the curse room's toll, a chest's spikes and a long drop are all tuned
     * in coins-and-hearts terms against the health lockdown ({@code RunEngine.chargeToll} is the one
     * funnel and uses a source with no entity at all). Running a price through an armour curve would
     * mean a well-equipped party paid less for the same trade, which inverts what a cost is.</p>
     */
    UNATTRIBUTED;

    /** Whether the engine replaces the incoming amount outright. */
    public boolean replacesAmount() {
        return this == MELEE;
    }

    /** Whether the attacker's verb — light chain, armed heavy, poise chip — applies. */
    public boolean carriesVerb() {
        return this == MELEE;
    }

    /** Whether the engine touches the amount at all. */
    public boolean mitigated() {
        return this != UNATTRIBUTED;
    }

    /**
     * Classifies a hit.
     *
     * @param livingAttacker  the source names a living entity as responsible
     * @param directIsAttacker the thing that actually made contact <i>is</i> that entity, rather
     *                         than a projectile or an explosion it produced
     * @param meleeType       the damage type is one of vanilla's attack types — this is the
     *                        load-bearing test, because {@code thorns} and {@code explosion} both
     *                        report the attacker as the direct entity too and are not swings
     */
    public static HitClass of(boolean livingAttacker, boolean directIsAttacker, boolean meleeType) {
        if (!livingAttacker) {
            return UNATTRIBUTED;
        }
        return directIsAttacker && meleeType ? MELEE : ATTRIBUTED;
    }
}
