package es.boffmedia.teras.dungeon.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sheet's arithmetic, and the two properties that keep a ten-floor run readable: multipliers
 * that stack additively, and a ceiling on every axis.
 */
class StatBlockTest {

    @Test
    void everyStatStartsAtItsCatalogueDefault() {
        StatBlock block = new StatBlock();
        assertEquals(0, block.get(Stat.DANO), 1e-9, "damage is what a weapon brings, not a birthright");
        assertEquals(1, block.get(Stat.CADENCIA), 1e-9, "a rate stat at zero would stop the game");
        assertEquals(1, block.get(Stat.VELOCIDAD), 1e-9);
        assertEquals(1, block.get(Stat.ENFRIAMIENTO), 1e-9);
        assertEquals(1.5, block.get(Stat.CONTUNDENCIA), 1e-9);
        assertEquals(6, block.getInt(Stat.CONTENEDORES));
    }

    @Test
    void flatAddsBeforeScaleMultiplies() {
        StatBlock block = new StatBlock().setBase(Stat.DANO, 10).addFlat(Stat.DANO, 5)
                .addScale(Stat.DANO, 0.5);
        assertEquals(22.5, block.get(Stat.DANO), 1e-9, "(10 + 5) * 1.5");
    }

    /**
     * The property the whole design leans on. Multiplicative stacking makes the twentieth pickup
     * worth more than the first ten together, and a player who cannot tell which of their things is
     * doing the work has stopped building and started collecting.
     */
    @Test
    void twoHalfBonusesMakeDoubleAndNotMore() {
        StatBlock block = new StatBlock().setBase(Stat.DANO, 100)
                .addScale(Stat.DANO, 0.5).addScale(Stat.DANO, 0.5);
        assertEquals(200, block.get(Stat.DANO), 1e-9, "additive into one multiplier: x2, never x2.25");
    }

    @Test
    void theTenthCopyIsWorthWhatTheFirstWas() {
        StatBlock one = new StatBlock().setBase(Stat.DANO, 100).addScale(Stat.DANO, 0.1);
        StatBlock ten = new StatBlock().setBase(Stat.DANO, 100);
        for (int i = 0; i < 10; i++) {
            ten.addScale(Stat.DANO, 0.1);
        }
        assertEquals(110, one.get(Stat.DANO), 1e-9);
        assertEquals(200, ten.get(Stat.DANO), 1e-9, "ten identical picks, ten identical steps");
    }

    @Test
    void everyStatIsCapped() {
        for (Stat stat : Stat.values()) {
            StatBlock block = new StatBlock().setBase(stat, Double.MAX_VALUE);
            assertEquals(stat.max(), block.get(stat), 1e-9, stat + " has no ceiling");
            StatBlock floored = new StatBlock().setBase(stat, -Double.MAX_VALUE);
            assertEquals(stat.min(), floored.get(stat), 1e-9, stat + " has no floor");
        }
    }

    /**
     * Two stats may go below zero and they mean different things by it: {@code suerte} is a signed
     * quantity (unlucky is a state), {@code alcance} is an offset onto the weapon's own reach (a
     * negative shortens the swing). Everything else — armour, hearts, cooldown rate — is nonsense
     * below zero, and a new stat quietly joining this set is a design slip worth failing over.
     */
    @Test
    void onlySignedAndOffsetStatsMayGoNegative() {
        for (Stat stat : Stat.values()) {
            boolean signed = stat == Stat.SUERTE || stat == Stat.ALCANCE;
            assertEquals(signed, stat.min() < 0,
                    stat + " disagrees with the signed-stat rule");
        }
    }

    @Test
    void countsAreWholeNumbersThroughEveryAccessor() {
        StatBlock block = new StatBlock().setBase(Stat.CONTENEDORES, 5).addScale(Stat.CONTENEDORES, 0.1);
        assertEquals(block.getInt(Stat.CONTENEDORES), (int) block.get(Stat.CONTENEDORES),
                "the HUD and the maths must not disagree about how many hearts you have");
    }

    @Test
    void resetDropsModifiersAndCopyDoesNotShareThem() {
        StatBlock block = new StatBlock().setBase(Stat.DANO, 10).addFlat(Stat.DANO, 90);
        StatBlock copy = block.copy();
        copy.addFlat(Stat.DANO, 100);
        assertEquals(100, block.get(Stat.DANO), 1e-9, "a copy wrote back into its source");
        assertEquals(200, copy.get(Stat.DANO), 1e-9);

        block.reset();
        assertEquals(0, block.get(Stat.DANO), 1e-9);
    }

    /**
     * Attack rate and movement speed are separate axes with separate names. They were one stat called
     * {@code velocidad}, and the first person to read the panel took it for movement — which it was
     * not. A regression here would put that ambiguity back.
     */
    @Test
    void attackRateAndMovementSpeedAreDistinctStats() {
        assertEquals("cadencia", Stat.CADENCIA.key());
        assertEquals("velocidad", Stat.VELOCIDAD.key());
        StatBlock block = new StatBlock().addScale(Stat.CADENCIA, 1.0);
        assertEquals(2, block.get(Stat.CADENCIA), 1e-9);
        assertEquals(1, block.get(Stat.VELOCIDAD), 1e-9, "swinging faster must not move you faster");
    }

    @Test
    void unknownKeysAreReportedAndKnownOnesAreNot() {
        assertTrue(Stat.problems(List.of("dano", "suerte")).isEmpty());
        assertEquals(1, Stat.problems(List.of("dano", "tears")).size(),
                "a config naming a stat that does not exist must be a line at boot, not a silence");
        assertFalse(Stat.exists("tears"));
        assertEquals(Stat.DANO, Stat.byKey("DANO"), "keys are matched case-insensitively");
    }
}
