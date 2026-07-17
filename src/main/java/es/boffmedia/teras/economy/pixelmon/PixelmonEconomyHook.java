package es.boffmedia.teras.economy.pixelmon;

import com.pixelmonmod.pixelmon.api.economy.BankAccountProxy;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.economy.EconomyStore;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Hands Pixelmon's bank over to Teras, so every PokéDollar read and write in the game routes through
 * {@code EconomyStore} to starbank and Pixelmon's own storage stops being consulted.
 *
 * <p>Also keeps the client's PokéDollar counter in sync: Pixelmon caches the <i>displayed</i> balance
 * separately from the account, so swapping the manager changes {@code getBalance()} but never refreshes
 * the number on screen (it reads 0 until a balance packet is sent). A listener pushes every
 * {@code EconomyStore} change to the client via {@code BankAccount.updatePlayer()}.</p>
 *
 * <p><b>Pixelmon-coupled.</b> Only ever named from {@code EconomyBridge}, behind its mod-presence
 * check — the check cannot live here, as naming a Pixelmon type in a method body makes the verifier
 * load it when the enclosing class is verified, before any guard runs.</p>
 */
public final class PixelmonEconomyHook {
    private PixelmonEconomyHook() {}

    public static void install() {
        BankAccountProxy.setAccountManager(new TerasBankManager());
        EconomyStore.addChangeListener(PixelmonEconomyHook::refreshClient);
        PixelmonShopSync.install();
        Teras.LOGGER.info("Teras is now Pixelmon's bank; PokeDollars are backed by starbank");
    }

    /**
     * Pushes the player's current {@link EconomyStore} balance to their client so the on-screen counter
     * matches the bank. Hops to the server thread ({@code updatePlayer} sends a packet); a no-op when the
     * player is offline or before the server is up.
     */
    private static void refreshClient(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> new TerasBankAccount(playerId).updatePlayer());
    }
}
