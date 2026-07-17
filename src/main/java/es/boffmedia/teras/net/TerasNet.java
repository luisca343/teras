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

    /**
     * Server-side bound on a dex scan, squared: the item's range plus slack for movement between the
     * client's raytrace and the packet landing.
     */
    private static final double MAX_DEX_SCAN_DISTANCE_SQR =
            (es.boffmedia.teras.items.SmartRotom.SCAN_RANGE + 8.0)
                    * (es.boffmedia.teras.items.SmartRotom.SCAN_RANGE + 8.0);

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ChatMessagePayload.TYPE, ChatMessagePayload.STREAM_CODEC, TerasNet::handleChat);
        registrar.playToServer(UserDataRequestPayload.TYPE, UserDataRequestPayload.STREAM_CODEC, TerasNet::handleUserDataRequest);
        registrar.playToServer(SpawnsRequestPayload.TYPE, SpawnsRequestPayload.STREAM_CODEC, TerasNet::handleSpawnsRequest);
        registrar.playToServer(MisionesRequestPayload.TYPE, MisionesRequestPayload.STREAM_CODEC, TerasNet::handleMisionesRequest);
        registrar.playToServer(DarCajaPayload.TYPE, DarCajaPayload.STREAM_CODEC, TerasNet::handleDarCajaRequest);
        registrar.playToServer(DexRegisterPayload.TYPE, DexRegisterPayload.STREAM_CODEC, TerasNet::handleDexRegister);
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

    /** Asks the server to redeem what this player is owed from {@code source}; see {@link DarCajaPayload}. */
    public static void requestDarCaja(long requestId, String source) {
        PacketDistributor.sendToServer(new DarCajaPayload(requestId, source));
    }

    /** Asks the server to register the Pokémon the player just scanned; see {@link DexRegisterPayload}. */
    public static void registerDex(int entityId) {
        PacketDistributor.sendToServer(new DexRegisterPayload(entityId));
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
            // Which darCaja contract this jar speaks. The page branches on it: present -> it sends a
            // {source} and lets the backend pick the items; ABSENT -> it falls back to 1.16.5's
            // {objetos}, where the page names them. Both populations exist at once (the page redeploys
            // instantly, jars update per player), so this must be advertised by the jar rather than
            // flagged at build time — and absent has to keep meaning legacy.
            //
            // This must ship in the same build as handleDarCajaRequest. Advertise without implementing
            // and the page sends {source} to a handler that cannot serve it; implement without
            // advertising and it sends {objetos} after its legacy path already spent the rewards.
            // Either way the player loses them.
            json.addProperty("cajaProtocol", "source");
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

    /**
     * Redeems what the sender is owed from {@code source} and gives it to them as chests.
     *
     * <p>AUTHORITY: the uuid comes off the connection, never the page — that is the whole security
     * boundary. The page contributes only a source string; the backend picks the items. 1.16.5 took
     * the item list from the client and granted it, which is what let a modified client mint anything.
     * A blank or malformed source fails the request: there is no "everything owed", because the
     * phrase is not well-defined across sources that disagree about what {@code used} means.</p>
     *
     * <p>The backend <b>spends before we deliver</b>, so a disconnect between the two loses the items
     * (DARCAJA.md §7 — two-phase reserve/confirm is the follow-up). The audit line below is the only
     * record when that happens.</p>
     */
    private static void handleDarCajaRequest(DarCajaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            String source = payload.source();
            if (!es.boffmedia.teras.util.net.HttpText.isValidIdentifier(source)) {
                Teras.LOGGER.warn("DarCaja from {} with invalid source '{}'; granting nothing",
                        sp.getGameProfile().getName(), source);
                sendDarCajaReply(sp, payload.requestId(), errorJson("invalid source"));
                return;
            }
            java.util.UUID uuid = sp.getUUID();
            // The claim blocks on HTTP; on this thread it would stall the tick. Hop out, then back:
            // the grant touches the player's inventory and must land on the server thread again.
            Teras.EXECUTOR.execute(() -> {
                java.util.List<es.boffmedia.teras.model.world.ObjetoMC> objetos =
                        es.boffmedia.teras.util.net.SmartRotomService.claimCaja(uuid, source);
                server.execute(() -> deliverCaja(server, uuid, source, payload.requestId(), objetos));
            });
        });
    }

    /** Server thread: hands over what the backend granted, and says so exactly once. */
    private static void deliverCaja(MinecraftServer server, java.util.UUID uuid, String source,
                                    long requestId,
                                    java.util.List<es.boffmedia.teras.model.world.ObjetoMC> objetos) {
        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
        if (objetos == null) {
            Teras.LOGGER.warn("DarCaja: claim failed for {} (source '{}'); granting nothing", uuid, source);
            if (sp != null) {
                sendDarCajaReply(sp, requestId, errorJson("claim failed"));
            }
            return;
        }
        if (objetos.isEmpty()) {
            Teras.LOGGER.info("DarCaja: {} is owed nothing from '{}'", uuid, source);
            if (sp != null) {
                sendDarCajaReply(sp, requestId, okJson(0));
            }
            return;
        }
        if (sp == null) {
            // The backend has already spent these and we have nobody to give them to. Nothing here
            // redelivers them — this line is the only trace they existed.
            Teras.LOGGER.error("DarCaja: {} disconnected before delivery; {} item(s) from '{}' were "
                    + "SPENT AND LOST: {}", uuid, objetos.size(), source, objetos);
            return;
        }
        es.boffmedia.teras.util.ChestCreationHelper.createAndGiveChests(sp, objetos);
        Teras.LOGGER.info("DarCaja: granted {} item(s) to {} ({}) from '{}': {}",
                objetos.size(), sp.getGameProfile().getName(), uuid, source, objetos);
        sendDarCajaReply(sp, requestId, okJson(objetos.size()));
    }

    private static String okJson(int granted) {
        JsonObject json = new JsonObject();
        json.addProperty("status", "ok");
        json.addProperty("objetos", granted);
        return GSON.toJson(json);
    }

    private static String errorJson(String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("status", "error");
        json.addProperty("reason", reason);
        return GSON.toJson(json);
    }

    private static void sendDarCajaReply(ServerPlayer sp, long requestId, String json) {
        PacketDistributor.sendToPlayer(sp, new McefResponsePayload(requestId, json));
    }

    /**
     * Registers a SmartRotom-scanned Pokémon as seen in the sender's Pokédex.
     *
     * <p>AUTHORITY: the player comes from the connection, so a client can only write its own dex, and
     * the species is read off the server's own entity — after checking it is in the player's level and
     * within scan range, without which any loaded Pokémon on the server could be registered.</p>
     *
     * <p>No reply: the page was already opened from the client's own scan. The backend POST hangs off
     * the engine's dex-changed event instead (see {@code docs/DEX.md}).</p>
     */
    private static void handleDexRegister(DexRegisterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            es.boffmedia.teras.dex.api.DexProvider provider = es.boffmedia.teras.dex.api.DexProviders.get();
            if (provider == null) {
                Teras.LOGGER.warn("Dex registration from {} but no Pokémon engine is installed; ignoring",
                        sp.getGameProfile().getName());
                return;
            }
            net.minecraft.world.entity.Entity entity = sp.level().getEntity(payload.entityId());
            if (entity == null || entity.isRemoved()) {
                return;
            }
            double distanceSqr = entity.distanceToSqr(sp);
            if (distanceSqr > MAX_DEX_SCAN_DISTANCE_SQR) {
                Teras.LOGGER.warn("Dex registration from {} for an entity {} blocks away (max {}); ignoring",
                        sp.getGameProfile().getName(),
                        String.format("%.1f", Math.sqrt(distanceSqr)),
                        String.format("%.0f", Math.sqrt(MAX_DEX_SCAN_DISTANCE_SQR)));
                return;
            }
            provider.markSeen(sp, entity);
        });
    }
}
