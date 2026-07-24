package es.boffmedia.teras.dungeon.run;

/**
 * What one member carries through a run that the party does not share. Money is common
 * ({@link DungeonWallet}); bodies are personal — a devil deal takes <i>your</i> hearts, a phoenix
 * charm saves <i>you</i>, and deaths are counted per player for the results report.
 */
public final class PlayerRunState {

    /** Afflictions this member took on their own behalf, rather than the party's. */
    private final AfflictionSet afflictions = new AfflictionSet();

    public AfflictionSet afflictions() {
        return afflictions;
    }

    private int hpDebt;
    private boolean phoenix;
    private int deaths;

    /** Half-hearts of maximum health sold to devil deals, held so respawns can re-apply them. */
    public int hpDebt() {
        return hpDebt;
    }

    public void addHpDebt(int halfHearts) {
        if (halfHearts > 0) {
            hpDebt += halfHearts;
        }
    }

    /**
     * Gives back every heart sold to a devil deal — la Orden's <i>restitución</i>, and the only
     * thing in the run that undoes one.
     *
     * <p>Clears the ledger only. The attribute has to be rewritten through
     * {@code Afflictions.apply}, which recomputes the total from this <b>and</b> from any curse-room
     * Pulso débil: touching the modifier directly here would hand the player back health an
     * affliction is still supposed to be taking.</p>
     */
    public void forgiveHpDebt() {
        hpDebt = 0;
    }

    public boolean hasPhoenix() {
        return phoenix;
    }

    public void grantPhoenix() {
        phoenix = true;
    }

    /** Spends the charm; false when there was none, and the death stands. */
    public boolean consumePhoenix() {
        if (!phoenix) {
            return false;
        }
        phoenix = false;
        return true;
    }

    public int deaths() {
        return deaths;
    }

    public void countDeath() {
        deaths++;
    }
}
