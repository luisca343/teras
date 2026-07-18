package es.boffmedia.teras.voice;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import es.boffmedia.teras.Teras;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Simple Voice Chat plugin: powers the SmartRotom ChatApp's in-game voice calls.
 *
 * <p>SVC discovers this class by scanning mod jars for {@link ForgeVoicechatPlugin}, so it only loads
 * when SVC is present and may import {@code de.maxhenkel.voicechat.api.*} freely. Everything else in
 * Teras reaches it through {@link #startCall}/{@link #endCall}, only inside a
 * {@code ModList.isLoaded("voicechat")} branch, so a server without SVC never loads it.</p>
 *
 * <p>Participants resolve-or-create one hidden, non-persistent group keyed on the {@code chatId}:
 * hidden keeps it out of SVC's group screen, keying on the chat means a joiner can only ever land in a
 * Teras call group rather than someone's private one, and non-persistent lets SVC drop it once empty.
 * Failures return a reason instead of throwing, so the page's promise never hangs.</p>
 */
@ForgeVoicechatPlugin
public class TerasVoicechatPlugin implements VoicechatPlugin {

    /** The name shown for a call group. SVC validates names against {@code ^[^\p{C}\s][^\p{C}]{0,23}$}. */
    private static final String GROUP_NAME = "Llamada";

    /** Set on {@link VoicechatServerStartedEvent}, cleared on stop. {@code volatile}: written on SVC's
     *  startup thread, read on the server thread from {@link #startCall}/{@link #endCall}. */
    private static volatile VoicechatServerApi serverApi;

    @Override
    public String getPluginId() {
        return Teras.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        Teras.LOGGER.info("Teras SimpleVoiceChat plugin discovered; registering voice events");
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStopped);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        serverApi = event.getVoicechat();
        Teras.LOGGER.info("SimpleVoiceChat server API acquired; ChatApp calls enabled");
    }

    private void onServerStopped(VoicechatServerStoppedEvent event) {
        serverApi = null;
    }

    /**
     * Puts {@code player} into the (hidden, non-persistent) voice group for {@code chatId}, creating it if
     * this is the first participant. Must run on the server thread.
     *
     * @return {@code null} on success, or a short machine reason on failure (see {@link CallStatus}).
     */
    public static String startCall(ServerPlayer player, String chatId) {
        VoicechatServerApi api = serverApi;
        if (api == null) {
            Teras.LOGGER.warn("Call: cannot place {} in chat {} — no SimpleVoiceChat server API "
                    + "(voice server not started, or the Teras plugin was not discovered)",
                    player.getGameProfile().getName(), chatId);
            return CallStatus.SERVER_UNAVAILABLE;
        }
        VoicechatConnection connection = api.getConnectionOf(player.getUUID());
        if (connection == null) {
            Teras.LOGGER.warn("Call: cannot place {} in chat {} — no SimpleVoiceChat connection "
                    + "(client mod missing, or voice not connected this session)",
                    player.getGameProfile().getName(), chatId);
            return CallStatus.NOT_INSTALLED;
        }
        try {
            UUID groupId = groupIdFor(chatId);
            Group group = findGroup(api, groupId);
            boolean created = group == null;
            if (created) {
                // build() only registers persistent groups; this one stays invisible to findGroup
                // until the setGroup below adds it. Keep the two steps together.
                group = api.groupBuilder()
                        .setId(groupId)
                        .setName(GROUP_NAME)
                        .setPersistent(false)
                        .setHidden(true)
                        .setType(Group.Type.NORMAL)
                        .build();
            }
            connection.setGroup(group);
            Teras.LOGGER.info("Call: placed {} in voice group {} (chat {}, {}) — connected={} disabled={}",
                    player.getGameProfile().getName(), groupId, chatId,
                    created ? "created" : "existing", connection.isConnected(), connection.isDisabled());
            return null;
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to start call for {} (chat {})", player.getGameProfile().getName(), chatId, e);
            return CallStatus.ERROR;
        }
    }

    /**
     * Removes {@code player} from any voice group. Idempotent and lenient: leaving must never block the
     * page's UI cleanup, so a missing API or connection is treated as already-left.
     *
     * @return {@code null} on success (including the nothing-to-do cases), or a reason on real failure.
     */
    public static String endCall(ServerPlayer player) {
        VoicechatServerApi api = serverApi;
        if (api == null) {
            return null;
        }
        VoicechatConnection connection = api.getConnectionOf(player.getUUID());
        if (connection == null) {
            return null;
        }
        try {
            connection.setGroup(null);
            Teras.LOGGER.info("Call: removed {} from any voice group", player.getGameProfile().getName());
            return null;
        } catch (Exception e) {
            Teras.LOGGER.error("Failed to end call for {}", player.getGameProfile().getName(), e);
            return CallStatus.ERROR;
        }
    }

    /**
     * The live group with {@code groupId}, or {@code null}.
     *
     * <p>Not {@code api.getGroup(id)}: that wraps the lookup unconditionally, so a missing group comes
     * back non-null with a null delegate and NPEs on every call, including inside {@code setGroup}.</p>
     */
    private static Group findGroup(VoicechatServerApi api, UUID groupId) {
        for (Group group : api.getGroups()) {
            if (groupId.equals(group.getId())) {
                return group;
            }
        }
        return null;
    }

    /** Deterministic, stable group id for a chat, so every participant resolves the same group. */
    private static UUID groupIdFor(String chatId) {
        return UUID.nameUUIDFromBytes(("teras-call:" + chatId).getBytes(StandardCharsets.UTF_8));
    }

    /** Machine reasons returned to the network layer, which maps them to a page-facing reply. */
    public static final class CallStatus {
        private CallStatus() {}

        /** SVC is installed but its server has not started (or has stopped). */
        public static final String SERVER_UNAVAILABLE = "voicechat_server_unavailable";
        /** The player has no SVC client connection — they cannot be placed in a voice group. */
        public static final String NOT_INSTALLED = "voicechat_not_installed";
        /** An unexpected error; details are logged server-side. */
        public static final String ERROR = "voicechat_error";
    }
}
