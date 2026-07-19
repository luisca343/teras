package es.boffmedia.teras.dungeon.ability;

/**
 * What an enemy can do beyond walking up and swinging. Pure data: which of these an enemy carries
 * is authored in {@code enemies.json}, and both enemy paths — CustomNPCs clones through
 * {@code CnpcAbilityEvents} and {@code teras:dungeon_enemy} through its own hooks — run the same
 * set, so a server without CustomNPCs still fights something with tricks.
 *
 * <p>Deliberately not a replacement for CustomNPCs' own scripting: an admin who installs a script
 * engine can still attach JS in the NPC editor, and {@code ICustomNpc.trigger} reaches it. This is
 * the layer that works with nothing installed.</p>
 */
public enum AbilityKind {
    /**
     * Melee hits splash onto everyone else within {@code radius}, for {@code fraction} of the
     * damage. Punishes a party bunching up on one enemy.
     */
    CLEAVE,
    /**
     * Once below {@code healthPct} of max health: faster, harder, with a cue and particles so the
     * change is telegraphed rather than silently unfair. Fires once per enemy.
     */
    ENRAGE,
    /**
     * Once below {@code healthPct}: spawns {@code count} adds named by the spec in {@code arg}.
     * The adds enter the room's kill ledger, so the room does not clear while they are alive.
     */
    SUMMON,
    /** Applies the {@code arg} mob effect to whoever it hits, for {@code duration} ticks. */
    ON_HIT,
    /** Reflects {@code fraction} of incoming melee damage back at the attacker. */
    THORNS
}
