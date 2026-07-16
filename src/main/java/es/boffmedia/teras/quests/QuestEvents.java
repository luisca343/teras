package es.boffmedia.teras.quests;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.quests.model.QuestInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.event.DialogEvent;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.wrapper.PlayerWrapper;

/**
 * CustomNPCs event handlers — port of the 1.16.5 {@code CustomNPCsEvents}.
 *
 * <p>Registered on CustomNPCs' <b>own</b> event bus by {@link QuestBridge}, not via
 * {@code @EventBusSubscriber} (these aren't NeoForge game events). Handlers are static because that
 * bus, like NeoForge's, only picks up static methods when a class is registered.</p>
 *
 * <p>Two 1.16.5 handlers were intentionally dropped: a client-side {@code onReadDialogue} that was a
 * second subscriber on {@code DialogEvent.OpenEvent} doing nothing but logging, and a
 * {@code QuestCompletedEvent} handler whose body only cast the player and then returned.</p>
 */
public final class QuestEvents {
    private QuestEvents() {}

    /**
     * Catalogs the NPC and learns the quest it offers. Runs on dialog open rather than on an NPC
     * update tick because that's the point where the NPC is fully configured — 1.16.5 made the same
     * call for the same reason.
     */
    @SubscribeEvent
    public static void onDialogOpen(DialogEvent.OpenEvent event) {
        try {
            catalogNpc(event.npc, event.dialog);
            learnQuest(event);
        } catch (Exception e) {
            // Never let a bad NPC/dialog break the player's interaction.
            Teras.LOGGER.warn("Failed handling dialog open: {}", e.toString());
        }
    }

    /** Records this NPC against the opened dialog and every dialog its options link to. */
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

    /**
     * Caches the definition of the quest this dialog offers, if the player could actually accept it.
     * {@code canQuestBeAccepted} is the same gate 1.16.5 used, so quests the player can't take don't
     * pollute the cache.
     */
    private static void learnQuest(DialogEvent.OpenEvent event) {
        IQuest quest = event.dialog.getQuest();
        if (quest == null) return;
        if (!(event.player.getMCEntity() instanceof ServerPlayer player)) return;

        PlayerWrapper<?> wrapper = new PlayerWrapper<>(player);
        if (!wrapper.canQuestBeAccepted(quest.getId())) return;

        QuestInfo info = QuestBuilder.definition(quest, event.npc);
        info.setDialogId(event.dialog.getId());
        MisionesStore.put(info);
    }
}
