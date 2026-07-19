package es.boffmedia.teras.dungeon.ability;

import es.boffmedia.teras.Teras;
import net.neoforged.fml.ModList;

/**
 * Presence guard and entry point for the CustomNPCs half of the ability layer, mirroring
 * {@code QuestBridge}.
 *
 * <p>Deliberately free of {@code noppes} imports <b>and</b> of any reference to a class that has
 * them — including {@code CnpcBridge}, whose own {@code available()} would drag {@code NpcAPI} in
 * through the back door. It is loaded on every server, so the check has to be
 * {@link ModList} and nothing else; {@link CnpcAbilityEvents} is named only inside the branch that
 * has already passed.</p>
 *
 * <p>Without CustomNPCs this is a no-op and {@code teras:dungeon_enemy} still runs its abilities —
 * {@link AbilityEngine} depends on neither mod.</p>
 */
public final class AbilityBridge {
    private AbilityBridge() {}

    public static void registerIfPresent() {
        if (!ModList.get().isLoaded("customnpcs")) {
            Teras.LOGGER.info("Dungeons: CustomNPCs absent — NPC abilities disabled "
                    + "(the animated enemy keeps its own)");
            return;
        }
        CnpcAbilityEvents.register();
    }
}
