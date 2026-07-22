package es.boffmedia.teras.dungeon.run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * What the curse room is selling, and what it costs to undo.
 *
 * <h2>What this replaces</h2>
 *
 * <p>The curse room used to be a tax with a prize attached: crossing the threshold spent coins —
 * or dealt six magic damage if the purse was short — withdrew ₽, and dropped a reward on a pedestal.
 * Nothing about it was a decision. You could not decline, could not see the price before paying it,
 * and the room played identically whether you were flush or broke.</p>
 *
 * <p>It is now a market. Offers stand on pedestals showing <b>both halves of the trade</b> — the
 * reward, and the affliction it costs — so the argument happens before anyone touches anything. You
 * may take all of them, or walk out having taken none.</p>
 *
 * <h2>Why there is a way back out</h2>
 *
 * <p>Afflictions have no cap by design: the party decides how hard its own run gets. Without a sink
 * that is a one-way ratchet, and a run that took three early has no play left but to endure them.
 * The purge pedestal is the other half of the currency — coins buy one back — which is what turns
 * "how much can we carry" into a shape with an ending: go deep while the floors are cheap, then buy
 * your way out before the boss.</p>
 *
 * <p>The price rises with how many you already carry. Digging out of a hole you chose should get
 * harder the deeper it is, or the sink would simply cancel the decision.</p>
 *
 * <p>Pure — no Minecraft — so the offer draw and the pricing are testable without a server.</p>
 */
public final class AfflictionMarket {

    /** One pedestal: take this drawback, get this many coins. */
    public record Offer(Afliccion afliccion, int reward) {}

    private AfflictionMarket() {}

    /**
     * Draws the offers a curse room shows.
     *
     * <p>Only afflictions the party is not already carrying, because an offer you cannot accept is
     * a dead pedestal. That also means a party deep in afflictions finds a thinner market, which is
     * the correct pressure: the room stops being able to pay you.</p>
     *
     * @param party    the run's party-wide set — {@link Afliccion.Scope#PARTY} offers are filtered
     *                 against it
     * @param personal the clicking party's personal sets, merged; a PERSONAL offer is dropped only
     *                 when <b>everyone</b> already carries it and nobody could accept
     * @param slots    how many pedestals the room has
     * @param baseReward   coins for one affliction at the shallowest depth
     * @param stage        which floor, so a late offer is worth more than an early one
     */
    public static List<Offer> draw(AfflictionSet party, List<AfflictionSet> personal, int slots,
                                   int baseReward, int stage, long seed) {
        List<Afliccion> available = new ArrayList<>();
        for (Afliccion afliccion : Afliccion.all()) {
            if (afliccion.scope() == Afliccion.Scope.PARTY) {
                if (!party.has(afliccion)) {
                    available.add(afliccion);
                }
            } else if (personal.isEmpty() || personal.stream().anyMatch(set -> !set.has(afliccion))) {
                available.add(afliccion);
            }
        }
        Collections.shuffle(available, new Random(seed));
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < Math.min(slots, available.size()); i++) {
            offers.add(new Offer(available.get(i), reward(baseReward, stage)));
        }
        return offers;
    }

    /**
     * What one affliction pays.
     *
     * <p>Scaled by depth because the same drawback costs more the further it has to be carried: a
     * Niebla taken on floor one is paid for over the whole run, one taken on the last floor for a
     * single fight. Paying more for the late one would be backwards — the reward tracks the price,
     * and the price is how long you live with it.</p>
     */
    public static int reward(int baseReward, int stage) {
        // Floor 1 pays full, and each floor deeper pays 15% less, never below a third.
        int scaled = (int) Math.round(baseReward * Math.max(0.34, 1.0 - 0.15 * (stage - 1)));
        return Math.max(1, scaled);
    }

    /**
     * What it costs to shed one, given how many are carried.
     *
     * <p>Counted over everything the player is living with — the party's and their own — because
     * that is what the purge is actually digging them out of.</p>
     */
    public static int purgePrice(int basePrice, int carried) {
        if (carried <= 0) {
            return 0;
        }
        // The first is the advertised price; each one already carried adds 60% of it on top.
        return Math.max(1, (int) Math.round(basePrice * (1.0 + 0.6 * (carried - 1))));
    }
}
