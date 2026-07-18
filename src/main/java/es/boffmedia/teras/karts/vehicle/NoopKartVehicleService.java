package es.boffmedia.teras.karts.vehicle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * What karts does without Immersive Vehicles: nothing, quietly.
 *
 * <p>Every track, preset, garage and leaderboard still loads and can be edited — only spawning a
 * kart is impossible. Commands check {@link KartVehicleService#available()} and explain that, rather
 * than failing somewhere deeper with a less useful message.</p>
 */
final class NoopKartVehicleService implements KartVehicleService {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public List<VehicleItemRef> listVehicleItems() {
        return List.of();
    }

    @Override
    public boolean isInstalled(KartSpec spec) {
        return false;
    }

    @Override
    public Optional<VehicleRef> spawn(ServerLevel level, KartLoadout loadout, ServerPlayer placer,
                                      double x, double y, double z, float yaw) {
        return Optional.empty();
    }

    @Override
    public boolean seat(VehicleRef ref, ServerPlayer player) {
        return false;
    }

    @Override
    public void freeze(VehicleRef ref) {
    }

    @Override
    public void release(VehicleRef ref) {
    }

    @Override
    public void remove(VehicleRef ref) {
    }

    @Override
    public Optional<Vec3> positionOf(VehicleRef ref) {
        return Optional.empty();
    }

    @Override
    public boolean exists(VehicleRef ref) {
        return false;
    }

    @Override
    public Optional<VehicleRef> vehicleOf(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<KartSpec> specOf(VehicleRef ref) {
        return Optional.empty();
    }

    @Override
    public Optional<KartLoadout> loadoutOf(VehicleRef ref) {
        return Optional.empty();
    }
}
