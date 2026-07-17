package es.boffmedia.teras.util.net;

import es.boffmedia.teras.model.world.ObjetoMC;
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

    // ---- parseObjetos (darCaja) ----
    //
    // The distinction these pin is the whole grant contract: `null` means "we were not told to hand
    // anything over" and an empty list means "told, and the answer is nothing". Both grant nothing, so
    // a bug collapsing them is invisible — right up until one of them starts granting.

    @Test
    void parsesObjetosOffTheResponseRoot() {
        List<ObjetoMC> objetos = SmartRotomService.parseObjetos(
                "{\"objetos\":[{\"id\":\"minecraft:diamond\",\"cantidad\":5},"
                        + "{\"id\":\"minecraft:bone\",\"cantidad\":2}]}");
        assertNotNull(objetos);
        assertEquals(2, objetos.size());
        assertEquals(new ObjetoMC("minecraft:diamond", 5), objetos.get(0));
        assertEquals(new ObjetoMC("minecraft:bone", 2), objetos.get(1));
    }

    /** Nothing owed is a successful claim, not a failure — it must not read as "the claim broke". */
    @Test
    void readsAnEmptyObjetosArrayAsOwedNothingRatherThanFailure() {
        List<ObjetoMC> objetos = SmartRotomService.parseObjetos("{\"objetos\":[]}");
        assertNotNull(objetos);
        assertTrue(objetos.isEmpty());
    }

    /**
     * The route opts out of the API's global {success, statusCode, data} envelope via @SkipEnvelope.
     * If that decorator is ever dropped, `objetos` moves under `data` — and this must come back null
     * (grant nothing), never reach in and read it. Granting from an envelope we were told not to
     * expect is how a protocol change becomes a silent item duplication.
     */
    @Test
    void refusesAnEnvelopedBodyRatherThanReadingData() {
        assertNull(SmartRotomService.parseObjetos(
                "{\"success\":true,\"statusCode\":200,\"data\":{\"objetos\":"
                        + "[{\"id\":\"minecraft:diamond\",\"cantidad\":5}]}}"));
    }

    @Test
    void returnsNullWhenTheBodyIsNotACajaResponse() {
        assertNull(SmartRotomService.parseObjetos(null));
        assertNull(SmartRotomService.parseObjetos(""));
        assertNull(SmartRotomService.parseObjetos("{}"));
        assertNull(SmartRotomService.parseObjetos("{\"objetos\":\"nope\"}"));
        assertNull(SmartRotomService.parseObjetos("[{\"id\":\"minecraft:diamond\"}]"));
        assertNull(SmartRotomService.parseObjetos("<html><body>502 Bad Gateway</body></html>"));
        // A 4xx body: postJsonAuthed already returns null for these, so this is defence in depth.
        assertNull(SmartRotomService.parseObjetos(
                "{\"statusCode\":400,\"message\":\"source should not be empty\"}"));
    }

    @Test
    void dropsEntriesWithNoUsableIdButKeepsTheRest() {
        List<ObjetoMC> objetos = SmartRotomService.parseObjetos(
                "{\"objetos\":[{\"cantidad\":3},{\"id\":\"\",\"cantidad\":1},"
                        + "{\"id\":\"minecraft:emerald\",\"cantidad\":3}]}");
        assertNotNull(objetos);
        assertEquals(1, objetos.size());
        assertEquals("minecraft:emerald", objetos.get(0).id());
    }

    /** A missing cantidad becomes 0 and ChestCreationHelper clamps it to 1; it must not drop the item. */
    @Test
    void keepsAnEntryWhoseCantidadIsMissing() {
        List<ObjetoMC> objetos = SmartRotomService.parseObjetos(
                "{\"objetos\":[{\"id\":\"minecraft:diamond\"}]}");
        assertNotNull(objetos);
        assertEquals(new ObjetoMC("minecraft:diamond", 0), objetos.get(0));
    }
}
