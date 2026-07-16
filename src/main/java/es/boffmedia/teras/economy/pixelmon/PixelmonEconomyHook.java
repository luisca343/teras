package es.boffmedia.teras.economy.pixelmon;

import com.pixelmonmod.pixelmon.api.economy.BankAccountProxy;
import es.boffmedia.teras.Teras;

/**
 * Hands Pixelmon's bank over to Teras, so every PokéDollar read and write in the game routes through
 * {@code EconomyStore} to starbank and Pixelmon's own storage stops being consulted.
 *
 * <p><b>Pixelmon-coupled.</b> Only ever named from {@code EconomyBridge}, behind its mod-presence
 * check. The check cannot live in this class: naming a Pixelmon type anywhere in a method body makes
 * the verifier load it when the <i>enclosing</i> class is verified, which is before any guard runs.</p>
 */
public final class PixelmonEconomyHook {
    private PixelmonEconomyHook() {}

    public static void install() {
        BankAccountProxy.setAccountManager(new TerasBankManager());
        Teras.LOGGER.info("Teras is now Pixelmon's bank; PokeDollars are backed by starbank");
    }
}
