package es.boffmedia.teras.dungeon.entity;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bestiary held to {@link BestiaryAudit}, at build time.
 *
 * <p>Every rule here exists because the fault it catches shipped: {@code VOLLEY} and {@code BLINK}
 * declared with no goal behind them, a variant asking a rig for a clip it did not have, an asset
 * path renamed out from under a variant. None of them failed anything — they read as configured
 * everywhere a person would look and did nothing in play, which is the only kind of bug a bestiary
 * really has.</p>
 */
class BestiaryAuditTest {

    /** The headline: nothing shipped may be broken. */
    @Test
    void theShippedBestiaryAuditsClean() {
        List<BestiaryAudit.Finding> findings = BestiaryAudit.auditAll();
        List<BestiaryAudit.Finding> errors = findings.stream()
                .filter(f -> f.level() == BestiaryAudit.Level.ERROR)
                .toList();
        assertTrue(errors.isEmpty(), "bestiary has errors:\n" + join(errors));
    }

    /**
     * A behaviour in the vocabulary must compose a goal. This is the check that would have caught
     * VOLLEY and BLINK sitting unwired for two releases, and it is why {@code isImplemented} exists
     * as a declaration rather than as something to be verified by reading {@code rebuildGoals}.
     */
    @Test
    void everyBehaviourAndMovementIsImplemented() {
        for (Behaviour behaviour : Behaviour.values()) {
            assertTrue(behaviour.isImplemented(),
                    behaviour + " is in the vocabulary but composes no goal — either wire it in "
                            + "DungeonGeoEnemy.rebuildGoals or take it out of the enum");
        }
        for (Movement movement : Movement.values()) {
            assertTrue(movement.isImplemented(),
                    movement + " is declared but nothing honours it; a variant using it would "
                            + "silently move as GROUND");
        }
    }

    /**
     * Every clip a variant can be asked for must exist in the animation file it plays from. The
     * controller picks a clip from a synched action, so a missing one is silent: the entity keeps
     * whatever it was playing and the move it just made is invisible.
     */
    @Test
    void everyVariantsRigHasEveryClipItCanBeAsked() {
        Set<String> missing = new TreeSet<>();
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            String json = read("assets/teras/" + variant.animation());
            if (json == null) {
                missing.add(variant.id() + " -> " + variant.animation() + " (file missing)");
                continue;
            }
            for (String clip : BestiaryAudit.requiredClips(variant)) {
                if (!json.contains("\"" + clip + "\"")) {
                    missing.add(variant.id() + " needs '" + clip + "' in " + variant.animation());
                }
            }
        }
        assertTrue(missing.isEmpty(), "clips a variant can request but its rig lacks:\n"
                + String.join("\n", missing));
    }

    /** Every asset a variant names has to be in the jar. */
    @Test
    void everyVariantsAssetsExist() {
        Set<String> missing = new TreeSet<>();
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            for (String path : List.of(variant.model(), variant.texture(), variant.animation())) {
                if (read("assets/teras/" + path) == null) {
                    missing.add(variant.id() + " -> " + path);
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing assets:\n" + String.join("\n", missing));
    }

    // --- the cave chaff, which is the reason this pass happened -----------------------------------

    /**
     * The slimes must be hoppers. As CustomNPCs clones they could not be: a clone's walking and its
     * melee damage are the same goal ({@code EntityAIAttackTarget}, which deals its damage in the
     * tick that also paths), so suppressing the walk to make it bounce made it harmless.
     */
    @Test
    void slimesHopAndTheSwarmDoesNot() {
        assertEquals(Movement.HOPPER, GeoEnemyVariant.of("limo_cueva").movement());
        assertEquals(Movement.HOPPER, GeoEnemyVariant.of("limo_mayor").movement());
        assertEquals(Movement.GROUND, GeoEnemyVariant.of("lepisma_cueva").movement());
    }

    /**
     * A hopper needs the jump clip, and a climber the climb loop — the two cases where movement,
     * not a behaviour, drives an animation.
     */
    @Test
    void movementDrivesItsOwnClips() {
        assertTrue(BestiaryAudit.requiredClips(GeoEnemyVariant.of("limo_cueva")).contains("jump"));
        assertTrue(BestiaryAudit.requiredClips(GeoEnemyVariant.of("cria")).contains("climb"));
        assertFalse(BestiaryAudit.requiredClips(GeoEnemyVariant.of("husk_guardian")).contains("climb"));
    }

    /**
     * The big slime has to be genuinely big. This is the number the clone could never have: a clone
     * scales hitbox and model together from a player-shaped base, so a slime model large enough to
     * see came with a hitbox too tall to fit through a 3-block door.
     */
    @Test
    void theBigSlimeIsBiggerAndStillFitsThroughADoor() {
        GeoEnemyVariant big = GeoEnemyVariant.of("limo_mayor");
        GeoEnemyVariant small = GeoEnemyVariant.of("limo_cueva");
        assertTrue(big.scale() > small.scale(), "limo_mayor must be the larger of the two");
        // Base height is EntityInit's 1.95, scaled by the variant. Doors are 3 high.
        assertTrue(1.95f * big.scale() < 3.0f,
                "limo_mayor is too tall to follow the party through a doorway");
    }

    /** A variant whose only ranged behaviour is VOLLEY aims at the ground, not down a bolt line. */
    @Test
    void volleyIsRangedButNotABolt() {
        assertTrue(Behaviour.VOLLEY.isRanged());
        assertEquals("cast", Behaviour.VOLLEY.clip());
        assertEquals("", Behaviour.BLINK.clip(), "blinking plays no clip; it is a reposition");
    }

    private static String join(List<BestiaryAudit.Finding> findings) {
        StringBuilder out = new StringBuilder();
        for (BestiaryAudit.Finding finding : findings) {
            out.append(finding).append('\n');
        }
        return out.toString();
    }

    private static String read(String resource) {
        try (InputStream in = BestiaryAuditTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }
}
