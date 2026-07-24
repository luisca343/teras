package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.gen.SatelliteOdds;

import java.util.ArrayList;
import java.util.List;

/**
 * What is on la Orden's font, as a decision rather than as a screen — which gifts a purity tier
 * offers, and which single mercy the party's own state puts in the first slot.
 *
 * <p><b>No Minecraft here</b>, the same rule {@code RoomKeys} and {@code SatelliteOdds} follow: the
 * unit-test classpath has no game on it, so anything that has to be checked has to be separable from
 * what renders it. {@link OrdenGift} is the half that talks to players and hands out the goods; this
 * is the half that decides what the offer <i>is</i>.</p>
 */
public final class OrdenPool {
    private OrdenPool() {}

    /** The gift ids, as they travel behind a clickable chat line or a dialogue option. */
    public static final String ABSOLUCION = "absolucion";
    public static final String PURIFICACION = "purificacion";
    public static final String VIGOR = "vigor";
    public static final String OBOLO = "obolo";
    public static final String RELIQUIA = "reliquia";
    public static final String RESTITUCION = "restitucion";
    public static final String FENIX = "fenix";

    /** One offer: what it is called, and the line under it. */
    public record Gift(String id, String label, String blurb) {}

    /**
     * Her first slot is contextual mercy and is always present: the run's debt if it carries one,
     * and otherwise an affliction lifted.
     *
     * <p>This is also what answers the debt loophole. An unpaid deuda pierces {@code
     * ordenCommitted} — grace absolves the soul but does not settle accounts
     * ({@link SatelliteOdds#acreedor}) — so she has to be able to settle one deliberately, or a
     * party that borrowed and then took the blessing would be visited forever with no way out.</p>
     */
    public static Gift mercy(int deuda) {
        if (deuda > 0) {
            return new Gift(ABSOLUCION, "§6Absolución",
                    "§7Salda las §f" + deuda + "§7 monedas que debéis");
        }
        return new Gift(PURIFICACION, "§6Purificación", "§7Te quita una aflicción");
    }

    /**
     * The pool for a purity tier, exactly {@link SatelliteOdds.Tier#options()} long.
     *
     * <p>§63e's table reads "Plena: 2 picks, 4 options — pool adds the phoenix charm", which is one
     * more entry than four once <i>restitución</i> is already in. Rather than offer a fifth option
     * the shipped {@code Tier.options()} does not allow, or pick one at random, <b>the óbolo steps
     * aside at Plena</b>: coins are the weakest thing on the list for the one party that played the
     * floor flawlessly, and a menu that changes by rule can be learned in a way one that changes by
     * dice cannot.</p>
     *
     * @param obolus what the óbolo is worth on this floor, already scaled — passed in rather than
     *               read, because the coin curve lives behind the game classes this class refuses
     */
    public static List<Gift> of(SatelliteOdds.Tier tier, int obolus) {
        List<Gift> gifts = new ArrayList<>();
        gifts.add(new Gift(VIGOR, "§aVigor", "§7Cura a toda la partida por completo"));
        if (tier != SatelliteOdds.Tier.PLENA) {
            gifts.add(new Gift(OBOLO, "§eÓbolo de la Orden",
                    "§7+" + obolus + " monedas a la bolsa común"));
        }
        gifts.add(new Gift(RELIQUIA, "§bReliquia", "§7Una pieza de su arsenal, aquí mismo"));
        if (tier != SatelliteOdds.Tier.MENOR) {
            gifts.add(new Gift(RESTITUCION, "§cRestitución",
                    "§7Devuelve los corazones vendidos de toda la partida"));
        }
        if (tier == SatelliteOdds.Tier.PLENA) {
            gifts.add(new Gift(FENIX, "§dPluma de Fénix",
                    "§7Te levanta una vez si caes en este piso"));
        }
        return gifts;
    }

    /** The whole offer: mercy first, then the tier's pool. */
    public static List<Gift> offer(SatelliteOdds.Tier tier, int deuda, int obolus) {
        List<Gift> gifts = new ArrayList<>();
        gifts.add(mercy(deuda));
        gifts.addAll(of(tier, obolus));
        return gifts;
    }

    public static String tierLabel(SatelliteOdds.Tier tier) {
        return switch (tier) {
            case MENOR -> "Gracia menor";
            case MAYOR -> "Gracia mayor";
            case PLENA -> "Gracia plena";
        };
    }
}
