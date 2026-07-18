package es.boffmedia.teras.caja;

import es.boffmedia.teras.model.world.CajaGrant;
import es.boffmedia.teras.model.world.ObjetoMC;
import es.boffmedia.teras.model.world.PokemonSpec;
import es.boffmedia.teras.util.net.SmartRotomService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The darCaja delivery branches — the highest-consequence logic in the mod, since each one decides
 * whether a backend reward is delivered, silently lost, or duplicated.
 *
 * <p>The rule under test throughout: <b>confirm only after an actual delivery</b>. An unconfirmed
 * reservation expires back to claimable after 5 minutes, so failing to confirm costs a retry;
 * confirming without delivering spends the rows and loses the reward for good.</p>
 */
class CajaDeliveryServiceTest {

    private static final long REQUEST_ID = 42L;
    private static final String SOURCE = "mina";

    private final UUID player = UUID.randomUUID();

    /** Records what the service asked the game to do, in order. */
    private static final class FakeRuntime implements CajaDeliveryService.Runtime {
        final List<String> actions = new ArrayList<>();
        boolean online = true;
        int pokemonDelivered;

        @Override
        public boolean isOnline(UUID playerId) {
            return online;
        }

        @Override
        public int giveItems(UUID playerId, CajaGrant grant) {
            actions.add("giveItems:" + grant.objetos().size());
            return grant.objetos().size();
        }

        @Override
        public int givePokemon(UUID playerId, List<PokemonSpec> pokemon, String source) {
            actions.add("givePokemon:" + pokemon.size());
            return pokemonDelivered;
        }

        @Override
        public void replyOk(UUID playerId, long requestId, int objetos, int pokemon) {
            actions.add("replyOk:" + objetos + ":" + pokemon);
        }

        @Override
        public void replyError(UUID playerId, long requestId, String reason) {
            actions.add("replyError:" + reason);
        }

        @Override
        public void confirm(UUID playerId, String reservationId, String source) {
            actions.add("confirm:" + reservationId);
        }

        boolean confirmed() {
            return actions.stream().anyMatch(a -> a.startsWith("confirm:"));
        }

        boolean replied() {
            return actions.stream().anyMatch(a -> a.startsWith("reply"));
        }
    }

    private final FakeRuntime runtime = new FakeRuntime();

    private void deliver(SmartRotomService.Reservation reservation) {
        CajaDeliveryService.deliver(runtime, player, SOURCE, REQUEST_ID, reservation);
    }

    private static SmartRotomService.Reservation reservation(String id, int items, int mons) {
        List<ObjetoMC> objetos = new ArrayList<>();
        for (int i = 0; i < items; i++) {
            objetos.add(new ObjetoMC("minecraft:diamond", 1));
        }
        List<PokemonSpec> pokemon = new ArrayList<>();
        for (int i = 0; i < mons; i++) {
            pokemon.add(new PokemonSpec("pikachu", 1));
        }
        return new SmartRotomService.Reservation(id, new CajaGrant(objetos, pokemon));
    }

    @Test
    void aFailedReserveGrantsNothingAndConfirmsNothing() {
        // Nothing was locked, so nothing is spent — the claim stays retryable.
        deliver(null);

        assertEquals(List.of("replyError:reserve failed"), runtime.actions);
        assertFalse(runtime.confirmed());
    }

    @Test
    void aFailedReserveForAnOfflinePlayerDoesNotEvenReply() {
        runtime.online = false;

        deliver(null);

        assertTrue(runtime.actions.isEmpty());
    }

    @Test
    void owingNothingSucceedsWithoutConfirming() {
        // An empty grant is not an error, but there is no reservation to spend either.
        deliver(reservation(null, 0, 0));

        assertEquals(List.of("replyOk:0:0"), runtime.actions);
        assertFalse(runtime.confirmed());
    }

    @Test
    void aReservationWithNoIdIsTreatedAsOwingNothing() {
        deliver(reservation(null, 3, 1));

        assertEquals(List.of("replyOk:0:0"), runtime.actions);
        assertFalse(runtime.confirmed());
    }

    @Test
    void disconnectingBeforeDeliveryLeavesTheReservationToExpire() {
        // THE recoverable case: not confirming is what lets the player re-claim after the TTL. A
        // confirm here would spend rows that were never handed over.
        runtime.online = false;

        deliver(reservation("res-1", 2, 1));

        assertFalse(runtime.confirmed());
        assertFalse(runtime.replied());
        assertTrue(runtime.actions.isEmpty());
    }

    @Test
    void aSuccessfulDeliveryGivesRepliesThenConfirms() {
        runtime.pokemonDelivered = 1;

        deliver(reservation("res-1", 2, 1));

        assertEquals(List.of("giveItems:2", "givePokemon:1", "replyOk:2:1", "confirm:res-1"),
                runtime.actions);
    }

    @Test
    void deliveryConfirmsEvenWhenSomeGivesFailed() {
        // Give failures are permanent (bad spec, no engine, storage full). Not confirming would loop
        // reserve → expire → reserve forever, so the spend is correct even though the player got less.
        runtime.pokemonDelivered = 0;

        deliver(reservation("res-1", 1, 2));

        assertEquals(List.of("giveItems:1", "givePokemon:2", "replyOk:1:0", "confirm:res-1"),
                runtime.actions);
    }

    @Test
    void theReplyReportsWhatWasActuallyDelivered() {
        runtime.pokemonDelivered = 2;

        deliver(reservation("res-1", 3, 5));

        assertTrue(runtime.actions.contains("replyOk:3:2"));
    }

    @Test
    void everyBranchRepliesAtMostOnce() {
        runtime.pokemonDelivered = 1;

        deliver(reservation("res-1", 1, 1));

        assertEquals(1, runtime.actions.stream().filter(a -> a.startsWith("reply")).count());
    }
}
