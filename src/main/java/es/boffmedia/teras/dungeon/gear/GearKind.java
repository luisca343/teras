package es.boffmedia.teras.dungeon.gear;

/**
 * The shapes dungeon gear comes in. Each kind fixes where the piece is worn, which Armourer's
 * Workshop skin type its visual has to be, and whether it has to register as armour — the vanilla
 * bindings for all three live in {@link GearVanilla}, so this stays plain Java.
 */
public enum GearKind {
    // Skin type names are AW's registered ones, read out of SkinTypes' static init: bare and
    // lowercase (byName prepends "armourers:"). "item_sword"/"armor_chest"-style names look
    // plausible but resolve to UNKNOWN, and the skin silently never attaches.
    SWORD("sword"),
    HELMET("head"),
    CHESTPLATE("chest"),
    LEGGINGS("legs"),
    BOOTS("feet"),
    /** Held in the offhand; a trinket whose whole point is the ability, not the stat line. */
    CHARM("item");

    private final String skinType;

    GearKind(String skinType) {
        this.skinType = skinType;
    }

    /** Default AW skin type name, resolved through {@code SkinTypes.byName}. */
    public String skinType() {
        return skinType;
    }
}
