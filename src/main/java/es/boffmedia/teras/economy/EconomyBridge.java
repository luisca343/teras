package es.boffmedia.teras.economy;

import es.boffmedia.teras.Teras;
import net.neoforged.fml.ModList;

/**
 * Entry point and mod-presence guard for the economy, mirroring {@link es.boffmedia.teras.quests.QuestBridge}.
 *
 * <p>Deliberately free of {@code pixelmonmod} imports: it loads on every server, so it must never drag
 * a Pixelmon class into verification. {@link TerasBankManager} and {@link TerasBankAccount} are only
 * named inside a branch that has already checked {@link #isAvailable()}, so the JVM never loads them
 * otherwise.</p>
 *
 * <p>Note what is <b>not</b> guarded: {@link EconomyStore} is Pixelmon-free and always active. Pixelmon
 * is how players <i>spend</i> the balance, not where it lives — starbank is. A server without Pixelmon
 * still tracks balances, it just has no PokéDollar surface wired to them.</p>
 */
public final class EconomyBridge {
    private EconomyBridge() {}

    public static final String PIXELMON_MOD_ID = "pixelmon";

    /** True when Pixelmon is installed; guards every entry into the Pixelmon-coupled classes. */
    public static boolean isAvailable() {
        return ModList.get().isLoaded(PIXELMON_MOD_ID);
    }

    /**
     * Installs Teras as Pixelmon's bank if Pixelmon is present. Called from common setup.
     *
     * <p>This takes over Pixelmon's economy process-wide — every PokéDollar read and write in the game
     * routes through {@link EconomyStore} to starbank afterwards. Pixelmon's own bank storage stops
     * being consulted.</p>
     */
    public static void registerIfPresent() {
        if (!isAvailable()) {
            Teras.LOGGER.info("Pixelmon not installed; the Pixelmon bank bridge is disabled "
                    + "(starbank balances are still tracked)");
            return;
        }
        com.pixelmonmod.pixelmon.api.economy.BankAccountProxy.setAccountManager(new TerasBankManager());
        Teras.LOGGER.info("Teras is now Pixelmon's bank; PokeDollars are backed by starbank");
    }
}
