package es.boffmedia.teras.quests;

import es.boffmedia.teras.Teras;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.event.DialogEvent;
import noppes.npcs.api.handler.data.IDialog;

/**
 * CustomNPCs event handlers — port of the 1.16.5 {@code CustomNPCsEvents}.
 *
 * <p>Registered on CustomNPCs' <b>own</b> event bus by {@link QuestRegistrar}, not via
 * {@code @EventBusSubscriber} (these aren't NeoForge game events). Handlers are static because that
 * bus, like NeoForge's, only picks up static methods when a class is registered.</p>
 *
 * <p>All this does now is keep the {@link NpcCatalog} current: quest and dialog data are read live
 * from CustomNPCs when {@link QuestService} builds the catalog, so there's nothing to cache. The
 * catalog exists because CustomNPCs can tell you a dialog's id but not <i>who says it or where</i> —
 * that only exists on the NPC entity, so it's recorded when players meet one and persisted.</p>
 *
 * <p>Three 1.16.5 handlers were intentionally dropped: a client-side {@code onReadDialogue} that was a
 * second subscriber on {@code DialogEvent.OpenEvent} doing nothing but logging; a
 * {@code QuestCompletedEvent} handler whose body only cast the player and returned; and the
 * {@code misiones.json} quest-definition cache — nothing ever read that file, in the mod or in the
 * Wungill plugin (whose {@code FileHelper.getQuests()} reader had zero callers).</p>
 */
public final class QuestEvents {
    private QuestEvents() {}

    /**
     * Records the NPC against the dialog it's showing. Done on dialog open rather than on an NPC
     * update tick because that's the point where the NPC is fully configured — 1.16.5 made the same
     * call for the same reason.
     */
    @SubscribeEvent
    public static void onDialogOpen(DialogEvent.OpenEvent event) {
        try {
            catalogNpc(event.npc, event.dialog);
        } catch (Exception e) {
            // Never let a bad NPC/dialog break the player's interaction.
            Teras.LOGGER.warn("Failed cataloguing NPC on dialog open: {}", e.toString());
        }
    }

    /** Catalogs this NPC against the opened dialog and every dialog its options link to. */
    private static void catalogNpc(ICustomNpc<?> npc, IDialog dialog) {
        Entity mc = npc.getMCEntity();
        String dimension = mc != null && mc.level() instanceof ServerLevel level
                ? level.dimension().location().toString()
                : "minecraft:overworld";
        String skin = NpcCatalog.extractTextureName(npc.getDisplay().getSkinTexture());
        String uuid = NpcScanner.resolveUuid(npc);

        NpcCatalog.update(dialog.getId(), NpcScanner.build(npc, dialog.getId(), skin, dimension, uuid));
        for (int linkedId : NpcScanner.linkedDialogIds(dialog)) {
            NpcCatalog.update(linkedId, NpcScanner.build(npc, linkedId, skin, dimension, uuid));
        }
        NpcCatalog.saveIfDirty();
    }
}
