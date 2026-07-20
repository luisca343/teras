package es.boffmedia.teras.dungeon.gear;

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
 * @param kind      slot and skin shape
 * @param rarity    tooltip colour only; drop chance is the loot table's business
 * @param stats     attribute lines applied from the piece's slot
 * @param ability   the hook, {@link GearAbility#NINGUNA} for a pure stat stick
 * @param magnitude the ability's one number — a fraction for the percentage abilities, a flat
 *                  amount for {@link GearAbility#BOTIN}, seconds for the debuffs
 * @param skinId    Armourer's Workshop skin identifier ({@code ws:} / {@code db:}), blank for none
 * @param skinType  AW skin type name; blank means {@link GearKind#skinType()}
 * @param baseItem  vanilla item id this piece rides on (e.g. {@code minecraft:diamond_sword}).
 *                  Gear registers no items of its own: a piece is its base item tagged with the
 *                  {@code teras:gear_id} component, needs no model, texture or sprite, and its
 *                  look is the AW skin
 */
public record GearDef(
        String id,
        GearKind kind,
        Rarity rarity,
        List<Stat> stats,
        GearAbility ability,
        double magnitude,
        String skinId,
        String skinType,
        String baseItem) {

    /** Base-less convenience shape; {@link GearDefs} assigns the base item as it builds the map. */
    public GearDef(String id, GearKind kind, Rarity rarity, List<Stat> stats, GearAbility ability,
                   double magnitude, String skinId, String skinType) {
        this(id, kind, rarity, stats, ability, magnitude, skinId, skinType, "");
    }

    public enum Rarity { COMUN, RARO, EPICO }

    public record Stat(GearStat stat, double amount, GearOp operation) {}

    public String effectiveSkinType() {
        return skinType == null || skinType.isBlank() ? kind.skinType() : skinType;
    }

    public boolean hasSkin() {
        return skinId != null && !skinId.isBlank();
    }

    public GearDef withMagnitude(double value) {
        return new GearDef(id, kind, rarity, stats, ability, value, skinId, skinType, baseItem);
    }

    public GearDef withAbility(GearAbility value) {
        return new GearDef(id, kind, rarity, stats, value, magnitude, skinId, skinType, baseItem);
    }

    public GearDef withSkin(String skin, String type) {
        return new GearDef(id, kind, rarity, stats, ability, magnitude, skin, type, baseItem);
    }

    public GearDef withStats(List<Stat> replacement) {
        return new GearDef(id, kind, rarity, replacement, ability, magnitude, skinId, skinType, baseItem);
    }

    public GearDef withBaseItem(String item) {
        return new GearDef(id, kind, rarity, stats, ability, magnitude, skinId, skinType, item);
    }

    public boolean hasBaseItem() {
        return baseItem != null && !baseItem.isBlank();
    }
}
