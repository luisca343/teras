package es.boffmedia.teras.dungeon.gear;

/**
 * What a piece of gear does beyond its stat line. The numbers live in {@link GearDef} so they stay
 * tunable from {@code gear.json}; this enum is only the choice of hook.
 *
 * <p>Two of these are run-scoped by nature — {@link #BOTIN} credits the party purse and
 * {@link #FENIX_MENOR} writes {@code PlayerRunState} — and no-op outside a dungeon. The rest work
 * wherever the gear is carried: it is kept after a run, and a reward that stops working when you
 * leave is a reward players learn not to chase.</p>
 */
public enum GearAbility {
    NINGUNA,
    /** On hit: heals the attacker for a fraction of the damage dealt. */
    VAMPIRISMO,
    /** On hit: sets the target on fire. */
    QUEMAZON,
    /** On hit: applies wither. */
    DESGARRO,
    /** On kill: damages and knocks back everything around the corpse. */
    ONDA,
    /** On kill: extra dungeon coins into the shared purse. */
    BOTIN,
    /** While worn: reflects a fraction of incoming melee damage back at the attacker. */
    ESPINAS,
    /** While worn: grants the one-shot revive charm once per floor entry. */
    FENIX_MENOR
}
