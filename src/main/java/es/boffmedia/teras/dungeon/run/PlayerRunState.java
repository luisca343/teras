package es.boffmedia.teras.dungeon.run;

/**
 * What one member carries through a run that the party does not share. Money is common
 * ({@link DungeonWallet}); bodies are personal — a devil deal takes <i>your</i> hearts, a phoenix
 * charm saves <i>you</i>, and deaths are counted per player for the results report.
 */
public final class PlayerRunState {

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
