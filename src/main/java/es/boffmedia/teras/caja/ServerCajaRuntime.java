package es.boffmedia.teras.caja;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.give.api.GiveProvider;
import es.boffmedia.teras.give.api.GiveProviders;
import es.boffmedia.teras.model.world.CajaGrant;
import es.boffmedia.teras.model.world.PokemonSpec;
import es.boffmedia.teras.net.McefResponsePayload;
import es.boffmedia.teras.util.ChestCreationHelper;
import es.boffmedia.teras.util.net.SmartRotomService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * {@link CajaDeliveryService.Runtime} against a live server: gives, replies over the network, and
 * confirms on {@link Teras#EXECUTOR}.
 *
 * <p>Every method re-resolves the player from the server rather than holding a {@code ServerPlayer},
 * so a disconnect mid-delivery is seen rather than acting on a stale handle.</p>
 */
public final class ServerCajaRuntime implements CajaDeliveryService.Runtime {

    private static final Gson GSON = new Gson();

    private final MinecraftServer server;

    public ServerCajaRuntime(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Runs the whole claim: reserve off-thread, deliver on the server thread, confirm off-thread again.
     * The reserve and confirm block on HTTP and would stall the tick; the grant touches the player's
     * party and inventory and must be on the server thread.
     */
    public static void claim(MinecraftServer server, UUID playerId, String source,
                             List<Integer> ids, long requestId) {
        ServerCajaRuntime runtime = new ServerCajaRuntime(server);
        Teras.EXECUTOR.execute(() -> {
            SmartRotomService.Reservation reservation = SmartRotomService.reserveCaja(playerId, source, ids);
            server.execute(() ->
                    CajaDeliveryService.deliver(runtime, playerId, source, requestId, reservation));
        });
    }

    private ServerPlayer player(UUID playerId) {
        return server.getPlayerList().getPlayer(playerId);
    }

    @Override
    public boolean isOnline(UUID playerId) {
        return player(playerId) != null;
    }

    @Override
    public int giveItems(UUID playerId, CajaGrant grant) {
        ServerPlayer sp = player(playerId);
        if (sp == null || grant.objetos().isEmpty()) {
            return 0;
        }
        ChestCreationHelper.createAndGiveChests(sp, grant.objetos());
        return grant.objetos().size();
    }

    @Override
    public int givePokemon(UUID playerId, List<PokemonSpec> pokemon, String source) {
        ServerPlayer sp = player(playerId);
        if (sp == null || pokemon.isEmpty()) {
            return 0;
        }
        GiveProvider provider = GiveProviders.get();
        if (provider == null) {
            Teras.LOGGER.error("DarCaja: {} owed Pokémon from '{}' but no engine is installed; "
                    + "SPENT AND LOST: {}", playerId, source, pokemon);
            return 0;
        }
        int given = 0;
        for (PokemonSpec mon : pokemon) {
            for (int i = 0; i < mon.cantidad(); i++) {
                if (provider.givePokemon(sp, mon.spec(), true)) {
                    given++;
                } else {
                    Teras.LOGGER.error("DarCaja: {} SPENT AND LOST a Pokémon '{}' from '{}' "
                                    + "(give failed — unparseable, engine mismatch, or storage full)",
                            playerId, mon.spec(), source);
                }
            }
        }
        return given;
    }

    /** The {@code status} field is kept for logging; the page branches on the transport {@code ok} flag. */
    @Override
    public void replyOk(UUID playerId, long requestId, int objetos, int pokemon) {
        ServerPlayer sp = player(playerId);
        if (sp == null) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("status", "ok");
        json.addProperty("objetos", objetos);
        json.addProperty("pokemon", pokemon);
        PacketDistributor.sendToPlayer(sp, McefResponsePayload.ok(requestId, GSON.toJson(json)));
    }

    /** Rejects the page's promise via onFailure, so a failed claim cannot read as done. */
    @Override
    public void replyError(UUID playerId, long requestId, String reason) {
        ServerPlayer sp = player(playerId);
        if (sp == null) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("status", "error");
        json.addProperty("reason", reason);
        PacketDistributor.sendToPlayer(sp, McefResponsePayload.error(requestId, GSON.toJson(json)));
    }

    @Override
    public void confirm(UUID playerId, String reservationId, String source) {
        Teras.EXECUTOR.execute(() -> {
            if (!SmartRotomService.confirmCaja(playerId, reservationId)) {
                Teras.LOGGER.error("DarCaja: delivered to {} from '{}' but confirm FAILED for reservation "
                        + "{}; those rows may be re-delivered on a re-claim after the 5-min TTL (possible "
                        + "dupe)", playerId, source, reservationId);
            }
        });
    }
}
