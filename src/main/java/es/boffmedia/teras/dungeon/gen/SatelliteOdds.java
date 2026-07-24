package es.boffmedia.teras.dungeon.gen;

/**
 * Who visits the sala del sello, and how much they bring — the Acreedor/Orden arc's whole decision
 * layer, kept pure so it can be unit-tested (PISOS §63c/§63e, PRODUCCION §10.4).
 *
 * <p><b>The model is weighted, not gated.</b> Each condition nudges a base chance rather than
 * hard-locking a room, which is Isaac's own answer and the reason all eight conditions can be live
 * at once: under ANDed gates "whole floor flawless" would make the Orden unreachable in a party of
 * four, but as a modifier it is simply a large bonus a perfect floor earns.</p>
 *
 * <p><b>The two want opposite things.</b> La Orden is drawn to a clean floor, el Acreedor to a
 * bloody one — every purity signal that pulls her has a desperation twin that pulls him. So how the
 * party played the floor decides <i>who visits</i>, and the moral fork is a consequence of play
 * rather than a dice roll. A death is {@code +20} to him and forfeits her {@code +20}; selling
 * hearts feeds him and forfeits her bonus.</p>
 *
 * <p><b>Performance is floor-local, the ledger is run-long.</b> Damage and deaths reset every floor;
 * debts, deals and the Orden pact do not. The split is the same one {@code PlayerRunState} already
 * draws between what a floor costs you and what a run remembers.</p>
 */
public final class SatelliteOdds {
    private SatelliteOdds() {}

    /** Nothing is ever certain except a debt: every weighted chance stops short of a promise. */
    public static final int CAP = 95;

    public static final int ACREEDOR_BASE = 30;
    /** Once you are a client he rarely misses — the escalation the arc is built on. */
    public static final int ACREEDOR_DEALT_BEFORE = 45;
    public static final int ACREEDOR_SOLD_HEARTS = 15;
    public static final int ACREEDOR_DEATH = 20;
    public static final int ACREEDOR_LOW_HP = 20;
    public static final int ACREEDOR_BROKE = 15;

    /**
     * Generous once earned: the refusal is the real gate, and purity then decides how much she
     * gives. A branch nobody ever sees is a branch not worth authoring.
     */
    public static final int ORDEN_BASE = 45;
    public static final int ORDEN_NO_DEATHS = 20;
    public static final int ORDEN_BOSS_FLAWLESS = 25;
    public static final int ORDEN_FLOOR_FLAWLESS = 25;
    public static final int ORDEN_SOLD_NO_HEARTS = 10;

    /** Every purity signal at once — the score a flawless floor scores. */
    public static final int PURITY_MAX =
            ORDEN_NO_DEATHS + ORDEN_BOSS_FLAWLESS + ORDEN_FLOOR_FLAWLESS + ORDEN_SOLD_NO_HEARTS;
    public static final int PURITY_MAYOR = 40;

    /**
     * How cleanly the floor was played, and therefore how much la Orden gives. The same weights
     * that decide whether she appears decide what she offers — one system doing two jobs.
     */
    public enum Tier {
        /** Earned her, played roughly: one pick from three. */
        MENOR,
        /** A clean floor: one pick from four, restoring sold hearts among them. */
        MAYOR,
        /** Flawless: two picks, and the phoenix charm enters the pool. */
        PLENA;

        public int picks() {
            return this == PLENA ? 2 : 1;
        }

        public int options() {
            return this == MENOR ? 3 : 4;
        }
    }

    /**
     * The run-long half: what the party has done with him and what it still owes.
     *
     * @param soldHeartsThisRun any member has paid a heart price at any point — he knows a customer
     */
    public record Ledger(int deals, int refusals, boolean ordenCommitted, int deuda,
                         boolean soldHeartsThisRun) {

        public static Ledger empty() {
            return new Ledger(0, 0, false, 0, false);
        }
    }

    /**
     * The floor-local half, scored at the descent. Every field is one condition, already decided —
     * this record deliberately holds no thresholds, so "low HP" and "broke" can be defined where
     * the numbers live without this class knowing about health or wallets.
     *
     * @param walletBelowDealPrice the party cannot afford the cash price, so the loan is the only
     *                             door still open — which is exactly when a creditor should knock
     */
    public record FloorOutcome(boolean someoneDied, boolean bossFlawless, boolean floorFlawless,
                               boolean soldHearts, boolean lowPartyHp, boolean walletBelowDealPrice) {

        /** A floor nobody played — the state a run starts in, before there is anything to score. */
        public static FloorOutcome fresh() {
            return new FloorOutcome(false, false, false, false, false, false);
        }
    }

    /**
     * His chance to appear, 0–100.
     *
     * <p>An unpaid deuda forces him regardless of everything else, <b>including the Orden's
     * protection</b>: grace absolves the soul but does not settle accounts, so a party that
     * borrowed and then took the blessing is still visited by its creditor. That is the one
     * "abnormal condition" the pact does not cover.</p>
     */
    public static int acreedor(Ledger ledger, FloorOutcome floor) {
        if (ledger.deuda() > 0) {
            return 100;
        }
        if (ledger.ordenCommitted()) {
            return 0;
        }
        int chance = ACREEDOR_BASE;
        if (ledger.deals() >= 1) {
            chance += ACREEDOR_DEALT_BEFORE;
        }
        if (ledger.soldHeartsThisRun()) {
            chance += ACREEDOR_SOLD_HEARTS;
        }
        if (floor.someoneDied()) {
            chance += ACREEDOR_DEATH;
        }
        if (floor.lowPartyHp()) {
            chance += ACREEDOR_LOW_HP;
        }
        if (floor.walletBelowDealPrice()) {
            chance += ACREEDOR_BROKE;
        }
        return Math.min(CAP, chance);
    }

    /**
     * Her chance to appear, 0–100. Two hard requirements — the pact is not yet made, and he was
     * turned down at least once — then purity on top. Refusal credit is run-long: ignoring him
     * once keeps her eligible, and she is generous from there.
     */
    public static int orden(Ledger ledger, FloorOutcome floor) {
        if (ledger.ordenCommitted() || ledger.refusals() < 1) {
            return 0;
        }
        return Math.min(CAP, ORDEN_BASE + purity(floor));
    }

    /** The purity score of a floor, 0–{@link #PURITY_MAX}; also what tiers her gift. */
    public static int purity(FloorOutcome floor) {
        int score = 0;
        if (!floor.someoneDied()) {
            score += ORDEN_NO_DEATHS;
        }
        if (floor.bossFlawless()) {
            score += ORDEN_BOSS_FLAWLESS;
        }
        if (floor.floorFlawless()) {
            score += ORDEN_FLOOR_FLAWLESS;
        }
        if (!floor.soldHearts()) {
            score += ORDEN_SOLD_NO_HEARTS;
        }
        return score;
    }

    /** What a purity score buys at her pedestal. */
    public static Tier tierOf(int purity) {
        if (purity >= PURITY_MAX) {
            return Tier.PLENA;
        }
        return purity >= PURITY_MAYOR ? Tier.MAYOR : Tier.MENOR;
    }
}
