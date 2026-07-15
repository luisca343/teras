package es.boffmedia.teras.battle.api;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.cobblemon.CobblemonBattleProvider;
import es.boffmedia.teras.battle.pixelmon.PixelmonBattleProvider;
import net.neoforged.fml.ModList;

/**
 * Detects which Pokémon engine is installed and hands out the matching {@link BattleProvider}.
 *
 * <p><b>Class-load safety.</b> A provider class transitively references its engine's types, so
 * loading {@link PixelmonBattleProvider} on a Cobblemon-only server (or vice-versa) would throw
 * {@code NoClassDefFoundError}. Each {@code new} therefore sits behind its {@code isLoaded} guard:
 * the JVM only links a provider class when that branch actually runs, so the absent engine's
 * provider is never linked. The chosen provider is cached after the first successful lookup.</p>
 */
public final class BattleProviders {
    private BattleProviders() {}

    private static final String PIXELMON = "pixelmon";
    private static final String COBBLEMON = "cobblemon";

    private static BattleProvider active;
    private static boolean resolved;

    public static boolean isPixelmonLoaded() {
        return ModList.get().isLoaded(PIXELMON);
    }

    public static boolean isCobblemonLoaded() {
        return ModList.get().isLoaded(COBBLEMON);
    }

    /**
     * The active provider, or {@code null} if neither engine is installed. Resolved once and cached.
     * Pixelmon wins if — unusually — both are present.
     */
    public static synchronized BattleProvider get() {
        if (resolved) {
            return active;
        }
        resolved = true;
        if (isPixelmonLoaded()) {
            active = new PixelmonBattleProvider();
        } else if (isCobblemonLoaded()) {
            active = new CobblemonBattleProvider();
        } else {
            active = null;
            Teras.LOGGER.warn("No supported battle engine (Pixelmon/Cobblemon) is installed; "
                    + "Teras battle commands will be unavailable.");
        }
        if (active != null) {
            Teras.LOGGER.info("Teras battle engine: {}", active.engineId());
        }
        return active;
    }
}
