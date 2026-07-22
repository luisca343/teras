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
    /** The slower, harder-hitting melee profile. AW's own skin type, verified from its lang file. */
    AXE("axe"),
    /**
     * Offhand defence. Only possible because charms moved to a Curios slot and stopped occupying
     * the offhand — where Curios is absent they share it again, and a player has to choose.
     */
    SHIELD("shield"),
    HELMET("head"),
    CHESTPLATE("chest"),
    LEGGINGS("legs"),
    BOOTS("feet"),
    /**
     * A trinket whose whole point is the ability, not the stat line. Worn in a Curios slot where
     * Curios is installed and in the offhand where it is not.
     */
    CHARM("item"),
    /**
     * Held, and <b>used</b>: the flare, the charge, the flask, the hook.
     *
     * <p>The first kind whose point is a verb rather than a stat or a passive. It shares AW's
     * {@code item} skin type with {@link #CHARM}, which is the one place this enum's
     * "every kind has a distinct skin type" promise does not hold — so {@link #bySkinType} still
     * answers {@code CHARM} for it, and a gadget in {@code gear.json} has to name its {@code tipo}
     * explicitly rather than have it inferred. Stated here because a silently mis-inferred kind
     * would make a gadget a charm: worn, not used, and doing nothing.</p>
     */
    GADGET("item");

    private final String skinType;

    GearKind(String skinType) {
        this.skinType = skinType;
    }

    /** Default AW skin type name, resolved through {@code SkinTypes.byName}. */
    public String skinType() {
        return skinType;
    }

    /**
     * The kind whose Armourer's Workshop skin type is {@code skinType}, or null.
     *
     * <p>Every kind has a distinct skin type, so this is unambiguous — which lets {@code gear.json}
     * infer {@code tipo} from a {@code skinType} that is already there. A file that says
     * {@code "skinType": "shield"} has already said the piece is a shield, and making it say so
     * twice is a field to forget.</p>
     */
    public static GearKind bySkinType(String skinType) {
        if (skinType == null || skinType.isBlank()) {
            return null;
        }
        String wanted = skinType.trim().toLowerCase(java.util.Locale.ROOT);
        for (GearKind kind : values()) {
            if (kind.skinType.equals(wanted)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The registry path of the first-party item pieces of this kind are built on.
     *
     * <p>Here rather than only in {@code GearItems} so a test can hold the loot tables to it without
     * a running game: a loot entry that hands out the wrong item for its {@code gear_id} produces a
     * piece that migration silently rebuilds on pickup, which works but means the table was lying.</p>
     */
    public String itemPath() {
        return switch (this) {
            case SWORD -> "gear_espada";
            case AXE -> "gear_hacha";
            case SHIELD -> "gear_escudo";
            case HELMET -> "gear_yelmo";
            case CHESTPLATE -> "gear_coraza";
            case LEGGINGS -> "gear_grebas";
            case BOOTS -> "gear_botas";
            case CHARM -> "gear_amuleto";
            case GADGET -> "gear_artilugio";
        };
    }
}
