package es.boffmedia.teras.karts.model;

/**
 * How a race decides which kart each racer drives.
 *
 * @param mode      which rule applies
 * @param reference the preset name for {@link Mode#SPEC}, the selection name for
 *                  {@link Mode#CURATED}, and unused for {@link Mode#GARAGE}
 */
public record KartProvisioning(Mode mode, String reference) {

    public enum Mode {
        /** Everyone drives the same kart — a spec race, where the driving is the only variable. */
        SPEC,
        /** Racers pick from a curated list, so the field stays balanced but not identical. */
        CURATED,
        /** Racers bring a kart they own. */
        GARAGE
    }

    public static KartProvisioning spec(String presetName) {
        return new KartProvisioning(Mode.SPEC, presetName);
    }

    public static KartProvisioning curated(String selectionName) {
        return new KartProvisioning(Mode.CURATED, selectionName);
    }

    public static KartProvisioning garage() {
        return new KartProvisioning(Mode.GARAGE, "");
    }

    public KartProvisioning {
        reference = reference == null ? "" : reference;
    }

    public String describe() {
        return switch (mode) {
            case SPEC -> "kart fijo (" + reference + ")";
            case CURATED -> "selección '" + reference + "'";
            case GARAGE -> "kart propio";
        };
    }
}
