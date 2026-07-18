package es.boffmedia.teras.karts.vehicle.iv;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.vehicle.KartLoadout;
import es.boffmedia.teras.karts.vehicle.KartSpec;
import es.boffmedia.teras.karts.vehicle.KartVehicleService;
import es.boffmedia.teras.karts.vehicle.VehicleItemRef;
import es.boffmedia.teras.karts.vehicle.VehicleRef;
import mcinterface1211.BuilderEntityLinkedSeat;
import mcinterface1211.WrapperPlayer;
import mcinterface1211.WrapperWorld;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.components.AEntityA_Base;
import minecrafttransportsimulator.entities.instances.APart;
import minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics;
import minecrafttransportsimulator.entities.instances.PartEngine;
import minecrafttransportsimulator.entities.instances.PartSeat;
import minecrafttransportsimulator.items.components.AItemPack;
import minecrafttransportsimulator.items.instances.ItemVehicle;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packloading.PackParser;
import minecrafttransportsimulator.systems.ConfigSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only class in Teras allowed to import {@code minecrafttransportsimulator.*} or
 * {@code mcinterface1211.*} — same isolation rule as {@code WorldEditBridge} and the battle
 * providers. Callers reach it through {@code KartVehicles.get()}, which constructs it only behind a
 * {@code ModList.isLoaded("mts")} guard, so it never links on a server without Immersive Vehicles.
 *
 * <p>Written against IV 24.0.0 for 1.21.1 (CurseForge file 7926606); every signature used here was
 * checked against that jar rather than against upstream master, which has since moved on.</p>
 *
 * <p>Every public method tolerates a vehicle that has already gone away — races outlive individual
 * karts, and a chunk unload or an admin {@code /kill} must not break a running race.</p>
 */
public final class IvKartVehicleService implements KartVehicleService {

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<VehicleItemRef> listVehicleItems() {
        if (!PackParser.arePacksPresent()) {
            return List.of();
        }
        List<VehicleItemRef> out = new ArrayList<>();
        for (AItemPack<?> item : PackParser.getAllPackItems()) {
            if (item instanceof ItemVehicle vehicle) {
                out.add(new VehicleItemRef(specOf(vehicle), vehicle.getItemName()));
            }
        }
        return out;
    }

    @Override
    public boolean isInstalled(KartSpec spec) {
        return findVehicleItem(spec) != null;
    }

    @Override
    public Optional<VehicleRef> spawn(ServerLevel level, KartLoadout loadout, ServerPlayer placer,
                                      double x, double y, double z, float yaw) {
        if (loadout == null || !loadout.isValid()) {
            return Optional.empty();
        }
        KartSpec spec = loadout.model();
        ItemVehicle item = findVehicleItem(spec);
        if (item == null) {
            Teras.LOGGER.warn("Karts: no vehicle item installed for spec {}", spec);
            return Optional.empty();
        }
        try {
            AWrapperWorld world = WrapperWorld.getWrapperFor(level);
            WrapperPlayer placerWrapper = WrapperPlayer.getWrapperFor(placer);

            // Null data means "a new vehicle": IV then fills every slot from the pack's default
            // parts, which is what makes a spec alone enough to rebuild a complete kart.
            EntityVehicleF_Physics vehicle = new EntityVehicleF_Physics(world, placerWrapper, item, null);

            vehicle.position.set(x, y, z);
            vehicle.prevPosition.set(vehicle.position);
            // Faces exactly the way the grid slot was authored. IV's own placement code adds 90°
            // here, but that is a convenience for placing a car perpendicular to the block face you
            // clicked — on a starting grid it turns every kart sideways to the track.
            vehicle.orientation.setToAngles(new Point3D(0, yaw, 0));
            vehicle.prevOrientation.set(vehicle.orientation);
            vehicle.motion.set(0, 0, 0);
            vehicle.prevMotion.set(vehicle.motion);

            world.spawnEntity(vehicle);
            // Parts must come after the spawn so they inherit the vehicle's tick order and position.
            // Null data fills every slot from the pack's defaults, which is the baseline the
            // loadout then overrides.
            vehicle.addPartsPostAddition(placerWrapper, null);
            applyParts(vehicle, loadout, placerWrapper);

            return Optional.of(new VehicleRef(vehicle.uniqueUUID, dimensionOf(level)));
        } catch (Exception e) {
            Teras.LOGGER.error("Karts: failed to spawn {} for {}", spec, placer.getGameProfile().getName(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean seat(VehicleRef ref, ServerPlayer player) {
        EntityVehicleF_Physics vehicle = resolve(ref, player.serverLevel());
        if (vehicle == null) {
            return false;
        }
        try {
            PartSeat seat = findControllerSeat(vehicle);
            if (seat == null) {
                Teras.LOGGER.warn("Karts: vehicle {} has no controller seat", ref.id());
                return false;
            }
            return seat.setRider(WrapperPlayer.getWrapperFor(player), true);
        } catch (Exception e) {
            Teras.LOGGER.error("Karts: failed to seat {} in {}", player.getGameProfile().getName(), ref.id(), e);
            return false;
        }
    }

    @Override
    public void freeze(VehicleRef ref) {
        withVehicle(ref, vehicle -> {
            vehicle.parkingBrakeVar.setTo(1, true);
            vehicle.brakeVar.setTo(1, true);
            // Locked keeps anyone else from climbing in while the grid is forming.
            vehicle.lockedVar.setTo(1, true);
            for (PartEngine engine : vehicle.engines) {
                engine.magnetoVar.setTo(0, true);
            }
        }, "freeze");
    }

    @Override
    public void release(VehicleRef ref) {
        withVehicle(ref, vehicle -> {
            ensureFuel(vehicle);
            vehicle.lockedVar.setTo(0, true);
            vehicle.parkingBrakeVar.setTo(0, true);
            vehicle.brakeVar.setTo(0, true);
            for (PartEngine engine : vehicle.engines) {
                engine.magnetoVar.setTo(1, true);
                engine.electricStarterVar.setTo(1, true);
            }
        }, "release");
    }

    @Override
    public void remove(VehicleRef ref) {
        withVehicle(ref, AEntityA_Base::remove, "remove");
    }

    @Override
    public Optional<Vec3> positionOf(VehicleRef ref) {
        EntityVehicleF_Physics vehicle = resolveAnywhere(ref);
        if (vehicle == null) {
            return Optional.empty();
        }
        Point3D p = vehicle.position;
        return Optional.of(new Vec3(p.x, p.y, p.z));
    }

    @Override
    public boolean exists(VehicleRef ref) {
        return resolveAnywhere(ref) != null;
    }

    @Override
    public Optional<VehicleRef> vehicleOf(ServerPlayer player) {
        // A rider sits on a builder entity that stands in for the seat part, not on the vehicle
        // itself; the vehicle is two hops up: builder -> seat part -> vehicle.
        if (!(player.getVehicle() instanceof BuilderEntityLinkedSeat builder)) {
            return Optional.empty();
        }
        if (!(builder.entity instanceof APart part) || part.vehicleOn == null) {
            return Optional.empty();
        }
        return Optional.of(new VehicleRef(part.vehicleOn.uniqueUUID, dimensionOf(player.serverLevel())));
    }

    @Override
    public Optional<KartSpec> specOf(VehicleRef ref) {
        EntityVehicleF_Physics vehicle = resolveAnywhere(ref);
        if (vehicle == null || vehicle.definition == null) {
            return Optional.empty();
        }
        String subName = vehicle.subDefinition != null ? vehicle.subDefinition.subName : "";
        return Optional.of(new KartSpec(vehicle.definition.packID, vehicle.definition.systemName, subName));
    }

    @Override
    public Optional<KartLoadout> loadoutOf(VehicleRef ref) {
        EntityVehicleF_Physics vehicle = resolveAnywhere(ref);
        if (vehicle == null || vehicle.definition == null) {
            return Optional.empty();
        }
        Map<Integer, KartSpec> parts = new LinkedHashMap<>();
        for (APart part : vehicle.partsInSlots) {
            // partsInSlots is indexed by slot and holds null for an empty one.
            if (part == null || part.definition == null) {
                continue;
            }
            String subName = part.subDefinition != null ? part.subDefinition.subName : "";
            parts.put(part.placementSlot,
                    new KartSpec(part.definition.packID, part.definition.systemName, subName));
        }
        return specOf(ref).map(model -> new KartLoadout(model, parts));
    }

    // --- internals -------------------------------------------------------------------------

    /**
     * Swaps in the loadout's parts over the pack defaults IV has just fitted.
     *
     * <p>A slot already holding the wanted part is left alone rather than removed and refitted —
     * that is the common case for a loadout that only pins one or two slots, and refitting would
     * discard part state for no reason.</p>
     */
    private static void applyParts(EntityVehicleF_Physics vehicle, KartLoadout loadout,
                                   WrapperPlayer placer) {
        if (!loadout.hasCustomParts()) {
            return;
        }
        for (Map.Entry<Integer, KartSpec> wanted : loadout.parts().entrySet()) {
            int slot = wanted.getKey();
            KartSpec partSpec = wanted.getValue();
            if (slot < 0 || slot >= vehicle.partsInSlots.size() || !partSpec.isValid()) {
                Teras.LOGGER.warn("Karts: loadout for {} names slot {}, which this model does not have",
                        loadout.model(), slot);
                continue;
            }
            try {
                APart current = vehicle.partsInSlots.get(slot);
                if (current != null && matches(current, partSpec)) {
                    continue;
                }
                Object partItem = partSpec.subName().isEmpty()
                        ? PackParser.getItem(partSpec.packId(), partSpec.systemName())
                        : PackParser.getItem(partSpec.packId(), partSpec.systemName(), partSpec.subName());
                if (!(partItem instanceof AItemPack<?> pack)) {
                    Teras.LOGGER.warn("Karts: no part installed with id {}", partSpec);
                    continue;
                }
                if (current != null) {
                    vehicle.removePart(current, false, true);
                }
                // bypassSlotChecks: an admin who pinned this part in this slot has already decided
                // it belongs there, and pack slot rules would otherwise silently drop the choice.
                vehicle.addPartFromStack(pack.getNewStack(null), placer, slot, true, false);
            } catch (Exception e) {
                Teras.LOGGER.error("Karts: failed to fit {} in slot {}", partSpec, slot, e);
            }
        }
    }

    private static boolean matches(APart part, KartSpec spec) {
        if (part.definition == null) {
            return false;
        }
        String subName = part.subDefinition != null ? part.subDefinition.subName : "";
        return part.definition.packID.equals(spec.packId())
                && part.definition.systemName.equals(spec.systemName())
                && subName.equals(spec.subName());
    }

    private static KartSpec specOf(ItemVehicle item) {
        String subName = item.subDefinition != null ? item.subDefinition.subName : "";
        return new KartSpec(item.definition.packID, item.definition.systemName, subName);
    }

    private static String dimensionOf(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /**
     * IV's registry is keyed by sub-name, so a spec without one cannot be looked up directly; fall
     * back to the model's first variant, which is what a player picking "that car" means.
     */
    private static ItemVehicle findVehicleItem(KartSpec spec) {
        if (spec == null || !spec.isValid()) {
            return null;
        }
        Object direct = spec.subName().isEmpty()
                ? PackParser.getItem(spec.packId(), spec.systemName())
                : PackParser.getItem(spec.packId(), spec.systemName(), spec.subName());
        if (direct instanceof ItemVehicle vehicle) {
            return vehicle;
        }
        if (!spec.subName().isEmpty()) {
            return null;
        }
        for (AItemPack<?> item : PackParser.getAllPackItems()) {
            if (item instanceof ItemVehicle vehicle
                    && vehicle.definition.packID.equals(spec.packId())
                    && vehicle.definition.systemName.equals(spec.systemName())) {
                return vehicle;
            }
        }
        return null;
    }

    private static PartSeat findControllerSeat(EntityVehicleF_Physics vehicle) {
        PartSeat fallback = null;
        for (APart part : vehicle.allParts) {
            if (part instanceof PartSeat seat) {
                if (seat.placementDefinition != null && seat.placementDefinition.isController) {
                    return seat;
                }
                if (fallback == null) {
                    fallback = seat;
                }
            }
        }
        return fallback;
    }

    private EntityVehicleF_Physics resolve(VehicleRef ref, ServerLevel level) {
        if (ref == null || level == null) {
            return null;
        }
        try {
            AEntityA_Base entity = WrapperWorld.getWrapperFor(level).getEntity(ref.id());
            return entity instanceof EntityVehicleF_Physics vehicle && vehicle.isValid ? vehicle : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves without being told the level, by asking the dimension named in the ref. Used by the
     * per-tick reads, which have a ref but no player to borrow a level from.
     */
    private EntityVehicleF_Physics resolveAnywhere(VehicleRef ref) {
        if (ref == null) {
            return null;
        }
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!dimensionOf(level).equals(ref.dimension())) {
                continue;
            }
            return resolve(ref, level);
        }
        return null;
    }

    private void withVehicle(VehicleRef ref, java.util.function.Consumer<EntityVehicleF_Physics> action,
                             String what) {
        EntityVehicleF_Physics vehicle = resolveAnywhere(ref);
        if (vehicle == null) {
            return;
        }
        try {
            action.accept(vehicle);
        } catch (Exception e) {
            Teras.LOGGER.error("Karts: {} failed for vehicle {}", what, ref.id(), e);
        }
    }

    /**
     * Guarantees a kart can actually drive. IV fuels a freshly placed vehicle only when the pack
     * declares a {@code defaultFuelQty}, so a model that omits it would sit dead on the grid.
     * Tops up whatever fluid is already in the tank, and otherwise picks the same "most potent
     * valid fuel" IV itself would for the engine fitted.
     */
    private static void ensureFuel(EntityVehicleF_Physics vehicle) {
        String current = vehicle.fuelTank.getFluid();
        if (current != null && !current.isEmpty()) {
            vehicle.fuelTank.manuallySet(current, vehicle.fuelTank.getFluidMod(), vehicle.fuelTank.getMaxLevel());
            return;
        }
        for (PartEngine engine : vehicle.engines) {
            if (engine.definition == null || engine.definition.engine == null) {
                continue;
            }
            String fuel = mostPotentFuel(engine.definition.engine.fuelType);
            if (!fuel.isEmpty()) {
                vehicle.fuelTank.manuallySet(fuel, "", vehicle.fuelTank.getMaxLevel());
                return;
            }
        }
        Teras.LOGGER.warn("Karts: no valid fuel for {}:{}; the kart may not move",
                vehicle.definition.packID, vehicle.definition.systemName);
    }

    private static String mostPotentFuel(String fuelType) {
        Map<String, Map<String, Double>> fuels = ConfigSystem.settings.fuel.fuels;
        Map<String, Double> candidates = fuelType == null ? null : fuels.get(fuelType);
        if (candidates == null) {
            // Electric engines carry a fuel type that is not in the fuels table.
            return PartEngine.ELECTRICITY_FUEL;
        }
        String best = "";
        for (Map.Entry<String, Double> candidate : candidates.entrySet()) {
            if (!InterfaceManager.coreInterface.isFluidValid(candidate.getKey())) {
                continue;
            }
            if (best.isEmpty() || candidates.get(best) < candidate.getValue()) {
                best = candidate.getKey();
            }
        }
        return best;
    }
}
