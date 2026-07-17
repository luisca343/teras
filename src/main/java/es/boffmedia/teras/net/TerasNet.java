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
        registrar.playToServer(FrameConfigPayload.TYPE, FrameConfigPayload.STREAM_CODEC, TerasNet::handleFrameConfig);
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
    public static void requestDarCaja(long requestId, String source, java.util.List<Integer> ids) {
        PacketDistributor.sendToServer(new DarCajaPayload(requestId, source, ids));
    }

    /** Asks the server to register the Pokémon the player just scanned; see {@link DexRegisterPayload}. */
    public static void registerDex(int entityId) {
        PacketDistributor.sendToServer(new DexRegisterPayload(entityId));
    }

    /** Sends a picture frame's edited configuration to the server; see {@link FrameConfigPayload}. */
    public static void sendFrameConfig(FrameConfigPayload payload) {
        PacketDistributor.sendToServer(payload);
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
     * Redeems what the sender is owed from {@code source} (narrowed to {@code payload.ids()}) and gives
     * it to them: items as chests, Pokémon to the party.
     *
     * <p>AUTHORITY: the uuid comes off the connection, never the page — that is the whole security
     * boundary. The page contributes only a source string and a row-id selector; the backend picks the
     * rewards. 1.16.5 took the item list from the client and granted it, which is what let a modified
     * client mint anything. A blank or malformed source fails the request: there is no "everything
     * owed", because the phrase is not well-defined across sources that disagree about what
     * {@code used} means.</p>
     *
     * <p>Delivery is two-phase (DARCAJA.md §7): <b>reserve</b> soft-locks the owed rows without
     * spending, we deliver, then <b>confirm</b> spends them. If the player is gone before delivery we
     * skip the confirm and the reservation expires back to claimable — the disconnect case is now
     * recoverable, not lost.</p>
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
            java.util.UUID uuid = sp.getUUID();
            // The reserve blocks on HTTP; on this thread it would stall the tick. Hop out, then back:
            // the grant touches the player's party/inventory and must land on the server thread again.
            Teras.EXECUTOR.execute(() -> {
                es.boffmedia.teras.util.net.SmartRotomService.Reservation reservation =
                        es.boffmedia.teras.util.net.SmartRotomService.reserveCaja(uuid, source, payload.ids());
                server.execute(() -> deliverCaja(server, uuid, source, payload.requestId(), reservation));
            });
        });
    }

    /** Server thread: hands over the reserved grant, says so exactly once, then confirms the spend. */
    private static void deliverCaja(MinecraftServer server, java.util.UUID uuid, String source,
                                    long requestId,
                                    es.boffmedia.teras.util.net.SmartRotomService.Reservation reservation) {
        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
        if (reservation == null) {
            // Reserve failed (transport/parse). Nothing was locked, so nothing is spent — recoverable.
            Teras.LOGGER.warn("DarCaja: reserve failed for {} (source '{}'); granting nothing", uuid, source);
            if (sp != null) {
                replyError(sp, requestId, "reserve failed");
            }
            return;
        }
        es.boffmedia.teras.model.world.CajaGrant grant = reservation.grant();
        if (reservation.reservationId() == null || grant.isEmpty()) {
            // Nothing owed — nothing to confirm.
            Teras.LOGGER.info("DarCaja: {} is owed nothing from '{}'", uuid, source);
            if (sp != null) {
                replyOk(sp, requestId, 0, 0);
            }
            return;
        }
        if (sp == null) {
            // Player gone before delivery. Do NOT confirm: the reservation expires in 5 min and the
            // rows return to claimable, so the reward survives a re-claim. This is the whole point.
            Teras.LOGGER.warn("DarCaja: {} disconnected before delivery from '{}'; reservation {} left to "
                    + "expire (recoverable) objetos={} pokemon={}", uuid, source,
                    reservation.reservationId(), grant.objetos(), grant.pokemon());
            return;
        }
        int items = grant.objetos().size();
        if (items > 0) {
            es.boffmedia.teras.util.ChestCreationHelper.createAndGiveChests(sp, grant.objetos());
        }
        int mons = deliverPokemon(sp, uuid, source, grant.pokemon());
        Teras.LOGGER.info("DarCaja: granted {} item stack(s) and {} Pokémon to {} ({}) from '{}'",
                items, mons, sp.getGameProfile().getName(), uuid, source);
        replyOk(sp, requestId, items, mons);
        // Delivered to an online player: spend it. Per-item give failures above are already logged and
        // are permanent (bad spec, no engine, PC full) — confirming anyway is correct, since not
        // confirming would loop reserve→expire→reserve forever. Back off the server thread; confirm blocks.
        String reservationId = reservation.reservationId();
        Teras.EXECUTOR.execute(() -> {
            if (!es.boffmedia.teras.util.net.SmartRotomService.confirmCaja(uuid, reservationId)) {
                Teras.LOGGER.error("DarCaja: delivered to {} from '{}' but confirm FAILED for reservation "
                        + "{}; those rows may be re-delivered on a re-claim after the 5-min TTL (possible "
                        + "dupe)", uuid, source, reservationId);
            }
        });
    }

    /** Gives each spec to the party (full → PC), returning how many landed. A failed give is lost. */
    private static int deliverPokemon(ServerPlayer sp, java.util.UUID uuid, String source,
                                      java.util.List<es.boffmedia.teras.model.world.PokemonSpec> pokemon) {
        if (pokemon.isEmpty()) {
            return 0;
        }
        es.boffmedia.teras.give.api.GiveProvider provider = es.boffmedia.teras.give.api.GiveProviders.get();
        if (provider == null) {
            Teras.LOGGER.error("DarCaja: {} owed Pokémon from '{}' but no engine is installed; "
                    + "SPENT AND LOST: {}", uuid, source, pokemon);
            return 0;
        }
        int given = 0;
        for (es.boffmedia.teras.model.world.PokemonSpec mon : pokemon) {
            for (int i = 0; i < mon.cantidad(); i++) {
                if (provider.givePokemon(sp, mon.spec(), true)) {
                    given++;
                } else {
                    Teras.LOGGER.error("DarCaja: {} SPENT AND LOST a Pokémon '{}' from '{}' "
                            + "(give failed — unparseable, engine mismatch, or storage full)",
                            uuid, mon.spec(), source);
                }
            }
        }
        return given;
    }

    /** Success reply: resolves the page's onSuccess. The {@code status} field is kept for logging and
     *  any consumer that inspects the body; the transport {@code ok} flag is what the page branches on. */
    private static void replyOk(ServerPlayer sp, long requestId, int objetos, int pokemon) {
        JsonObject json = new JsonObject();
        json.addProperty("status", "ok");
        json.addProperty("objetos", objetos);
        json.addProperty("pokemon", pokemon);
        PacketDistributor.sendToPlayer(sp, McefResponsePayload.ok(requestId, GSON.toJson(json)));
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

    /** Squared reach for editing a frame: the block must be near the editor, with slack for lag. */
    private static final double MAX_FRAME_EDIT_DISTANCE_SQR = 64.0 * 64.0;

    private static void handleFrameConfig(FrameConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            // AUTHORITY CHECK: media frames point at arbitrary URLs, so editing is a build-tool
            // privilege — only creative or op players, never any client that forges the packet.
            if (!sp.isCreative() && !sp.hasPermissions(CHAT_PERMISSION_LEVEL)) {
                Teras.LOGGER.warn("Player {} tried to configure a frame without permission",
                        sp.getGameProfile().getName());
                sp.sendSystemMessage(Component.translatable("message.teras.frame_no_permission"));
                return;
            }
            BlockPos pos = payload.pos();
            if (!sp.level().isLoaded(pos)) return;
            double distanceSqr = sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distanceSqr > MAX_FRAME_EDIT_DISTANCE_SQR) {
                Teras.LOGGER.warn("Frame edit from {} {} blocks away; ignoring",
                        sp.getGameProfile().getName(), String.format("%.1f", Math.sqrt(distanceSqr)));
                return;
            }
            if (sp.level().getBlockEntity(pos) instanceof es.boffmedia.teras.blockentity.FrameBlockEntity frame) {
                frame.applyConfig(payload.url(), payload.minX(), payload.minY(), payload.maxX(), payload.maxY(),
                        payload.rotation(), payload.flipX(), payload.flipY(),
                        payload.bothSides(), payload.brightness(), payload.alpha(), payload.renderDistance(),
                        payload.volume(), payload.minAudioDistance(), payload.maxAudioDistance(),
                        payload.loop(), payload.playing(), payload.muted(), payload.lit(), payload.showFrame(),
                        payload.anchorH(), payload.anchorV());
            }
        });
    }
}
