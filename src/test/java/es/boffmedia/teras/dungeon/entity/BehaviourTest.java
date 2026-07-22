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
        // CEILING_WEB throws webbing but ascends first — deliberately not "ranged", so the queen is
        // never handed a RangedAttackGoal that would park her at distance instead of closing.
        assertFalse(Behaviour.CEILING_WEB.isRanged());
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
        for (String id : List.of("cria", "tejedora", "reina_madre")) {
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
        GeoEnemyVariant queen = GeoEnemyVariant.of("reina_madre");
        assertTrue(queen.has(Behaviour.MELEE));
        assertTrue(queen.has(Behaviour.LEAP));
        // She webs from overhead (CEILING_WEB), not from range (WEB_SHOT) — which is why she closes
        // to bite and pounce rather than parking, and why she is not "ranged".
        assertTrue(queen.has(Behaviour.CEILING_WEB));
        assertFalse(queen.shoots());
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

    /**
     * A renamed id resolves to what it is called now, and reports as existing. This is the fallback
     * above turned into a bug when the id is one <i>we</i> retired: Infestadas' boss pool on every
     * server predating the rename says {@code reina_cria}, and without the migration the floor boss
     * comes back a husk guardian named "Reina Cria" — a fight that looks configured, in a config no
     * one has reason to reopen.
     */
    @Test
    void aRenamedVariantResolvesToItsCurrentId() {
        assertEquals("reina_madre", GeoEnemyVariant.current("reina_cria"));
        assertEquals("reina_madre", GeoEnemyVariant.of("reina_cria").id());
        assertTrue(GeoEnemyVariant.exists("reina_cria"),
                "an old id a shipped config still names must not read as unknown");
        // Only renames are healed: an id nothing was ever renamed to is left alone, so a typo is
        // still reported instead of being silently pointed somewhere.
        assertEquals("no_such_enemy", GeoEnemyVariant.current("no_such_enemy"));
        assertFalse(GeoEnemyVariant.exists("no_such_enemy"));
    }

    /**
     * Every clip the animation controller can request must exist in the file it plays it from. The
     * controller picks a clip by action ({@code shoot}, {@code jump}) or state ({@code climb}), and a
     * clip named there but absent from the {@code .animation.json} T-poses the model at spawn — the
     * same class of two-places-drift bug as {@link #everyVariantsAssetsExist()}, but for clips.
     */
    @Test
    void animationFilesCarryEveryClipTheControllerPlays() throws Exception {
        // Guardians only ever bite or shoot; spiders climb and pounce as well.
        assertClips("animations/dungeon_guardian.animation.json", "idle", "walk", "attack", "shoot");
        assertClips("animations/dungeon_spider.animation.json",
                "idle", "walk", "attack", "shoot", "climb", "jump");
    }

    private static void assertClips(String path, String... clips) throws Exception {
        String json;
        try (java.io.InputStream in = BehaviourTest.class.getClassLoader()
                .getResourceAsStream("assets/teras/" + path)) {
            assertTrue(in != null, "missing animation file " + path);
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (String clip : clips) {
            assertTrue(json.contains("\"" + clip + "\""),
                    path + " is missing the '" + clip + "' clip the controller can play");
        }
    }
}
