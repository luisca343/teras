package es.boffmedia.teras.dungeon.entity;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What an enemy chooses to do — its AI goals. Composable rather than a single archetype label,
 * because a boss is often melee <i>and</i> a caster, and "archer" as a fixed type cannot say that.
 *
 * <p>Distinct from {@code dungeon/ability}, which is triggered effects (what happens when it hits or
 * is hurt). Behaviours are goals; abilities are reactions. The two systems sit beside each other and
 * neither replaces the other.</p>
 */
public enum Behaviour {
    /** Close and strike. */
    MELEE,
    /** Keep distance and shoot — the archer. */
    RANGED,
    /** A telegraphed area hit at the target's feet, answered by moving rather than out-healing. */
    VOLLEY,
    /** Fires webbing that lands as terrain, slowing whoever follows through it. */
    WEB_SHOT,
    /** Short teleport away when something reaches it — what stops a caster being a free target. */
    BLINK,
    /** Pounces at the target. The spider's signature. */
    LEAP;

    public boolean isRanged() {
        return this == RANGED || this == VOLLEY || this == WEB_SHOT;
    }

    /** Parses a config list, skipping names that no longer exist rather than failing the enemy. */
    public static Set<Behaviour> parse(Iterable<String> names) {
        Set<Behaviour> set = EnumSet.noneOf(Behaviour.class);
        if (names == null) {
            return set;
        }
        for (String name : names) {
            try {
                set.add(valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                // Deliberately quiet at parse time; the caller logs with the enemy's id for context.
            }
        }
        return set;
    }
}
