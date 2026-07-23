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
    THORNS,
    /**
     * On death: applies the {@code arg} mob effect to everything within {@code radio}, for
     * {@code duration} ticks, and deals {@code magnitud} damage.
     *
     * <p>The first ability that fires <b>after</b> the enemy is gone, which is the whole of what
     * makes it interesting: it is the only thing in the bestiary that punishes killing something
     * while standing next to it.</p>
     */
    ESTALLIDO,
    /**
     * On death: drops {@code magnitud} extra coins into the room.
     *
     * <p>For enemies that are worth killing rather than dangerous. Coins from a kill are otherwise
     * decided entirely by the tier tag, so an enemy cannot be made valuable without being made a
     * mini-boss.</p>
     */
    TESORO,
    /**
     * Once it has had a target for {@code ticks}: summons the {@code arg} spec, exactly as
     * {@link #SUMMON} does, and can do it again on the same interval.
     *
     * <p>A timer rather than a health threshold, which is what makes it a different question:
     * SUMMON asks "can you finish it", this asks "can you finish it <i>in time</i>". Only animated
     * enemies carry the countdown, so a CustomNPCs clone declaring it does nothing — the state has
     * to live on the entity, and a clone has nowhere to put it.</p>
     */
    ALERTA,
    /**
     * Enrages when struck from behind — the attacker inside {@code rearArc} degrees of directly
     * astern: faster by {@code speedMult}, harder by {@code damageMult}, once, with the enrage cue.
     *
     * <p>A flank punished, not a health phase, so it reads as "do not hit its back" rather than as
     * the boss cheating. The abdomen's own hit box is what makes the back reachable to strike in the
     * first place — this is the payoff for the queen carrying one.</p>
     */
    FLANK_RAGE
}
