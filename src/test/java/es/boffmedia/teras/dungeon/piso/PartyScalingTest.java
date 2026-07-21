package es.boffmedia.teras.dungeon.piso;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Party size reaching the wave, and the shape of how it does.
 *
 * <p>The rules here are the design, not the tuning: the numbers may move, but a party must never
 * make enemies hit harder, and the two scaling knobs must not compound into something neither
 * intended — which is the same trap {@link Dificultad} was written to avoid.</p>
 */
class PartyScalingTest {

    @Test
    void aSoloRunnerIsTheBaseline() {
        assertEquals(1.0, PartyScaling.count(1), 1e-9);
        assertEquals(1.0, PartyScaling.health(1), 1e-9);
        assertEquals(1.0, PartyScaling.damage(1), 1e-9);
    }

    @Test
    void countCarriesItAndHealthTrails() {
        assertTrue(PartyScaling.count(4) > PartyScaling.health(4),
                "count must move faster than health, or a party fights the same wave with more hp");
        assertTrue(PartyScaling.count(4) >= 1.9, "four players should bring nearly twice the wave");
        assertTrue(PartyScaling.health(4) <= 1.4, "health is the axis dificultad already leans on");
    }

    /** The one hard rule: grouping up must never make the floor hit harder. */
    @Test
    void damageNeverScalesWithTheParty() {
        for (int players = 1; players <= 8; players++) {
            assertEquals(1.0, PartyScaling.damage(players), 1e-9,
                    "damage moved for a party of " + players);
        }
    }

    /** Rooms are authored to a marker count, so the wave has a ceiling however large the party. */
    @Test
    void theCountIsCapped() {
        assertEquals(PartyScaling.MAX_PARTY_FACTOR, PartyScaling.count(8), 1e-9);
        assertTrue(PartyScaling.count(8) <= PartyScaling.MAX_PARTY_FACTOR);
    }

    @Test
    void nonsensePartySizesAreClamped() {
        assertEquals(1.0, PartyScaling.count(0), 1e-9);
        assertEquals(1.0, PartyScaling.count(-3), 1e-9);
    }

    /**
     * The compounding guard: a full party on the deepest tramo must stay well under the product of
     * the two knobs applied naively, for the same reason §11 splits dificultad per axis.
     */
    @Test
    void partyAndDepthDoNotCompoundIntoNonsense() {
        // What both knobs applied flat to all three axes would give — the shape §11 warns about,
        // and the reason each axis has its own curve instead.
        double naive = Math.pow(Dificultad.MAX, 3) * Math.pow(PartyScaling.MAX_PARTY_FACTOR, 3);
        double actual = Dificultad.count(Dificultad.MAX) * PartyScaling.count(8)
                * Dificultad.health(Dificultad.MAX) * PartyScaling.health(8)
                * Dificultad.damage(Dificultad.MAX) * PartyScaling.damage(8);
        assertTrue(actual < naive / 2,
                "the deepest tramo with a full party lands at " + actual + " against a naive "
                        + naive + "; the per-axis split is what keeps them apart");
    }
}
