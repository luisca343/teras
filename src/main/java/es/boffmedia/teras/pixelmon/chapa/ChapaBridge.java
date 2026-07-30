package es.boffmedia.teras.pixelmon.chapa;

import es.boffmedia.teras.Teras;
import net.neoforged.fml.ModList;

/**
 * Wires the rusty cap into Pixelmon, or does nothing.
 *
 * <p>Same isolation as the other bridges: this class names no Pixelmon type itself, so a server
 * without Pixelmon loads it, takes the early return and never touches
 * {@link InteractionChapaOxidada}. The cap stays registered and inert there.</p>
 */
public final class ChapaBridge {
    private ChapaBridge() {}

    public static void registerIfPresent() {
        if (!ModList.get().isLoaded("pixelmon")) {
            return;
        }
        register();
        Teras.LOGGER.info("Chapa oxidada: Pixelmon interaction registered");
    }

    /**
     * Split out so the Pixelmon class above is only resolved once the guard has passed —
     * verification of {@link #registerIfPresent} would otherwise load it on a server without it.
     */
    private static void register() {
        // A public static list is genuinely how Pixelmon exposes this; there is no register method.
        com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity.interactionList
                .add(new InteractionChapaOxidada());
    }
}
