package es.boffmedia.teras.battle;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.integration.PokemonEngines;

/**
 * Installs the backpack → Pixelmon battle-item scanner, mirroring {@link es.boffmedia.teras.dex.DexBridge}.
 *
 * <p>Must stay free of engine imports: it loads on every server, so the Pixelmon-coupled
 * {@code battle.pixelmon.BackpackItemScanner} may only be named inside an already-guarded branch.</p>
 *
 * <p>Pixelmon-only, and there is no Cobblemon branch to add later: {@code BattleItemScanner} is a
 * Pixelmon concept with no Cobblemon analogue. It needs no backpack mod to be installed either — the
 * scanner reads any container item through a vanilla NeoForge capability, so it is registered whenever
 * Pixelmon is present and simply finds nothing if the player carries no backpack.</p>
 */
public final class BackpackBridge {
    private BackpackBridge() {}

    /** Registers the scanner with Pixelmon. Called from common setup. */
    public static void registerIfPresent() {
        if (!PokemonEngines.isPixelmonLoaded()) {
            return;
        }
        es.boffmedia.teras.battle.pixelmon.BackpackItemScanner.install();
        Teras.LOGGER.info("Battle items inside backpacks are visible to Pixelmon.");
    }
}
