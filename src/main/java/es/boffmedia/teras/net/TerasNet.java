package es.boffmedia.teras.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge payload networking for SmartRotom, replacing the 1.16.5 Forge {@code SimpleChannel}
 * ({@code Messages}). Only the SmartRotom-relevant, self-contained messages are ported here.
 *
 * <p>Server-side handlers reference only common/server classes so this class is safe to load on a
 * dedicated server. The single client-side handler is dispatched through a lambda to a client-only
 * class, so its client-only dependencies (MCEF/JCEF) are never classloaded server-side.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TerasNet {
    private TerasNet() {}

    private static final Gson GSON = new Gson();
    private static final int CHAT_PERMISSION_LEVEL = 2; // OP/gamemaster, matches 1.16.5

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ChatMessagePayload.TYPE, ChatMessagePayload.STREAM_CODEC, TerasNet::handleChat);
        registrar.playToServer(UserDataRequestPayload.TYPE, UserDataRequestPayload.STREAM_CODEC, TerasNet::handleUserDataRequest);
        // Client-only body is isolated behind a lambda -> client class (never loaded on the server).
        registrar.playToClient(McefResponsePayload.TYPE, McefResponsePayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onMcefResponse(payload, context));
    }

    // ---- Client-side send helpers (called from QueryHelper on the client) ----

    public static void sendChatToServer(String json) {
        PacketDistributor.sendToServer(new ChatMessagePayload(json));
    }

    public static void requestUserData() {
        PacketDistributor.sendToServer(UserDataRequestPayload.INSTANCE);
    }

    // ---- Server-side handlers ----

    private static void handleChat(ChatMessagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            // AUTHORITY CHECK: only privileged players may broadcast a server-wide system message.
            if (!sp.hasPermissions(CHAT_PERMISSION_LEVEL)) {
                Teras.LOGGER.warn("Player {} attempted to broadcast a chat message without permission",
                        sp.getGameProfile().getName());
                return;
            }
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            try {
                JsonObject json = GSON.fromJson(payload.json(), JsonObject.class);
                String message = json.get("message").getAsString();
                server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
            } catch (Exception e) {
                Teras.LOGGER.error("Error parsing chat message: {}", payload.json(), e);
                sp.sendSystemMessage(Component.literal("Error parsing message"));
            }
        });
    }

    private static void handleUserDataRequest(UserDataRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            // Authoritative, server-known player data. Mod-specific fields (missions, economy, …)
            // are added here as those systems are ported.
            JsonObject json = new JsonObject();
            json.addProperty("status", "ok");
            json.addProperty("uuid", sp.getUUID().toString());
            json.addProperty("name", sp.getGameProfile().getName());
            json.addProperty("op", sp.hasPermissions(CHAT_PERMISSION_LEVEL));
            PacketDistributor.sendToPlayer(sp, new McefResponsePayload(GSON.toJson(json)));
        });
    }
}
