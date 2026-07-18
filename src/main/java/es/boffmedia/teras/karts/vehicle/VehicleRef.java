package es.boffmedia.teras.karts.vehicle;

import java.util.UUID;

/**
 * A handle to one spawned kart, as IV's entity UUID plus the dimension it lives in.
 *
 * <p>Deliberately engine-free and Minecraft-free: the race engine holds these per participant and
 * must stay unit-testable, and the vehicle ledger persists them as plain strings so orphaned karts
 * can be culled after a crash without IV being loaded at the time we read the file.</p>
 *
 * @param id        IV's {@code AEntityA_Base.uniqueUUID} — not the Minecraft entity id, which
 *                  belongs to the builder entity wrapping it
 * @param dimension the level's {@code ResourceLocation} as a string, e.g. {@code minecraft:overworld}
 */
public record VehicleRef(UUID id, String dimension) {

    public VehicleRef {
        dimension = dimension == null ? "" : dimension;
    }
}
