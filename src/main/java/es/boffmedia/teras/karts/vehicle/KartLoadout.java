package es.boffmedia.teras.karts.vehicle;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A kart as actually built: which model, plus which part sits in which slot.
 *
 * <p>A bare {@link KartSpec} names a model, and Immersive Vehicles fills it with the pack's default
 * parts — fine for a spec race, useless if a server wants a kart with a particular engine, wheels
 * or bodywork. The loadout carries those choices, keyed by IV's slot index, so a preset can pin a
 * whole build rather than just a model.</p>
 *
 * <p>Slots left out of {@code parts} keep the pack default, so a loadout only has to name what it
 * changes; an empty map is exactly the old behaviour.</p>
 *
 * @param model the vehicle itself
 * @param parts slot index → the part to fit there, as its own pack coordinate
 */
public record KartLoadout(KartSpec model, Map<Integer, KartSpec> parts) {

    public KartLoadout {
        parts = parts == null ? Map.of() : Map.copyOf(parts);
    }

    /** A stock kart: the model with whatever parts the pack fits by default. */
    public static KartLoadout of(KartSpec model) {
        return new KartLoadout(model, Map.of());
    }

    public boolean isValid() {
        return model != null && model.isValid();
    }

    public boolean hasCustomParts() {
        return !parts.isEmpty();
    }

    /** Returns a copy with one slot pinned to a part. */
    public KartLoadout withPart(int slot, KartSpec part) {
        Map<Integer, KartSpec> updated = new LinkedHashMap<>(parts);
        if (part == null || !part.isValid()) {
            updated.remove(slot);
        } else {
            updated.put(slot, part);
        }
        return new KartLoadout(model, updated);
    }

    /** Short human-readable form for command output: the model, and how many slots are pinned. */
    public String describe() {
        return hasCustomParts()
                ? model.toId() + " (" + parts.size() + " piezas fijadas)"
                : model.toId();
    }
}
