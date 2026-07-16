package es.boffmedia.teras.util.net;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
