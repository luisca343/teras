package es.boffmedia.teras.region.model;

/**
 * Protection flags a region can carry. In the region's {@code flags} map the json key maps to
 * {@code false} = denied, {@code true} = explicitly allowed, absent = no opinion (allow).
 */
public enum RegionFlag {
    /** Placing blocks. */
    BUILD("build"),
    /** Breaking blocks. */
    BREAK("break"),
    /** Right-clicking blocks (doors, chests, buttons...). */
    INTERACT("interact"),
    /** Player-vs-player damage. */
    PVP("pvp"),
    /** Explosions destroying blocks. */
    EXPLOSIONS("explosions");

    private final String key;

    RegionFlag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The flag whose json key is {@code key}, or {@code null} if none matches. */
    public static RegionFlag fromKey(String key) {
        for (RegionFlag flag : values()) {
            if (flag.key.equals(key)) return flag;
        }
        return null;
    }
}
