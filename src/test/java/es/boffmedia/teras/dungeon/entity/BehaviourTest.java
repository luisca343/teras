package es.boffmedia.teras.dungeon.entity;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour vocabulary. Composable rather than a fixed archetype, because a boss is routinely
 * melee <i>and</i> a caster and "archer" as a type cannot express that.
 *
 * <p>Kept separate from {@link Movement} on purpose: behaviours are goals — what an enemy chooses to
 * do — and movement is the navigation underneath them. A single list holding both would mean
 * "climbs walls" and "shoots webs" were the same kind of statement.</p>
 */
class BehaviourTest {

    @Test
    void rangedBehavioursAreTheOnesThatShoot() {
        assertTrue(Behaviour.RANGED.isRanged());
        assertTrue(Behaviour.VOLLEY.isRanged());
        assertTrue(Behaviour.WEB_SHOT.isRanged());
        assertFalse(Behaviour.MELEE.isRanged());
        assertFalse(Behaviour.LEAP.isRanged());
        assertFalse(Behaviour.BLINK.isRanged());
    }

    @Test
    void parsingIsCaseInsensitiveAndSkipsUnknowns() {
        Set<Behaviour> parsed = Behaviour.parse(List.of("melee", "WEB_SHOT", " leap ", "nonsense"));
        assertEquals(EnumSet.of(Behaviour.MELEE, Behaviour.WEB_SHOT, Behaviour.LEAP), parsed);
    }

    /** A name dropped from the enum must not brick a config written before it went. */
    @Test
    void parsingNullOrEmptyIsSafe() {
        assertTrue(Behaviour.parse(null).isEmpty());
        assertTrue(Behaviour.parse(List.of()).isEmpty());
    }

    // --- how variants compose --------------------------------------------------------------------

    @Test
    void meleeFactoryProducesAPlainGroundEnemy() {
        GeoEnemyVariant guardian = GeoEnemyVariant.of("husk_guardian");
        assertEquals(Movement.GROUND, guardian.movement());
        assertTrue(guardian.has(Behaviour.MELEE));
        assertFalse(guardian.shoots(), "a plain melee enemy must not be given a ranged goal");
    }

    /** All three spiders climb — the one field that makes Infestadas' ledges contested. */
    @Test
    void everySpiderClimbs() {
        for (String id : List.of("cria", "tejedora", "reina_cria")) {
            assertEquals(Movement.CLIMBER, GeoEnemyVariant.of(id).movement(), id);
        }
    }

    @Test
    void webbersShootAndBrawlersDoNot() {
        assertTrue(GeoEnemyVariant.of("tejedora").shoots());
        assertTrue(GeoEnemyVariant.of("tejedora").has(Behaviour.WEB_SHOT));
        assertFalse(GeoEnemyVariant.of("cria").shoots());
        assertTrue(GeoEnemyVariant.of("cria").has(Behaviour.LEAP));
    }

    /** The queen is the composition case: melee and a leap and webbing, not one archetype. */
    @Test
    void theQueenCombinesBehaviours() {
        GeoEnemyVariant queen = GeoEnemyVariant.of("reina_cria");
        assertTrue(queen.has(Behaviour.MELEE));
        assertTrue(queen.has(Behaviour.LEAP));
        assertTrue(queen.has(Behaviour.WEB_SHOT));
        assertTrue(queen.shoots());
    }

    /** Anything that shoots needs numbers to shoot with, or its goal fires blanks. */
    @Test
    void everyShooterHasRangedNumbers() {
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            if (variant.shoots()) {
                assertTrue(variant.rangedDamage() > 0,
                        variant.id() + " shoots but deals no projectile damage");
                assertTrue(variant.rangedCooldown() > 0,
                        variant.id() + " shoots but has no cooldown");
            }
        }
    }

    /**
     * Every variant's model, texture and animation must exist. A variant is authored in two places
     * — this catalog and three files — and only the catalog is compiled, so a renamed asset renders
     * as a missing-texture blob at spawn time rather than failing anywhere useful.
     */
    @Test
    void everyVariantsAssetsExist() {
        java.util.Set<String> missing = new java.util.TreeSet<>();
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            for (String path : List.of(variant.model(), variant.texture(), variant.animation())) {
                String resource = "assets/teras/" + path;
                try (java.io.InputStream in =
                             BehaviourTest.class.getClassLoader().getResourceAsStream(resource)) {
                    if (in == null) {
                        missing.add(variant.id() + " -> " + resource);
                    }
                } catch (Exception e) {
                    missing.add(variant.id() + " -> " + resource + " (" + e + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "variants naming assets that do not exist: " + missing);
    }

    /** An unknown id must fall back rather than return null into a spawn. */
    @Test
    void unknownVariantFallsBack() {
        assertEquals(GeoEnemyVariant.FALLBACK, GeoEnemyVariant.of("no_such_enemy").id());
    }
}
