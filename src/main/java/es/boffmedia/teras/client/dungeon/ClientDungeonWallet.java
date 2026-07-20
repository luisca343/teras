package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.DungeonWalletPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The client's copy of the party purse, as last sent by the server. Pure display, same contract as
 * {@link ClientDungeonMap}: nothing here feeds back into the run, and the server never trusts it.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class ClientDungeonWallet {
    private ClientDungeonWallet() {}

    private static DungeonWalletPayload state = DungeonWalletPayload.hidden();

    public static void accept(DungeonWalletPayload payload) {
        state = payload == null ? DungeonWalletPayload.hidden() : payload;
    }

    public static DungeonWalletPayload state() {
        return state;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        state = DungeonWalletPayload.hidden();
    }
}
