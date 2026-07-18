package es.boffmedia.teras.battle.lifecycle;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.config.BattleConfig;
import es.boffmedia.teras.battle.model.Recompensa;
import es.boffmedia.teras.battle.model.TeamMember;
import es.boffmedia.teras.util.data.TerasScoreboard;
import es.boffmedia.teras.util.net.SmartRotomService;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine-neutral battle-end handling: awards the config outcome (scoreboard objective, item rewards,
 * messages) and posts money + achievements to SmartRotom via {@link SmartRotomService}. Reward item
 * {@code nbt} display name/lore is converted to 1.21 data components (see {@link #applyLegacyNbt}).
 */
public final class BattleOutcomeHandler {
    private BattleOutcomeHandler() {}

    /**
     * One-shot per-player battle-end listeners, run after the standard handling (used by the battle
     * tower). A listener is told <b>which</b> battle ended because the registration outlives the battle
     * it was made for — an unrelated battle started in between would otherwise fire it.
     */
    private static final java.util.Map<UUID, EndListener> END_LISTENERS = new ConcurrentHashMap<>();

    /** Notified when a config battle ends, with the config that ended. */
    @FunctionalInterface
    public interface EndListener {
        void onBattleEnd(BattleConfig config, boolean playerWon);
    }

    public static void addEndListener(UUID playerId, EndListener listener) {
        END_LISTENERS.put(playerId, listener);
    }

    public static void removeEndListener(UUID playerId) {
        END_LISTENERS.remove(playerId);
    }

    public static void onConfigBattleEnd(ServerPlayer player, BattleConfig config, boolean playerWon) {
        onConfigBattleEnd(player, config, playerWon, null, List.of(), List.of());
    }

    public static void onConfigBattleEnd(ServerPlayer player, BattleConfig config, boolean playerWon,
                                         String replay, List<TeamMember> team1, List<TeamMember> team2) {
        if (config == null) {
            return;
        }
        try {
            if (playerWon) {
                onVictory(player, config);
            } else {
                MessageHelper.enviarMensaje(player, "§cHas perdido el combate contra " + trainerName(config));
            }
            reportBattle(player, config, playerWon, replay, team1, team2);
        } catch (Exception e) {
            Teras.LOGGER.error("Error handling battle outcome for '{}'", config.getNombreArchivo(), e);
        } finally {
            EndListener listener = END_LISTENERS.remove(player.getUUID());
            if (listener != null) {
                try {
                    listener.onBattleEnd(config, playerWon);
                } catch (Exception e) {
                    Teras.LOGGER.error("Battle-end listener failed", e);
                }
            }
        }
    }

    /** Reports the battle to SmartRotom when the config names a {@code logro}. */
    private static void reportBattle(ServerPlayer player, BattleConfig config, boolean playerWon,
                                     String replay, List<TeamMember> team1, List<TeamMember> team2) {
        String logro = config.getLogro();
        if (logro == null || logro.isBlank()) {
            return;
        }
        if (replay == null || replay.isBlank()) {
            Teras.LOGGER.warn("Battle '{}': replay log is empty — SmartRotom will reject the report",
                    config.getNombreArchivo());
        }
        if (team1.isEmpty() || team2.isEmpty()) {
            Teras.LOGGER.warn("Battle '{}': team capture is empty — SmartRotom will reject the report",
                    config.getNombreArchivo());
        }
        SmartRotomService.saveBattle(new SmartRotomService.BattleReport(
                player.getUUID().toString(), logro, playerWon,
                player.getName().getString(), trainerName(config), team1, team2, replay));
    }

    private static void onVictory(ServerPlayer player, BattleConfig config) {
        if (config.getNombreObjetivo() != null) {
            TerasScoreboard.set(player, config.getNombreObjetivo(), 1);
        }

        MessageHelper.enviarMensaje(player, "§aHas ganado el combate contra " + trainerName(config));

        if (config.getDinero() > 0) {
            SmartRotomService.defeatTrainer(player.getUUID(), config.getDinero());
            MessageHelper.enviarMensaje(player, "§aObtienes " + config.getDinero() + " Pokédólares");
        }

        grantRewards(player, config);
    }

    private static void grantRewards(ServerPlayer player, BattleConfig config) {
        if (config.getRecompensas() == null) {
            return;
        }
        for (Recompensa recompensa : config.getRecompensas()) {
            if (recompensa == null || recompensa.getObjeto() == null) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(recompensa.getObjeto());
            if (id == null) {
                Teras.LOGGER.warn("Reward with invalid item id '{}' in config '{}'",
                        recompensa.getObjeto(), config.getNombreArchivo());
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                Teras.LOGGER.warn("Reward item '{}' not found (config '{}')", id, config.getNombreArchivo());
                continue;
            }
            int count = Math.max(1, recompensa.getCantidad());
            ItemStack stack = new ItemStack(item, count);
            if (recompensa.getNbt() != null && !recompensa.getNbt().isBlank()) {
                applyLegacyNbt(stack, recompensa.getNbt(), player.level().registryAccess());
            }
            ItemHandlerHelper.giveItemToPlayer(player, stack);
        }
    }

    /** Applies an NBT string's {@code display} name/lore (JSON text components) as 1.21 data components.
     *  Other tags aren't mapped; a malformed string is logged and skipped. */
    private static void applyLegacyNbt(ItemStack stack, String nbt, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = TagParser.parseTag(nbt);
            if (!tag.contains("display", Tag.TAG_COMPOUND)) {
                return;
            }
            CompoundTag display = tag.getCompound("display");
            if (display.contains("Name", Tag.TAG_STRING)) {
                Component name = Component.Serializer.fromJson(display.getString("Name"), registries);
                if (name != null) {
                    stack.set(DataComponents.CUSTOM_NAME, name);
                }
            }
            if (display.contains("Lore", Tag.TAG_LIST)) {
                ListTag loreTag = display.getList("Lore", Tag.TAG_STRING);
                List<Component> lore = new ArrayList<>();
                for (int i = 0; i < loreTag.size(); i++) {
                    Component line = Component.Serializer.fromJson(loreTag.getString(i), registries);
                    if (line != null) {
                        lore.add(line);
                    }
                }
                if (!lore.isEmpty()) {
                    stack.set(DataComponents.LORE, new ItemLore(lore));
                }
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Reward NBT could not be applied ('{}'): {}", nbt, e.toString());
        }
    }

    private static String trainerName(BattleConfig config) {
        return config.getNombre() == null || config.getNombre().isBlank() ? "el rival" : config.getNombre();
    }
}
