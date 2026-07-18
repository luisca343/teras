package es.boffmedia.teras.karts.vehicle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Everything the karts subsystem does to a vehicle, in terms Teras owns.
 *
 * <p>One interface, one real implementation (Immersive Vehicles), one no-op — this is not the
 * multi-engine provider registry the battle system uses, because there is no second vehicle mod to
 * choose between. It exists so the race engine can be driven by a fake in unit tests, the same
 * reason {@code EconomyStore} takes an injectable sync dispatcher.</p>
 *
 * <p>Implementations must tolerate a stale {@link VehicleRef} on every method: a kart can be
 * removed by another mod, an admin, or a chunk unload between one race tick and the next. Nothing
 * here throws for a vehicle that no longer exists — lookups return empty, mutations no-op.</p>
 *
 * <p>Server-thread only. IV's entity lists are not safe to mutate off-tick.</p>
 */
public interface KartVehicleService {

    /** Whether karts can actually run — false when Immersive Vehicles is not installed. */
    boolean available();

    /**
     * Every vehicle model across all installed content packs.
     *
     * <p>Queried lazily at command time, never at mod init: IV registers pack items during its own
     * loading, so anything asking during Teras construction would see an empty registry.</p>
     */
    List<VehicleItemRef> listVehicleItems();

    /** Whether a model is actually installed, so we can reject a stale preset before a race starts. */
    boolean isInstalled(KartSpec spec);

    /**
     * Spawns a pristine kart of {@code spec} at the given position and yaw.
     *
     * <p>{@code placer} must be non-null: IV reports malformed default parts by messaging the
     * placing player, and dereferences it to do so, so a null there turns a pack problem into a
     * server crash. Pass the participant the kart is being spawned for.</p>
     *
     * @return the new kart, or empty if the model is not installed or IV refused the spawn
     */
    Optional<VehicleRef> spawn(ServerLevel level, KartLoadout loadout, ServerPlayer placer,
                               double x, double y, double z, float yaw);

    /** Seats {@code player} in the kart's controller seat. False if there is none, or it is taken. */
    boolean seat(VehicleRef ref, ServerPlayer player);

    /**
     * Holds the kart still on the grid: parking brake on and engine off, so a racer cannot jump the
     * start. Also locks it, so nobody else can climb in during the countdown.
     */
    void freeze(VehicleRef ref);

    /** Releases the grid hold and starts the engine. The "GO" of the countdown. */
    void release(VehicleRef ref);

    /** Removes the kart from the world. Safe to call twice. */
    void remove(VehicleRef ref);

    /** Where the kart is right now — the authoritative position checkpoints are tested against. */
    Optional<Vec3> positionOf(VehicleRef ref);

    /** Whether the kart still exists in the world. */
    boolean exists(VehicleRef ref);

    /** The kart {@code player} is currently riding, if any. */
    Optional<VehicleRef> vehicleOf(ServerPlayer player);

    /** Which model {@code ref} is — used to record what a player drove. */
    Optional<KartSpec> specOf(VehicleRef ref);

    /**
     * The kart as currently built: its model plus the part in every filled slot. This is what
     * capturing a vehicle into a preset or a garage reads, so a build survives being re-spawned.
     */
    Optional<KartLoadout> loadoutOf(VehicleRef ref);
}
