package es.boffmedia.teras.quests;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.quests.model.NpcData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.constants.EntitiesType;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IDialogOption;
import noppes.npcs.controllers.data.DialogOption;

import java.util.Map;
import java.util.List;

/**
 * Sweeps every loaded world for CustomNPCs and folds what it finds into the {@link NpcCatalog}.
 * Port of the 1.16.5 {@code UpdateNPCs}, minus its DTO role — the POST body now lives in
 * {@code SmartRotomService}, so this class only scans.
 *
 * <p>Touches CustomNPCs classes directly, so it must only be reached behind a
 * {@code ModList.isLoaded("customnpcs")} check (see {@link QuestBridge}).</p>
 */
public final class NpcScanner {
    private NpcScanner() {}

    /** How many dialog slots an NPC has in CustomNPCs; the 1.16.5 scan used the same fixed bound. */
    private static final int DIALOG_SLOTS = 12;

    /**
     * Scans all levels, updates the catalog with live positions, persists it, and returns the
     * <b>whole</b> catalog — including NPCs from previous sessions that aren't currently loaded,
     * which is the 1.16.5 behaviour the SmartRotom web expects.
     */
    public static Map<Integer, List<NpcData>> scanAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return NpcCatalog.getCatalog();

        NpcAPI api = NpcAPI.Instance();
        int scanned = 0;
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            IWorld world = api.getIWorld(level);
            for (IEntity<?> entity : world.getAllEntities(EntitiesType.NPC)) {
                if (!(entity instanceof ICustomNpc<?> npc)) continue;
                catalogNpc(npc, dimension);
                scanned++;
            }
        }
        NpcCatalog.saveIfDirty();
        Teras.LOGGER.info("NpcScanner: scanned {} NPCs across all levels", scanned);
        return NpcCatalog.getCatalog();
    }

    /** Catalogs every dialog on {@code npc}, plus the dialogs its options link to. */
    private static void catalogNpc(ICustomNpc<?> npc, String dimension) {
        String skin = NpcCatalog.extractTextureName(npc.getDisplay().getSkinTexture());
        String uuid = resolveUuid(npc);
        for (int slot = 0; slot < DIALOG_SLOTS; slot++) {
            IDialog dialog = npc.getDialog(slot);
            if (dialog == null) continue;
            NpcCatalog.update(dialog.getId(), build(npc, dialog.getId(), skin, dimension, uuid));
            for (int linkedId : linkedDialogIds(dialog)) {
                NpcCatalog.update(linkedId, build(npc, linkedId, skin, dimension, uuid));
            }
        }
    }

    /**
     * The dialog ids an option jumps to. {@code IDialogOption} exposes no dialog id, so this reads the
     * public field off the concrete {@code DialogOption} — the same cast the 1.16.5 code used.
     */
    static int[] linkedDialogIds(IDialog dialog) {
        List<IDialogOption> options = dialog.getOptions();
        return options.stream()
                .filter(DialogOption.class::isInstance)
                .mapToInt(o -> ((DialogOption) o).dialogId)
                .toArray();
    }

    static NpcData build(ICustomNpc<?> npc, int dialogId, String skin, String dimension, String uuid) {
        return new NpcData(npc.getDisplay().getName(), dialogId, skin,
                npc.getX(), npc.getY(), npc.getZ(), dimension, uuid);
    }

    /** CustomNPCs' own uuid where present, else the entity's — the catalog dedups on this. */
    static String resolveUuid(ICustomNpc<?> npc) {
        String uuid = npc.getUUID();
        if (uuid == null || uuid.isEmpty()) {
            Entity mc = npc.getMCEntity();
            uuid = mc != null ? mc.getStringUUID() : "";
        }
        return uuid;
    }
}
