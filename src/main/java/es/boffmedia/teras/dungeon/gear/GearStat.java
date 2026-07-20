package es.boffmedia.teras.dungeon.gear;

import java.util.Locale;

/**
 * The attributes gear is allowed to move. An enum rather than the vanilla {@code Holder<Attribute>}
 * so the catalog stays plain Java and can be unit-tested; {@link GearVanilla} does the binding.
 * The name doubles as the {@code gear.json} key, so what an admin types is what is listed here.
 */
public enum GearStat {
    ATTACK_DAMAGE,
    ATTACK_SPEED,
    ARMOR,
    ARMOR_TOUGHNESS,
    MOVEMENT_SPEED,
    MAX_HEALTH;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
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
