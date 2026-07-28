package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.util.YamlConfig;

/**
 * The pacto's prices and la deuda.
 *
 * <p>{@code floorsToCollect} at two means <i>La Expedición</i> — the two-floor everyday format —
 * never sees a Cobrador. That is a ruling, not an oversight (PISOS §63e): the debt dies with the
 * run rather than growing an exit-collection, so la deuda is a Descenso-shaped mechanic by choice.
 * Do not "fix" it in a later pass.</p>
 */
public record DevilConfig(int coinPrice, int heartPrice, int debtInterestPct,
                          int debtSettleDiscountPct, int floorsToCollect) {

    static DevilConfig defaults() {
        return new DevilConfig(60, 2, 50, 20, 2);
    }

    static DevilConfig read(YamlConfig yaml, DevilConfig previous) {
        YamlConfig devil = yaml.section("trato");
        return new DevilConfig(
                devil.integer("precioMonedas", previous.coinPrice),
                devil.integer("precioCorazones", previous.heartPrice),
                devil.integer("interesDeudaPct", previous.debtInterestPct),
                devil.integer("descuentoSaldoPct", previous.debtSettleDiscountPct),
                devil.integer("pisosHastaCobrador", previous.floorsToCollect));
    }
}
