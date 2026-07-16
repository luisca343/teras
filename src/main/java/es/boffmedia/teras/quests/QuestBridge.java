package es.boffmedia.teras.quests;

import es.boffmedia.teras.Teras;
import net.neoforged.fml.ModList;

/**
 * Entry point and mod-presence guard for the quest system.
 *
 * <p>Deliberately free of {@code noppes} imports: it is loaded on every server, including those
 * without CustomNPCs, so it must never drag a CustomNPCs class into verification. The classes that do
 * touch the API ({@link QuestRegistrar}, {@link QuestService}, …) are only named inside a branch that
 * has already checked {@link #isAvailable()}, so the JVM never loads them otherwise. Same isolation
 * pattern {@code TerasNet} uses for Pixelmon's {@code SpawnScanner}.</p>
 */
public final class QuestBridge {
    private QuestBridge() {}

    public static final String CUSTOMNPCS_MOD_ID = "customnpcs";
    public static final String PIXELMON_MOD_ID = "pixelmon";

    /** True when CustomNPCs is installed; guards every entry into the CustomNPCs-coupled classes. */
    public static boolean isAvailable() {
        return ModList.get().isLoaded(CUSTOMNPCS_MOD_ID);
    }

    /** True when hunt quests can run — they need Pixelmon's events and CustomNPCs' quests. */
    public static boolean isHuntAvailable() {
        return isAvailable() && ModList.get().isLoaded(PIXELMON_MOD_ID);
    }

    /** Registers the quest handlers if their dependencies are present. Called from common setup. */
    public static void registerIfPresent() {
        if (!isAvailable()) {
            Teras.LOGGER.info("CustomNPCs not installed; quest system disabled");
            return;
        }
        QuestRegistrar.register();
    }
}
