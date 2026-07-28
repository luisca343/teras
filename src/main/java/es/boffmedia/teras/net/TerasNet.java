package es.boffmedia.teras.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.minecraft.core.BlockPos;
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
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class TerasNet {
    private TerasNet() {}

    private static final Gson GSON = new Gson();
    private static final int ADMIN_PERMISSION_LEVEL = 2; // OP/gamemaster, matches 1.16.5

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
        registrar.playToServer(FrameConfigPayload.TYPE, FrameConfigPayload.STREAM_CODEC, TerasNet::handleFrameConfig);
        registrar.playToServer(FramePlaybackPayload.TYPE, FramePlaybackPayload.STREAM_CODEC, TerasNet::handleFramePlayback);
        registrar.playToServer(SetCallPayload.TYPE, SetCallPayload.STREAM_CODEC, TerasNet::handleSetCall);
        registrar.playToServer(LeaveCallPayload.TYPE, LeaveCallPayload.STREAM_CODEC, TerasNet::handleLeaveCall);
        registrar.playToServer(OpenPCPayload.TYPE, OpenPCPayload.STREAM_CODEC, TerasNet::handleOpenPC);
        registrar.playToServer(DodgePayload.TYPE, DodgePayload.STREAM_CODEC, TerasNet::handleDodge);
        // Client-only bodies are isolated behind lambdas -> client class (never loaded on the server).
        registrar.playToClient(McefResponsePayload.TYPE, McefResponsePayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onMcefResponse(payload, context));
        registrar.playToClient(ServerConfigPayload.TYPE, ServerConfigPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onServerConfig(payload, context));
        registrar.playToClient(StorageChangedPayload.TYPE, StorageChangedPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onStorageChanged(payload, context));
        registrar.playToClient(RegionBannerPayload.TYPE, RegionBannerPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onRegionBanner(payload, context));
        registrar.playToClient(RegionSyncPayload.TYPE, RegionSyncPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onRegionSync(payload, context));
        registrar.playToClient(GpsPayload.TYPE, GpsPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onGps(payload, context));
        registrar.playToClient(RaceHudPayload.TYPE, RaceHudPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onRaceHud(payload, context));
        registrar.playToClient(DungeonMapPayload.TYPE, DungeonMapPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onDungeonMap(payload, context));
        registrar.playToClient(DungeonWalletPayload.TYPE, DungeonWalletPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onDungeonWallet(payload, context));
        registrar.playToClient(CombatStatsPayload.TYPE, CombatStatsPayload.STREAM_CODEC,
                (payload, context) -> es.boffmedia.teras.client.ClientNetHandler.onCombatStats(payload, context));
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
    public static void requestDarCaja(long requestId, String source, java.util.List<Integer> ids) {
        PacketDistributor.sendToServer(new DarCajaPayload(requestId, source, ids));
    }

    /** Asks the server to register the Pokémon the player just scanned; see {@link DexRegisterPayload}. */
    public static void registerDex(int entityId) {
        PacketDistributor.sendToServer(new DexRegisterPayload(entityId));
    }

    /** Asks the server to place this player in the voice group for {@code chatId}; see {@link SetCallPayload}. */
    public static void requestSetCall(long requestId, String chatId) {
        PacketDistributor.sendToServer(new SetCallPayload(requestId, chatId));
    }

    /** Asks the server to remove this player from any voice group; see {@link LeaveCallPayload}. */
    public static void requestLeaveCall(long requestId) {
        PacketDistributor.sendToServer(new LeaveCallPayload(requestId));
    }

    /** Asks the server to open this player's PC; see {@link OpenPCPayload}. */
    public static void requestOpenPC(long requestId) {
        PacketDistributor.sendToServer(new OpenPCPayload(requestId));
    }

    /** Asks the server to roll; see {@link DodgePayload}. */
    public static void sendDodge(float forward, float left) {
        PacketDistributor.sendToServer(new DodgePayload(forward, left));
    }

    /** Sends a picture frame's edited configuration to the server; see {@link FrameConfigPayload}. */
    public static void sendFrameConfig(FrameConfigPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Sends a live playback command (play/pause/stop/seek) for a frame; see {@link FramePlaybackPayload}. */
    public static void sendFramePlayback(FramePlaybackPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    // ---- Server-side handlers ----

    private static void handleChat(ChatMessagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            // AUTHORITY CHECK: only privileged players may broadcast a server-wide system message.
            if (!sp.hasPermissions(ADMIN_PERMISSION_LEVEL)) {
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
            json.addProperty("op", sp.hasPermissions(ADMIN_PERMISSION_LEVEL));
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
            PacketDistributor.sendToPlayer(sp, McefResponsePayload.ok(payload.requestId(), GSON.toJson(json)));
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
            PacketDistributor.sendToPlayer(sp, McefResponsePayload.ok(payload.requestId(), spawnsJson));
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
            PacketDistributor.sendToPlayer(sp, McefResponsePayload.ok(payload.requestId(), json));
        });
    }

    /**
     * Redeems what the sender is owed from {@code source} (narrowed to {@code payload.ids()}).
     *
     * <p>AUTHORITY: the uuid comes off the connection, never the page — that is the whole security
     * boundary. The page contributes only a source string and a row-id selector; the backend picks the
     * rewards. 1.16.5 took the item list from the client and granted it, which is what let a modified
     * client mint anything. A blank or malformed source fails the request: there is no "everything
     * owed", because the phrase is not well-defined across sources that disagree about what
     * {@code used} means.</p>
     *
     * <p>The two-phase delivery itself lives in {@code caja.CajaDeliveryService}.</p>
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
                replyError(sp, payload.requestId(), "invalid source");
                return;
            }
            es.boffmedia.teras.caja.ServerCajaRuntime.claim(
                    server, sp.getUUID(), source, payload.ids(), payload.requestId());
        });
    }

    /** Failure reply: rejects the page's promise via onFailure, so a failed claim cannot read as done. */
    private static void replyError(ServerPlayer sp, long requestId, String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("status", "error");
        json.addProperty("reason", reason);
        PacketDistributor.sendToPlayer(sp, McefResponsePayload.error(requestId, GSON.toJson(json)));
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

    /** SimpleVoiceChat's mod id; guards every entry into the SVC-coupled call class. */
    private static final String VOICECHAT_MOD_ID = "voicechat";

    /**
     * Places the sender in the voice group for {@code chatId} (start/join a ChatApp call).
     *
     * <p>AUTHORITY: the participant is the connection's player, never the page — so a client can only ever
     * put <i>itself</i> in a call. The page contributes only a {@code chatId} (the group key); it cannot
     * name who joins or which group, which is what 1.16.5's client-supplied caller UUID allowed.</p>
     *
     * <p>Isolation matches getSpawns/getMisiones: {@code TerasVoicechatPlugin} is the only SVC-coupled
     * class and is named solely inside the {@code isLoaded} branch, so it is never classloaded on a
     * server without SVC (which would {@code NoClassDefFoundError} and hang the page). We always reply,
     * even on failure, so the JS promise resolves instead of ringing forever.</p>
     */
    private static void handleSetCall(SetCallPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            String chatId = payload.chatId();
            if (chatId == null || chatId.isBlank()) {
                Teras.LOGGER.warn("setCall from {} with no chatId; ignoring", sp.getGameProfile().getName());
                replyError(sp, payload.requestId(), "missing chatId");
                return;
            }
            if (!net.neoforged.fml.ModList.get().isLoaded(VOICECHAT_MOD_ID)) {
                Teras.LOGGER.warn("setCall requested by {} but SimpleVoiceChat is not installed on this "
                        + "server; the call cannot be wired.", sp.getGameProfile().getName());
                replyError(sp, payload.requestId(), "voicechat_not_installed_server");
                return;
            }
            String reason = es.boffmedia.teras.voice.TerasVoicechatPlugin.startCall(sp, chatId);
            if (reason == null) {
                JsonObject json = new JsonObject();
                // 201 mirrors 1.16.5's "Llamada iniciada"; the page only branches on the request not
                // failing, but the body is kept legacy-shaped for its existing status logging.
                json.addProperty("status", 201);
                json.addProperty("message", "Llamada iniciada");
                PacketDistributor.sendToPlayer(sp, McefResponsePayload.ok(payload.requestId(), GSON.toJson(json)));
            } else {
                Teras.LOGGER.warn("setCall for {} (chat {}) failed: {}",
                        sp.getGameProfile().getName(), chatId, reason);
                replyError(sp, payload.requestId(), reason);
            }
        });
    }

    /** Removes the sender from any voice group (leave a ChatApp call). Lenient — see {@code endCall}. */
    private static void handleLeaveCall(LeaveCallPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!net.neoforged.fml.ModList.get().isLoaded(VOICECHAT_MOD_ID)) {
                // Nothing to leave without SVC; report success so the page finishes its own cleanup.
                replyLeaveOk(sp, payload.requestId());
                return;
            }
            String reason = es.boffmedia.teras.voice.TerasVoicechatPlugin.endCall(sp);
            if (reason == null) {
                replyLeaveOk(sp, payload.requestId());
            } else {
                replyError(sp, payload.requestId(), reason);
            }
        });
    }

    /**
     * Opens the sender's Pokémon PC.
     *
     * <p>AUTHORITY: the PC is the connection's player's own. 1.16.5 sent a uuid in the packet and
     * (correctly) ignored it; carrying none removes the question.</p>
     */
    private static void handleOpenPC(OpenPCPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            // PCOpener compiles against Pixelmon: named only inside the guard. We always reply so the
            // page's promise resolves rather than hanging.
            if (!net.neoforged.fml.ModList.get().isLoaded("pixelmon")) {
                Teras.LOGGER.warn("openPC requested by {} but Pixelmon is not loaded on this server",
                        sp.getGameProfile().getName());
                replyError(sp, payload.requestId(), "pixelmon_not_installed_server");
                return;
            }
            String reason = es.boffmedia.teras.pixelmon.PCOpener.open(sp);
            if (reason == null) {
                JsonObject json = new JsonObject();
                json.addProperty("status", 200);
                json.addProperty("message", "PC abierto");
                PacketDistributor.sendToPlayer(sp, McefResponsePayload.ok(payload.requestId(), GSON.toJson(json)));
            } else {
                replyError(sp, payload.requestId(), reason);
            }
        });
    }

    /**
     * Rolls, if the sender may.
     *
     * <p>AUTHORITY: the player comes from the connection, so a client can only ever dodge itself, and
     * every condition that matters — inside a run, combat enabled, off cooldown — is checked in
     * {@code Dodge}. Spamming this costs nothing but a refused call.</p>
     */
    private static void handleDodge(DodgePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            es.boffmedia.teras.dungeon.combat.Dodge.perform(sp, payload.forward(), payload.left());
        });
    }

    /** Leave-call success reply, legacy-shaped ("Llamada finalizada", status 200). */
    private static void replyLeaveOk(ServerPlayer sp, long requestId) {
        JsonObject json = new JsonObject();
        json.addProperty("status", 200);
        json.addProperty("message", "Llamada finalizada");
        PacketDistributor.sendToPlayer(sp, McefResponsePayload.ok(requestId, GSON.toJson(json)));
    }

    /** Squared reach for editing a frame: the block must be near the editor, with slack for lag. */
    private static final double MAX_FRAME_EDIT_DISTANCE_SQR = 64.0 * 64.0;

    /**
     * The frame {@code sp} is allowed to edit/control at {@code pos}, or {@code null}. Editing media
     * frames is a build-tool privilege (arbitrary URLs), so it is gated to creative/op and to a nearby
     * block — never a forged packet from across the world.
     */
    private static es.boffmedia.teras.blockentity.FrameBlockEntity editableFrame(ServerPlayer sp, BlockPos pos) {
        if (!sp.isCreative() && !sp.hasPermissions(ADMIN_PERMISSION_LEVEL)) {
            Teras.LOGGER.warn("Player {} tried to control a frame without permission", sp.getGameProfile().getName());
            sp.sendSystemMessage(Component.translatable("message.teras.frame_no_permission"));
            return null;
        }
        if (!sp.level().isLoaded(pos)) return null;
        double distanceSqr = sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distanceSqr > MAX_FRAME_EDIT_DISTANCE_SQR) {
            Teras.LOGGER.warn("Frame command from {} {} blocks away; ignoring",
                    sp.getGameProfile().getName(), String.format("%.1f", Math.sqrt(distanceSqr)));
            return null;
        }
        return sp.level().getBlockEntity(pos) instanceof es.boffmedia.teras.blockentity.FrameBlockEntity frame
                ? frame : null;
    }

    private static void handleFrameConfig(FrameConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            es.boffmedia.teras.blockentity.FrameBlockEntity frame = editableFrame(sp, payload.pos());
            if (frame == null) return;
            frame.applyConfig(payload.url(), payload.minX(), payload.minY(), payload.maxX(), payload.maxY(),
                    payload.rotation(), payload.flipX(), payload.flipY(),
                    payload.bothSides(), payload.brightness(), payload.alpha(), payload.renderDistance(),
                    payload.volume(), payload.minAudioDistance(), payload.maxAudioDistance(),
                    payload.loop(), payload.playing(), payload.muted(), payload.lit(), payload.showFrame(),
                    payload.anchorH(), payload.anchorV());
        });
    }

    private static void handleFramePlayback(FramePlaybackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            es.boffmedia.teras.blockentity.FrameBlockEntity frame = editableFrame(sp, payload.pos());
            if (frame == null) return;
            switch (payload.action()) {
                case FramePlaybackPayload.PLAY -> frame.playbackPlay();
                case FramePlaybackPayload.PAUSE -> frame.playbackPause();
                case FramePlaybackPayload.STOP -> frame.playbackStop();
                case FramePlaybackPayload.SEEK -> frame.playbackSeek(payload.arg());
                default -> Teras.LOGGER.warn("Unknown frame playback action {} from {}",
                        payload.action(), sp.getGameProfile().getName());
            }
        });
    }
}
