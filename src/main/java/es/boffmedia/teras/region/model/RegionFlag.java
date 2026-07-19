package es.boffmedia.teras.region.model;

/**
 * Protection flags a region can carry. In the region's {@code flags} map the json key maps to
 * {@code false} = denied, {@code true} = explicitly allowed, absent = no opinion (allow).
 */
public enum RegionFlag {
    /** Placing blocks. */
    BUILD("build", true),
    /** Breaking blocks. */
    BREAK("break", true),
    /** Right-clicking blocks (doors, chests, buttons...) and entities (frames, armour stands). */
    INTERACT("interact", true),
    /** Player-vs-player damage. */
    PVP("pvp", false),
    /** Explosions destroying blocks. */
    EXPLOSIONS("explosions", false);

    private final String key;
    private final boolean ownershipGated;

    RegionFlag(String key, boolean ownershipGated) {
        this.key = key;
        this.ownershipGated = ownershipGated;
    }

    public String key() {
        return key;
    }

    /**
     * Whether a plot's implicit deny-unless-owner rule covers this flag. Only the building family
     * is gated: inside a plot you do not own, you cannot place, break or interact, with no flag
     * set anywhere. {@link #PVP} and {@link #EXPLOSIONS} stay purely flag-driven — they are not
     * about who owns the ground, and {@code EXPLOSIONS} is resolved with no player at all, so
     * gating it on ownership would block every explosion in every plot.
     */
    public boolean isOwnershipGated() {
        return ownershipGated;
    }

    /** The flag whose json key is {@code key}, or {@code null} if none matches. */
    public static RegionFlag fromKey(String key) {
        for (RegionFlag flag : values()) {
            if (flag.key.equals(key)) return flag;
        }
        return null;
    }
}
