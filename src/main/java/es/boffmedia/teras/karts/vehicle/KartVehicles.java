package es.boffmedia.teras.karts.vehicle;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.vehicle.iv.IvKartVehicleService;
import net.neoforged.fml.ModList;

/**
 * Hands out the active {@link KartVehicleService}.
 *
 * <p><b>Class-load safety.</b> {@link IvKartVehicleService} transitively references
 * {@code minecrafttransportsimulator.*} and {@code mcinterface1211.*}, so merely loading it on a
 * server without Immersive Vehicles would throw {@link NoClassDefFoundError}. The {@code new} sits
 * behind the {@code isLoaded} guard so the JVM only links it when that branch runs — the same rule
 * that keeps {@code WorldEditBridge} and the battle providers off servers that lack their mod.</p>
 */
public final class KartVehicles {
    private KartVehicles() {}

    /** Immersive Vehicles' mod id. Its own {@code InterfaceLoader.MODID}, unchanged since MTS. */
    public static final String MTS = "mts";

    private static KartVehicleService active;
    private static boolean resolved;

    public static boolean isImmersiveVehiclesLoaded() {
        return ModList.get().isLoaded(MTS);
    }

    /** The active service. Never null — falls back to a no-op when IV is absent. Resolved once. */
    public static synchronized KartVehicleService get() {
        if (resolved) {
            return active;
        }
        resolved = true;
        if (isImmersiveVehiclesLoaded()) {
            active = new IvKartVehicleService();
            Teras.LOGGER.info("Teras karts: Immersive Vehicles detected, karts enabled");
        } else {
            active = new NoopKartVehicleService();
            Teras.LOGGER.info("Teras karts: Immersive Vehicles not installed; tracks and garages "
                    + "still load, but no race can spawn karts");
        }
        return active;
    }

    /**
     * Test seam: swaps in a fake so the race engine can be driven without a game. Pass null to drop
     * back to normal detection.
     */
    static synchronized void setServiceForTests(KartVehicleService service) {
        active = service;
        resolved = service != null;
    }
}
