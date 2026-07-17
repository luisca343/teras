package es.boffmedia.teras.util.net;

import es.boffmedia.teras.model.world.CajaGrant;
import es.boffmedia.teras.model.world.ObjetoMC;
import es.boffmedia.teras.model.world.PokemonSpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for parsing a starbank balance response. Pure string→BigDecimal, so no Minecraft and no
 * network.
 *
 * <p>These matter because the current backend's response shape is <b>unconfirmed</b> (see
 * {@link SmartRotomService}), so the parser accepts several plausible shapes — and, more importantly,
 * returns {@code null} rather than a wrong number for anything it doesn't recognise. 1.16.5 did a bare
 * {@code Double.parseDouble} on the body, so an error page or an HTML 502 threw
 * {@code NumberFormatException} out of the login path.</p>
 */
class SmartRotomServiceTest {

    @Test
    void parsesBareNumber() {
        assertEquals(new BigDecimal("1234"), SmartRotomService.parseBalance("1234"));
        assertEquals(new BigDecimal("1234.56"), SmartRotomService.parseBalance("1234.56"));
    }

    @Test
    void parsesBareNumberWithSurroundingWhitespace() {
        assertEquals(new BigDecimal("1234"), SmartRotomService.parseBalance("  1234\n"));
    }

    @Test
    void parsesWrappedJsonShapes() {
        assertEquals(new BigDecimal("500"), SmartRotomService.parseBalance("{\"balance\":500}"));
        assertEquals(new BigDecimal("500"), SmartRotomService.parseBalance("{\"dinero\":500}"));
        assertEquals(new BigDecimal("500"), SmartRotomService.parseBalance("{\"data\":500}"));
    }

    /**
     * Unlike the caja routes, the starbank balance GET does <b>not</b> {@code @SkipEnvelope}, so the
     * live body is {@code {success, statusCode, data:{balance}}} and the number sits at
     * {@code data.balance}. Fixing the URL without reading through the envelope reproduces the original
     * "balance stays unknown" symptom with a 200 instead of a 404.
     */
    @Test
    void parsesTheEnvelopedBalanceUnderData() {
        assertEquals(new BigDecimal("1500"), SmartRotomService.parseBalance(
                "{\"success\":true,\"statusCode\":200,\"data\":{\"balance\":1500}}"));
    }

    @Test
    void preservesPrecisionRatherThanTruncatingToInt() {
        // 1.16.5 stored money as an int (WungillData.setDinero cast a double down), so any fractional
        // balance the backend held was silently floored on every read.
        assertEquals(new BigDecimal("10.75"), SmartRotomService.parseBalance("{\"balance\":10.75}"));
    }

    @Test
    void returnsNullForNonNumericBodies() {
        assertNull(SmartRotomService.parseBalance(null));
        assertNull(SmartRotomService.parseBalance(""));
        assertNull(SmartRotomService.parseBalance("   "));
        assertNull(SmartRotomService.parseBalance("<html><body>502 Bad Gateway</body></html>"));
        assertNull(SmartRotomService.parseBalance("ERROR"));
        assertNull(SmartRotomService.parseBalance("{\"error\":\"not found\"}"));
        assertNull(SmartRotomService.parseBalance("{\"balance\":\"mucho\"}"));
        assertNull(SmartRotomService.parseBalance("[1,2,3]"));
    }

    // ---- parseGrant (darCaja) ----
    //
    // The distinction these pin is the whole grant contract: a `null` CajaGrant means "we were not
    // told to hand anything over" and an empty one means "told, and the answer is nothing". Both grant
    // nothing, so a bug collapsing them is invisible — right up until one of them starts granting.

    @Test
    void parsesObjetosAndPokemonOffTheResponseRoot() {
        CajaGrant grant = SmartRotomService.parseGrant(
                "{\"objetos\":[{\"id\":\"pixelmon:rare_candy\",\"cantidad\":3}],"
                        + "\"pokemon\":[{\"spec\":\"Incineroar lvl:50 otn:Wolfey\",\"cantidad\":1}]}");
        assertNotNull(grant);
        assertEquals(List.of(new ObjetoMC("pixelmon:rare_candy", 3)), grant.objetos());
        assertEquals(List.of(new PokemonSpec("Incineroar lvl:50 otn:Wolfey", 1)), grant.pokemon());
    }

    /** Mine's shape: items only, pokemon empty. A source-only claim must parse identically to before. */
    @Test
    void parsesAnItemOnlyGrantWithEmptyPokemon() {
        CajaGrant grant = SmartRotomService.parseGrant(
                "{\"objetos\":[{\"id\":\"minecraft:diamond\",\"cantidad\":5}],\"pokemon\":[]}");
        assertNotNull(grant);
        assertEquals(1, grant.objetos().size());
        assertTrue(grant.pokemon().isEmpty());
    }

    /** An absent pokemon key (older backend) is empty, not a failure — mine keeps working. */
    @Test
    void treatsAnAbsentPokemonKeyAsEmpty() {
        CajaGrant grant = SmartRotomService.parseGrant("{\"objetos\":[]}");
        assertNotNull(grant);
        assertTrue(grant.isEmpty());
    }

    /**
     * The route opts out of the API's global {success, statusCode, data} envelope via @SkipEnvelope.
     * If that decorator is ever dropped, the arrays move under `data` — and this must come back null
     * (grant nothing), never reach in and read it. Granting from an envelope we were told not to
     * expect is how a protocol change becomes a silent duplication.
     */
    @Test
    void refusesAnEnvelopedBodyRatherThanReadingData() {
        assertNull(SmartRotomService.parseGrant(
                "{\"success\":true,\"statusCode\":200,\"data\":{\"objetos\":"
                        + "[{\"id\":\"minecraft:diamond\",\"cantidad\":5}],\"pokemon\":[]}}"));
    }

    @Test
    void returnsNullWhenTheBodyIsNotACajaResponse() {
        assertNull(SmartRotomService.parseGrant(null));
        assertNull(SmartRotomService.parseGrant(""));
        assertNull(SmartRotomService.parseGrant("{}"));
        assertNull(SmartRotomService.parseGrant("{\"objetos\":\"nope\"}"));
        assertNull(SmartRotomService.parseGrant("[{\"id\":\"minecraft:diamond\"}]"));
        assertNull(SmartRotomService.parseGrant("<html><body>502 Bad Gateway</body></html>"));
        // A 4xx body: postJsonAuthed already returns null for these, so this is defence in depth.
        assertNull(SmartRotomService.parseGrant(
                "{\"statusCode\":400,\"message\":\"source should not be empty\"}"));
    }

    @Test
    void dropsObjetosAndPokemonWithNoUsableKeyButKeepsTheRest() {
        CajaGrant grant = SmartRotomService.parseGrant(
                "{\"objetos\":[{\"cantidad\":3},{\"id\":\"\",\"cantidad\":1},"
                        + "{\"id\":\"minecraft:emerald\",\"cantidad\":3}],"
                        + "\"pokemon\":[{\"cantidad\":1},{\"spec\":\"  \"},"
                        + "{\"spec\":\"Pikachu\",\"cantidad\":2}]}");
        assertNotNull(grant);
        assertEquals(1, grant.objetos().size());
        assertEquals("minecraft:emerald", grant.objetos().get(0).id());
        assertEquals(1, grant.pokemon().size());
        assertEquals(new PokemonSpec("Pikachu", 2), grant.pokemon().get(0));
    }

    /** A missing item cantidad becomes 0 (ChestCreationHelper clamps to 1); a missing mon cantidad is 1. */
    @Test
    void appliesTheRightDefaultCantidadPerKind() {
        CajaGrant grant = SmartRotomService.parseGrant(
                "{\"objetos\":[{\"id\":\"minecraft:diamond\"}],"
                        + "\"pokemon\":[{\"spec\":\"Incineroar\"}]}");
        assertNotNull(grant);
        assertEquals(new ObjetoMC("minecraft:diamond", 0), grant.objetos().get(0));
        assertEquals(new PokemonSpec("Incineroar", 1), grant.pokemon().get(0));
    }

    // ---- parseReservation (darCaja reserve) ----
    //
    // Same grant contract as parseGrant, plus the reservationId off the root. The one distinction that
    // matters: reservationId == null means "nothing owed, do not confirm"; a non-null id with a
    // non-empty grant means "deliver, then confirm this".

    @Test
    void parsesReservationIdAlongsideTheGrant() {
        SmartRotomService.Reservation reservation = SmartRotomService.parseReservation(
                "{\"reservationId\":\"abc-123\",\"objetos\":[{\"id\":\"minecraft:diamond\",\"cantidad\":5}],"
                        + "\"pokemon\":[{\"spec\":\"Pikachu\",\"cantidad\":1}]}");
        assertNotNull(reservation);
        assertEquals("abc-123", reservation.reservationId());
        assertEquals(1, reservation.grant().objetos().size());
        assertEquals(1, reservation.grant().pokemon().size());
    }

    /** Nothing owed: reservationId is JSON null, both lists empty. Callers must not confirm this. */
    @Test
    void parsesANullReservationIdAsNothingOwed() {
        SmartRotomService.Reservation reservation = SmartRotomService.parseReservation(
                "{\"reservationId\":null,\"objetos\":[],\"pokemon\":[]}");
        assertNotNull(reservation);
        assertNull(reservation.reservationId());
        assertTrue(reservation.grant().isEmpty());
    }

    /** A body that isn't a caja response is null (grant nothing), exactly as parseGrant. */
    @Test
    void returnsNullWhenTheReservationBodyIsNotACajaResponse() {
        assertNull(SmartRotomService.parseReservation(null));
        assertNull(SmartRotomService.parseReservation(""));
        assertNull(SmartRotomService.parseReservation("{\"reservationId\":\"abc\"}"));
        assertNull(SmartRotomService.parseReservation("<html><body>502 Bad Gateway</body></html>"));
    }
}
