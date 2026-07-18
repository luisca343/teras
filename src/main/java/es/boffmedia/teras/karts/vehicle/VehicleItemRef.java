package es.boffmedia.teras.karts.vehicle;

/**
 * One vehicle model offered by an installed IV content pack, for command suggestions and admin
 * listings. {@code displayName} is IV's human-readable item name, which is what an admin recognises
 * ("Turbo Hatchback") when {@link KartSpec#toId()} ("oamp:hatchback:red") is not obvious.
 */
public record VehicleItemRef(KartSpec spec, String displayName) {

    public String packId() {
        return spec.packId();
    }

    public String systemName() {
        return spec.systemName();
    }

    public String subName() {
        return spec.subName();
    }
}
