package es.boffmedia.teras.client;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.mcef.JsQueryCallback;
import es.boffmedia.teras.mcef.PendingQueries;
import es.boffmedia.teras.net.McefResponsePayload;
import es.boffmedia.teras.net.RegionBannerPayload;
import es.boffmedia.teras.net.GpsPayload;
import es.boffmedia.teras.net.RegionSyncPayload;
import es.boffmedia.teras.net.ServerConfigPayload;
import es.boffmedia.teras.net.StorageChangedPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only network handling. Kept separate from {@link es.boffmedia.teras.net.TerasNet} (which is
 * loaded on both sides) so its client-only dependencies (JCEF via {@link JsQueryCallback}) are never
 * classloaded on a dedicated server.
 */
public final class ClientNetHandler {
    private ClientNetHandler() {}

    /**
     * Resolves the originating {@code mcefQuery} callback (matched by request id). A failed reply
     * ({@code ok == false}) rejects the JS promise via {@code failure}, so a server-side error surfaces
     * as a rejected query the page already handles — not a success carrying an error body, which is how
     * a failed grant used to read as a completed one.
     */
    public static void onMcefResponse(McefResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            JsQueryCallback cb = PendingQueries.take(payload.requestId());
            if (cb == null) {
                Teras.LOGGER.warn("MCEF response for unknown/expired request id {}: {}",
                        payload.requestId(), payload.json());
                return;
            }
            if (payload.ok()) {
                cb.success(payload.json());
            } else {
                cb.failure(SERVER_ERROR_CODE, payload.json());
            }
        });
    }

    /** Passed to {@code mcefQuery}'s {@code onFailure} for a server-side failure; the page reads the message, not the code. */
    private static final int SERVER_ERROR_CODE = 500;

    /** Stores the config of the server we just joined; see {@link ServerConfig}. */
    public static void onServerConfig(ServerConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ServerConfig.accept(payload));
    }

    /**
     * Optional page hook, called on every open browser: pages that do not define it are unaffected,
     * so the mod stays deployable ahead of the website. The PC app defines it to invalidate its
     * queries — see {@link StorageChangedPayload}.
     */
    private static final String STORAGE_CHANGED_JS =
            "if (typeof window.terasStorageChanged === 'function') { try { window.terasStorageChanged(); }"
                    + " catch (e) { console.error('terasStorageChanged failed', e); } }";

    /** The player's Pokémon storage changed server-side; tell any open SmartRotom to refetch. */
    public static void onStorageChanged(StorageChangedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> es.boffmedia.teras.mcef.TerasMCEF.broadcastJS(STORAGE_CHANGED_JS));
    }

    /** The player entered a region with a cartel; slide it in. See {@link RegionBannerPayload}. */
    public static void onRegionBanner(RegionBannerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> es.boffmedia.teras.client.region.CartelOverlay
                .show(payload.banner(), payload.holdSeconds()));
    }

    /** The server's region catalog (login or post-mutation); see {@link RegionSyncPayload}. */
    public static void onRegionSync(RegionSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> es.boffmedia.teras.client.region.ClientRegionStore
                .accept(payload.json()));
    }

    /** Turns the live GPS on or off for this player; see {@link GpsPayload}. */
    public static void onGps(GpsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.active()) {
                es.boffmedia.teras.client.region.ClientGps.start(payload.x(), payload.z());
            } else {
                es.boffmedia.teras.client.region.ClientGps.stop();
            }
        });
    }

    /** Refreshes this player's race HUD; see {@link es.boffmedia.teras.net.RaceHudPayload}. */
    public static void onRaceHud(es.boffmedia.teras.net.RaceHudPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> es.boffmedia.teras.client.karts.ClientRaceHud.accept(payload));
    }

    /** Refreshes this player's dungeon minimap; see {@link es.boffmedia.teras.net.DungeonMapPayload}. */
    public static void onDungeonMap(es.boffmedia.teras.net.DungeonMapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> es.boffmedia.teras.client.dungeon.ClientDungeonMap.accept(payload));
    }
}
