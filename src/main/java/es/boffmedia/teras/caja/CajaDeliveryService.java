package es.boffmedia.teras.caja;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.model.world.CajaGrant;
import es.boffmedia.teras.model.world.PokemonSpec;
import es.boffmedia.teras.util.net.SmartRotomService;

import java.util.List;
import java.util.UUID;

/**
 * The darCaja two-phase delivery state machine (DARCAJA.md §7): <b>reserve</b> soft-locks the rows the
 * player is owed without spending them, we deliver, then <b>confirm</b> spends them.
 *
 * <p>Which branch runs decides whether a reward is delivered, lost, or duplicated, so the decision is
 * kept behind {@link Runtime} — the game-side effects are the seam, and the branches are unit-tested
 * without a Minecraft runtime ({@code CajaDeliveryServiceTest}).</p>
 *
 * <p>Not confirming is the safe failure everywhere: an unconfirmed reservation expires back to
 * claimable after 5 minutes, so the reward survives. Confirming without delivering loses it.</p>
 */
public final class CajaDeliveryService {
    private CajaDeliveryService() {}

    /** Game-side effects, so the delivery branches can be exercised without a server. */
    public interface Runtime {
        /** True if the player is still connected and can be handed anything. */
        boolean isOnline(UUID playerId);

        /** Hands over the item stacks (as chests), returning how many stacks were granted. */
        int giveItems(UUID playerId, CajaGrant grant);

        /** Gives each spec to the party (full → PC), returning how many landed. */
        int givePokemon(UUID playerId, List<PokemonSpec> pokemon, String source);

        void replyOk(UUID playerId, long requestId, int objetos, int pokemon);

        void replyError(UUID playerId, long requestId, String reason);

        /** Spends the reserved rows. Blocking, so implementations run it off the server thread. */
        void confirm(UUID playerId, String reservationId, String source);
    }

    /**
     * Applies {@code reservation} to the player: delivers what was reserved, replies exactly once, and
     * confirms the spend only when delivery actually happened.
     */
    public static void deliver(Runtime runtime, UUID playerId, String source, long requestId,
                               SmartRotomService.Reservation reservation) {
        boolean online = runtime.isOnline(playerId);

        if (reservation == null) {
            // Reserve failed (transport/parse). Nothing was locked, so nothing is spent — recoverable.
            Teras.LOGGER.warn("DarCaja: reserve failed for {} (source '{}'); granting nothing", playerId, source);
            if (online) {
                runtime.replyError(playerId, requestId, "reserve failed");
            }
            return;
        }

        CajaGrant grant = reservation.grant();
        if (reservation.reservationId() == null || grant.isEmpty()) {
            Teras.LOGGER.info("DarCaja: {} is owed nothing from '{}'", playerId, source);
            if (online) {
                runtime.replyOk(playerId, requestId, 0, 0);
            }
            return;
        }

        if (!online) {
            // Player gone before delivery. Do NOT confirm: the reservation expires in 5 min and the
            // rows return to claimable, so the reward survives a re-claim. This is the whole point.
            Teras.LOGGER.warn("DarCaja: {} disconnected before delivery from '{}'; reservation {} left to "
                    + "expire (recoverable) objetos={} pokemon={}", playerId, source,
                    reservation.reservationId(), grant.objetos(), grant.pokemon());
            return;
        }

        int items = runtime.giveItems(playerId, grant);
        int mons = runtime.givePokemon(playerId, grant.pokemon(), source);
        Teras.LOGGER.info("DarCaja: granted {} item stack(s) and {} Pokémon to {} from '{}'",
                items, mons, playerId, source);
        runtime.replyOk(playerId, requestId, items, mons);
        // Delivered to an online player: spend it. Per-item give failures are already logged and are
        // permanent (bad spec, no engine, PC full); confirming anyway is correct, since not confirming
        // would loop reserve→expire→reserve forever.
        runtime.confirm(playerId, reservation.reservationId(), source);
    }
}
