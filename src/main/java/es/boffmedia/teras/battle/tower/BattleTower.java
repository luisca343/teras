package es.boffmedia.teras.battle.tower;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.api.BattleProvider;
import es.boffmedia.teras.battle.api.BattleProviders;
import es.boffmedia.teras.battle.config.BattleConfig;
import es.boffmedia.teras.battle.config.BattleConfigLoader;
import es.boffmedia.teras.battle.lifecycle.BattleOutcomeHandler;
import es.boffmedia.teras.util.net.HttpText;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Battle Tower ("Frente Batalla"): a player fights consecutive opponents for a win streak that
 * persists in player NBT. A win prompts continue/pause; a loss resets the streak; {@value #PRIZE_STREAK}
 * wins awards the prize. Each round's opponent is a trainer config loaded from
 * {@code combates/torre/{modality}/}, run through {@link BattleProvider} and observed via
 * {@link BattleOutcomeHandler#addEndListener}.
 */
public final class BattleTower {
    private BattleTower() {}

    private static final int PRIZE_STREAK = 7;
    private static final String KEY_ACTIVE = "teras_torre_activa";
    private static final String KEY_MODALITY = "teras_torre_modalidad";

    private static String streakKey(String modality) {
        return "teras_torre_racha_" + modality;
    }

    public static int streak(ServerPlayer player, String modality) {
        return player.getPersistentData().getInt(streakKey(modality));
    }

    public static void iniciar(ServerPlayer player, String modality) {
        if (!HttpText.isValidIdentifier(modality)) {
            MessageHelper.enviarMensaje(player, "§cModalidad inválida: '" + modality + "'");
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(KEY_ACTIVE)) {
            MessageHelper.enviarMensaje(player, "§cYa estás participando en el Frente Batalla.");
            return;
        }
        data.putBoolean(KEY_ACTIVE, true);
        data.putString(KEY_MODALITY, modality);
        MessageHelper.enviarMensaje(player, "§aFrente Batalla iniciado (modalidad §e" + modality
                + "§a). Racha actual: §e" + streak(player, modality));
        nextRound(player);
    }

    public static void continuar(ServerPlayer player) {
        if (!active(player)) {
            MessageHelper.enviarMensaje(player, "§cNo estás participando en el Frente Batalla.");
            return;
        }
        nextRound(player);
    }

    public static void pausar(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!active(player)) {
            MessageHelper.enviarMensaje(player, "§cNo estás participando en el Frente Batalla.");
            return;
        }
        data.putBoolean(KEY_ACTIVE, false);
        MessageHelper.enviarMensaje(player, "§aHas pausado el Frente Batalla con una racha de §e"
                + streak(player, data.getString(KEY_MODALITY)) + "§a victorias.");
    }

    /** True while the player is in a tower run — some actions (opening the PC) are refused during one. */
    public static boolean isActive(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY_ACTIVE);
    }

    private static boolean active(ServerPlayer player) {
        return isActive(player);
    }

    private static void nextRound(ServerPlayer player) {
        BattleProvider provider = BattleProviders.get();
        if (provider == null) {
            MessageHelper.enviarMensaje(player, "§cNo hay un motor de combate (Pixelmon/Cobblemon) instalado.");
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String modality = player.getPersistentData().getString(KEY_MODALITY);
        MessageHelper.enviarMensaje(player, "§7Preparando el siguiente combate…");

        Teras.EXECUTOR.submit(() -> {
            BattleConfig config = BattleConfigLoader.load(modality, "torre");
            if (config == null) {
                server.execute(() -> MessageHelper.enviarMensaje(player,
                        "§cNo se pudo cargar el combate de la torre '" + modality + "'."));
                return;
            }
            server.execute(() -> {
                BattleOutcomeHandler.addEndListener(player.getUUID(),
                        (endedConfig, won) -> onRoundEnd(player, modality, endedConfig, won));
                try {
                    provider.startConfigBattle(player, config);
                } catch (Exception e) {
                    BattleOutcomeHandler.removeEndListener(player.getUUID());
                    Teras.LOGGER.error("Error starting tower round for '{}'", modality, e);
                    MessageHelper.enviarMensaje(player, "§cError al iniciar el combate de la torre.");
                }
            });
        });
    }

    /**
     * Applies a finished round to the streak, ignoring any battle that isn't the round this listener
     * was registered for — an unrelated fight would otherwise advance or reset the streak.
     */
    private static void onRoundEnd(ServerPlayer player, String roundModality,
                                   BattleConfig endedConfig, boolean won) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(KEY_ACTIVE)) {
            return;
        }
        if (!isThisRound(endedConfig, roundModality)) {
            Teras.LOGGER.debug("Ignoring battle end for '{}' — not the tower round '{}' this listener "
                    + "was registered for", endedConfig == null ? null : endedConfig.getNombreArchivo(),
                    roundModality);
            return;
        }
        String modality = data.getString(KEY_MODALITY);
        String key = streakKey(modality);
        if (!won) {
            data.putInt(key, 0);
            data.putBoolean(KEY_ACTIVE, false);
            MessageHelper.enviarMensaje(player, "§cHas perdido. Tu racha en '" + modality + "' se reinicia.");
            return;
        }
        int streak = data.getInt(key) + 1;
        data.putInt(key, streak);
        if (streak >= PRIZE_STREAK) {
            MessageHelper.enviarMensaje(player, "§6¡Has alcanzado §e" + streak
                    + "§6 victorias en '" + modality + "'! Premio conseguido.");
        } else {
            MessageHelper.enviarMensaje(player, "§aVictoria §e" + streak + "§a. Te faltan §e"
                    + (PRIZE_STREAK - streak) + "§a para el premio.");
        }
        promptNext(player);
    }

    /** True when {@code config} is the tower battle for {@code modality} — folder and id both match. */
    private static boolean isThisRound(BattleConfig config, String modality) {
        return config != null
                && "torre".equals(config.getCarpeta())
                && modality != null && modality.equals(config.getNombreArchivo());
    }

    /** Sends the clickable [Siguiente Combate] / [Salir] prompt. */
    private static void promptNext(ServerPlayer player) {
        Component next = Component.literal("§a[Siguiente Combate]").withStyle(s -> s.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/frentebatalla continuar")));
        Component quit = Component.literal("§c[Salir]").withStyle(s -> s.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/frentebatalla pausar")));
        player.sendSystemMessage(Component.literal("§a¿Qué deseas hacer? ")
                .append(next).append(Component.literal(" ")).append(quit));
    }
}
