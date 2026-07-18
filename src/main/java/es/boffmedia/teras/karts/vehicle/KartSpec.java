package es.boffmedia.teras.karts.vehicle;

/**
 * Which kart, as an Immersive Vehicles pack-item coordinate: {@code packID:systemName[:subName]}.
 * {@code subName} selects the livery/variant within a model (IV's "sub-definition").
 *
 * <p><b>Why identity and not a saved NBT blob.</b> IV's {@code mcinterface} layer deliberately
 * exposes no bridge from {@code IWrapperNBT} to a Minecraft {@code CompoundTag} — the wrapper impls
 * are package-private precisely so pack data stays version-portable — so there is no supported way
 * to persist a captured vehicle's raw data to our own JSON. There is no need to: IV builds a
 * fully-parted vehicle (engine, wheels, seats) from the pack definition whenever it is constructed
 * with null data, so a spec is enough to rebuild the kart.</p>
 *
 * <p>That also suits racing better than a state snapshot would: every grid slot gets a pristine kart
 * of the agreed model, with no carried-over fuel, damage or bolted-on parts to make one racer
 * faster than another. The cost is that a player cannot race a personally-modified loadout — the
 * garage stores which kart you own, not the exact one you built.</p>
 */
public record KartSpec(String packId, String systemName, String subName) {

    public KartSpec {
        packId = packId == null ? "" : packId.trim();
        systemName = systemName == null ? "" : systemName.trim();
        subName = subName == null ? "" : subName.trim();
    }

    /** A spec with no sub-definition, for models that have only one variant. */
    public static KartSpec of(String packId, String systemName) {
        return new KartSpec(packId, systemName, "");
    }

    public boolean isValid() {
        return !packId.isEmpty() && !systemName.isEmpty();
    }

    /**
     * Round-trips with {@link #parse(String)}. The sub-name is omitted when empty so the common
     * single-variant case reads as {@code pack:model} in configs and command output.
     */
    public String toId() {
        return subName.isEmpty() ? packId + ":" + systemName : packId + ":" + systemName + ":" + subName;
    }

    /** Parses {@code pack:model} or {@code pack:model:variant}; returns null if it is neither. */
    public static KartSpec parse(String id) {
        if (id == null) {
            return null;
        }
        String[] parts = id.trim().split(":", 3);
        if (parts.length < 2) {
            return null;
        }
        KartSpec spec = new KartSpec(parts[0], parts[1], parts.length == 3 ? parts[2] : "");
        return spec.isValid() ? spec : null;
    }

    @Override
    public String toString() {
        return toId();
    }
}
