package es.boffmedia.teras.region;

import es.boffmedia.teras.net.RegionBannerPayload;
import es.boffmedia.teras.region.model.TerasRegion;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Flashes the cartel on region enter: towns show their own {@code textures/carteles/<name>.png},
 * everything else stays silent unless the region sets an explicit banner. Exit stays a no-op — the
 * 1.16.5 exit cartel was already commented out.
 */
public final class RegionBannerSender implements RegionListener {

    /** Matches the 1.16.5 {@code renderizarCartel(cartel, 2)} hold time. */
    private static final int HOLD_SECONDS = 2;

    @Override
    public void onEnter(ServerPlayer player, TerasRegion region) {
        String banner = region.bannerOrNull();
        if (banner != null) {
            PacketDistributor.sendToPlayer(player, new RegionBannerPayload(banner, HOLD_SECONDS));
        }
    }
}
