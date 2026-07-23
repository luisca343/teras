package es.boffmedia.teras.dungeon.gear;

import java.util.ArrayList;
import java.util.List;

/**
 * One piece of dungeon gear, in full: what it is, what it adds, what it does, and what it looks
 * like. Definitions are code-authored ({@link GearDefs}) and number-overridable from
 * {@code gear.json}, the same defaults-in-code shape the spawn tables and enemy abilities use.
 *
 * <p>Deliberately free of Minecraft types — {@link GearVanilla} binds it to attributes and slots —
 * so the catalog is data a unit test can read.</p>
 *
 * @param id        registry path, e.g. {@code espada_abisal}
 * @param kind      slot, item and skin shape. The item a piece is built on follows from this
 *                  ({@link GearKind#itemPath()}); a piece no longer rides a vanilla base
 * @param rarity    tooltip colour only; drop chance is the loot table's business
 * @param stats     attribute lines applied from the piece's slot
 * @param abilities what it does beyond its stat line. A list, because one ability per piece was a
 *                  limit of the old shape rather than a design decision, and empty for a pure stat
 *                  stick
 * @param skinId    Armourer's Workshop skin identifier ({@code ws:} / {@code db:}), blank for none
 * @param skinType  AW skin type name; blank means {@link GearKind#skinType()}
 * @param nombre    what the item is called, when the name should not come from a lang file. Blank
 *                  is the normal case: the stamp then uses {@code item.teras.<id>} with a name
 *                  derived from the id as its fallback, so a piece defined only in {@code gear.json}
 *                  is never nameless and a lang entry still wins if one exists
 * @param tint      ARGB colour for a worn armour piece with no AW skin — the grey layer is rendered
 *                  multiplied by it, so the piece reads apart on the body. {@code 0} means "unset":
 *                  the renderer falls back to a colour keyed on the piece's rarity. Item slots that
 *                  are not worn armour ignore it
 */
public record GearDef(
        String id,
        GearKind kind,
        Rarity rarity,
        List<Stat> stats,
        List<AbilityDef> abilities,
        String skinId,
        String skinType,
        String nombre,
        int tint) {

    /** Without an explicit name, which is what nearly every piece wants. */
    public GearDef(String id, GearKind kind, Rarity rarity, List<Stat> stats,
                   List<AbilityDef> abilities, String skinId, String skinType) {
        this(id, kind, rarity, stats, abilities, skinId, skinType, "", 0);
    }

    /** With a name but no explicit tint — the rarity colour stands in. */
    public GearDef(String id, GearKind kind, Rarity rarity, List<Stat> stats,
                   List<AbilityDef> abilities, String skinId, String skinType, String nombre) {
        this(id, kind, rarity, stats, abilities, skinId, skinType, nombre, 0);
    }

    public GearDef {
        nombre = nombre == null ? "" : nombre.trim();
        stats = stats == null ? List.of() : List.copyOf(stats);
        // Empty rather than a NINGUNA entry: "carries no ability" should read as an empty list at
        // every call site, not as a list of one thing that does nothing.
        abilities = abilities == null ? List.of()
                : abilities.stream().filter(a -> a != null && !a.isNone()).toList();
    }

    /** The one-ability shape every definition had before abilities became a list. */
    public GearDef(String id, GearKind kind, Rarity rarity, List<Stat> stats, GearAbility ability,
                   double magnitude, String skinId, String skinType) {
        this(id, kind, rarity, stats,
                ability == null || ability == GearAbility.NINGUNA
                        ? List.of() : List.of(AbilityDef.of(ability, magnitude)),
                skinId, skinType, "");
    }

    public enum Rarity { COMUN, RARO, EPICO }

    public record Stat(GearStat stat, double amount, GearOp operation) {}

    public String effectiveSkinType() {
        return skinType == null || skinType.isBlank() ? kind.skinType() : skinType;
    }

    public boolean hasName() {
        return !nombre.isEmpty();
    }

    /**
     * The name to show when no lang entry and no {@code nombre} exist: the id as words.
     *
     * <p>Delegates rather than implements — an elite's nameplate and a boss bar need exactly the
     * same thing, and the second copy of this was written within an hour of the first.</p>
     */
    public String derivedName() {
        return es.boffmedia.teras.dungeon.model.Names.fromId(id);
    }

    public GearDef withNombre(String value) {
        return new GearDef(id, kind, rarity, stats, abilities, skinId, skinType, value, tint);
    }

    public GearDef withTint(int value) {
        return new GearDef(id, kind, rarity, stats, abilities, skinId, skinType, nombre, value);
    }

    public boolean hasSkin() {
        return skinId != null && !skinId.isBlank();
    }

    /** Whether this piece carries {@code ability} at all. */
    public boolean has(GearAbility ability) {
        return abilities.stream().anyMatch(a -> a.ability() == ability);
    }

    /** The piece's definition of {@code ability}, or null when it does not carry it. */
    public AbilityDef ability(GearAbility ability) {
        return abilities.stream().filter(a -> a.ability() == ability).findFirst().orElse(null);
    }

    public GearDef withStats(List<Stat> replacement) {
        return new GearDef(id, kind, rarity, replacement, abilities, skinId, skinType, nombre, tint);
    }

    public GearDef withAbilities(List<AbilityDef> replacement) {
        return new GearDef(id, kind, rarity, stats, replacement, skinId, skinType, nombre, tint);
    }

    public GearDef withSkin(String skin, String type) {
        return new GearDef(id, kind, rarity, stats, abilities, skin, type, nombre, tint);
    }

    /**
     * Replaces the primary number of the piece's first ability — what {@code gear.json}'s legacy
     * {@code magnitud} key means, kept so an existing config file still tunes what it always tuned.
     */
    public GearDef withMagnitude(double value) {
        if (abilities.isEmpty()) {
            return this;
        }
        List<AbilityDef> replaced = new ArrayList<>(abilities);
        replaced.set(0, AbilityDef.of(replaced.get(0).ability(), value));
        return withAbilities(replaced);
    }

    /** Replaces the piece's single ability, keeping its numbers. Legacy {@code habilidad} key. */
    public GearDef withAbility(GearAbility value) {
        double magnitude = abilities.isEmpty() ? 0 : abilities.get(0).magnitude(0);
        return withAbilities(value == null || value == GearAbility.NINGUNA
                ? List.of() : List.of(AbilityDef.of(value, magnitude)));
    }
}
