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
    private int containersLost;

    /**
     * Heart containers this run has taken from the player by killing them.
     *
     * <p>Separate from {@link #hpDebt} even though both end up as the same attribute modifier: a
     * devil deal's hearts are <b>sold</b> and la Orden's restitución gives them back, while these
     * were <b>lost</b> and she does not undo a death. Folding them into one counter would make
     * restitución quietly refund the party's mistakes as well as its bargains.</p>
     */
    public int containersLost() {
        return containersLost;
    }

    public void loseContainers(int containers) {
        if (containers > 0) {
            containersLost += containers;
        }
    }

    /** Gives lost containers back, for whatever eventually pays for them. Never called by a death. */
    public void restoreContainers(int containers) {
        if (containers > 0) {
            containersLost = Math.max(0, containersLost - containers);
        }
    }

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
