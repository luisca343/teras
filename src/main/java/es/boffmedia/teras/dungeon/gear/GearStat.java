package es.boffmedia.teras.dungeon.gear;

import java.util.Locale;

/**
 * The attributes gear is allowed to move. An enum rather than the vanilla {@code Holder<Attribute>}
 * so the catalog stays plain Java and can be unit-tested; {@link GearVanilla} does the binding.
 * The name doubles as the {@code gear.json} key, so what an admin types is what is listed here.
 *
 * <h2>Two kinds of line, and why they share one enum</h2>
 *
 * <p>The first six are <b>vanilla-backed</b>: each binds to a real attribute, so a piece carrying one
 * moves the entity itself and the line appears wherever Minecraft shows attributes. The rest are
 * <b>first-party</b> axes from the rebuilt combat sheet ({@code combat.Stat}) — crit, penetration,
 * poise, luck — which have no vanilla attribute to bind to and are read straight off the worn stacks
 * by {@code CombatSheets} instead.</p>
 *
 * <p>They share one enum because they share one authoring surface: {@code gear.json}'s {@code stats}
 * block is keyed by {@link #key()}, so adding a constant here is the whole of what it takes to make
 * an axis tunable, and a piece's stat list stays one list rather than two that can disagree. What
 * separates them is {@link #vanilla()}, which every applier must respect —
 * {@link GearVanilla#attribute} has no answer for a first-party stat, and asking it for one is how
 * you get a {@code NullPointerException} at stamp time.</p>
 */
public enum GearStat {

    // --- vanilla-backed: these move the entity's own attributes ---------------------------------

    ATTACK_DAMAGE(true),
    ATTACK_SPEED(true),
    ARMOR(true),
    ARMOR_TOUGHNESS(true),
    MOVEMENT_SPEED(true),
    MAX_HEALTH(true),

    // --- first-party: the combat sheet's own axes (ROGUELIKE §4.1) ------------------------------
    // Named exactly as combat.Stat's keys, so one word means one thing across config, tooltip and
    // panel. GearSheet does the mapping; nothing in this package knows that class exists.

    /** Crit chance, as a fraction. Without a source for this, nothing in a run can ever crit. */
    CRITICO(false),

    /** What a crit multiplies by. */
    CONTUNDENCIA(false),

    /** Fraction of the target's armour ignored — the counter to a deep floor's armour curve. */
    PENETRACION(false),

    /** Blocks added to a melee swing's reach, or to a shot's range. */
    ALCANCE(false),

    /** Cooldown rate for the esquiva and for gadgets. A rate: 2 means cooldowns tick twice as fast. */
    ENFRIAMIENTO(false),

    /** Poise: how much stagger the wearer absorbs before their guard breaks. */
    APLOMO(false),

    /** Pool quality and roll weighting. The one line that may legitimately be negative. */
    SUERTE(false),

    /** Temporary hearts that absorb first and are never healed back. */
    ESCUDO(false);

    private final boolean vanilla;

    GearStat(boolean vanilla) {
        this.vanilla = vanilla;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether this line binds to a vanilla attribute.
     *
     * <p>False means {@link GearVanilla#attribute} returns null for it and every applier that builds
     * attribute modifiers has to skip the line — the combat sheet picks it up instead.</p>
     */
    public boolean vanilla() {
        return vanilla;
    }

    /** Null for an unknown key, so a bad {@code gear.json} entry is skipped rather than fatal. */
    public static GearStat byKey(String key) {
        for (GearStat stat : values()) {
            if (stat.key().equalsIgnoreCase(key)) {
                return stat;
            }
        }
        return null;
    }
}
