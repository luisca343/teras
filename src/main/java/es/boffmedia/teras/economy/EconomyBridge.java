package es.boffmedia.teras.economy;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Entry point and engine-presence guard for the economy, mirroring {@link es.boffmedia.teras.quests.QuestBridge}.
 *
 * <p>Deliberately free of engine imports: it loads on every server, so it must never drag a Pixelmon
 * class into verification. The Pixelmon-coupled classes live in {@code economy.pixelmon} and are only
 * named from inside a branch that has already checked the mod is loaded. Naming them <i>here</i> would
 * not be safe even behind the guard: the verifier resolves the types a method body mentions when this
 * class is verified, which is before any guard runs.</p>
 *
 * <p>Note what is <b>not</b> guarded: {@link EconomyStore} is engine-free and always active. An engine
 * is how players <i>spend</i> the balance, not where it lives — starbank is. A Cobblemon server, or one
 * with no Pokémon mod at all, still tracks balances; it just has no in-game currency wired to them,
 * because Cobblemon ships no bank API to bridge to.</p>
 */
public final class EconomyBridge {
    private EconomyBridge() {}

    /** True when an engine whose currency Teras can back is installed. Cobblemon has none. */
    public static boolean isAvailable() {
        return PokemonEngines.isPixelmonLoaded();
    }

    /**
     * Installs Teras as the engine's bank if that engine has one. Called from common setup.
     *
     * <p>Pixelmon is currently the only one: the takeover is process-wide, and every PokéDollar read
     * and write routes through {@link EconomyStore} to starbank afterwards. Pixelmon's own bank storage
     * stops being consulted.</p>
     */
    public static void registerIfPresent() {
        if (!isAvailable()) {
            Teras.LOGGER.info("{}; no in-game bank is backed by starbank, but balances are still "
                            + "tracked and /teras economia still works",
                    PokemonEngines.isCobblemonLoaded()
                            ? "Cobblemon is installed and has no economy API"
                            : "Pixelmon not installed");
            return;
        }
        es.boffmedia.teras.economy.pixelmon.PixelmonEconomyHook.install();
    }
}
