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
        registrar.playToServer(SpawnsRequestPayload.TYPE, SpawnsRequestPayload.STREAM_CODEC, TerasNet::handleSpawnsRequest);
        registrar.playToServer(MisionesRequestPayload.TYPE, MisionesRequestPayload.STREAM_CODEC, TerasNet::handleMisionesRequest);
        // Client-only bodies are isolated behind lambdas -> client class (never loaded on the server).
        registrar.playToClient(McefResponsePayload.TYPE, McefResponsePayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onMcefResponse(payload, context));
        registrar.playToClient(ServerConfigPayload.TYPE, ServerConfigPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onServerConfig(payload, context));
    }

    // ---- Server-side send helpers ----

    /** Hands the joining player this server's config; see {@link ServerConfigPayload}. */
    public static void sendConfig(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ServerConfigPayload(
                es.boffmedia.teras.util.TerasConfig.getHome()));
    }

    // ---- Client-side send helpers (called from QueryHelper on the client) ----

    public static void sendChatToServer(String json) {
        PacketDistributor.sendToServer(new ChatMessagePayload(json));
    }

    public static void requestUserData(long requestId) {
        PacketDistributor.sendToServer(new UserDataRequestPayload(requestId));
    }

    public static void requestSpawns(long requestId) {
        PacketDistributor.sendToServer(new SpawnsRequestPayload(requestId));
    }

    public static void requestMisiones(long requestId) {
        PacketDistributor.sendToServer(new MisionesRequestPayload(requestId));
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
            // Authoritative, server-known player data (matches the 1.16.5 getUserData contract the
            // SmartRotom web reads: uuid/username/world/x/y/z). `world` is THIS server's config id —
            // it's how the web confirms the player is on the right server. Mod-specific fields
            // (missions, economy, …) are added here as those systems are ported.
            JsonObject json = new JsonObject();
            json.addProperty("status", "ok");
            json.addProperty("uuid", sp.getUUID().toString());
            json.addProperty("username", sp.getGameProfile().getName());
            json.addProperty("world", es.boffmedia.teras.util.TerasConfig.getId());
            json.addProperty("x", sp.getX());
            json.addProperty("y", sp.getY());
            json.addProperty("z", sp.getZ());
            json.addProperty("op", sp.hasPermissions(CHAT_PERMISSION_LEVEL));
            PacketDistributor.sendToPlayer(sp, new McefResponsePayload(payload.requestId(), GSON.toJson(json)));
        });
    }

    private static void handleSpawnsRequest(SpawnsRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            // Port of 1.16.5 SMessageCheckSpawns: scan the player's Pixelmon spawner and reply with a
            // PokedexSpawnChance[] JSON. SpawnScanner is the only Pixelmon-coupled class; guard on the
            // mod being present so it is never classloaded when Pixelmon is absent (e.g. the
            // compileOnly dev runtime) — otherwise a NoClassDefFoundError would leave the JS callback
            // hanging. We always send a reply (even "[]") so the round-trip resolves.
            String spawnsJson = "[]";
            if (net.neoforged.fml.ModList.get().isLoaded("pixelmon")) {
                spawnsJson = es.boffmedia.teras.pixelmon.SpawnScanner.scanAsJson(sp);
            } else {
                Teras.LOGGER.warn("getSpawns requested by {} but Pixelmon is not loaded on this "
                        + "server; returning []. (Pixelmon is compileOnly — add it to the runtime, and "
                        + "note it needs NeoForge >= 21.1.200.)", sp.getGameProfile().getName());
            }
            PacketDistributor.sendToPlayer(sp, new McefResponsePayload(payload.requestId(), spawnsJson));
        });
    }

    private static void handleMisionesRequest(MisionesRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            // The in-game page's route to quests — the one needing no config and no network, so it is
            // what single-player and LAN worlds use (the HTTP API is off by default and has no backend
            // there to merge the catalog with the player's progress). Returns the MERGED shape the
            // backend would otherwise assemble, so the board reads one shape on both paths.
            //
            // AUTHORITY: the player comes from the connection, never from the request, so a client can
            // only ever read its own quests. That's why this payload carries no uuid, unlike the HTTP
            // route — which is server-to-server and gated by the bearer token instead.
            //
            // Same isolation as getSpawns above: QuestJson touches CustomNPCs, so it is only
            // referenced behind the ModList check and never classloaded when the mod is absent. We
            // always reply (even empty) so the JS callback resolves instead of hanging — which is
            // exactly how the 1.16.5 getMisiones failed.
            String json = es.boffmedia.teras.quests.QuestJson.EMPTY_MERGED;
            if (es.boffmedia.teras.quests.QuestBridge.isAvailable()) {
                json = es.boffmedia.teras.quests.QuestJson.mergedJson(sp.getUUID());
            } else {
                Teras.LOGGER.warn("getMisiones requested by {} but CustomNPCs is not loaded on this "
                        + "server; returning an empty quest list.", sp.getGameProfile().getName());
            }
            PacketDistributor.sendToPlayer(sp, new McefResponsePayload(payload.requestId(), json));
        });
    }
}
