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
            // The emissive sheet is the one asset with no fallback worth having: RenderType.eyes on
            // a texture that is not there draws the missing-texture checker at full brightness, so
            // an enemy meant to be readable in the dark becomes the brightest thing in the room.
            if (variant.glows() && read("assets/teras/" + variant.glowTexture()) == null) {
                missing.add(variant.id() + " -> " + variant.glowTexture() + " (glow)");
            }
        }
        assertTrue(missing.isEmpty(), "missing assets:\n" + String.join("\n", missing));
    }

    /**
     * A summoner has to have the clip its summon plays. {@code AbilityEngine.summon} fires
     * {@code Action.CAST} on any animated enemy, and abilities are data — they are attached by id
     * in {@code enemies.json}, not declared on the variant — so {@link BestiaryAudit} cannot derive
     * this the way it derives the rest. The shipped table is what gets checked, which is the same
     * bargain the rest of the bestiary makes.
     */
    @Test
    void everySummonerHasACastClip() {
        Set<String> missing = new TreeSet<>();
        es.boffmedia.teras.dungeon.encounter.DungeonEnemyPacks.abilities().forEach((id, defs) -> {
            if (!GeoEnemyVariant.exists(id)) {
                return;   // a CustomNPCs clone; it has no rig to drive
            }
            boolean summons = defs.stream().anyMatch(
                    d -> d.kind() == es.boffmedia.teras.dungeon.ability.AbilityKind.SUMMON);
            if (!summons) {
                return;
            }
            String json = read("assets/teras/" + GeoEnemyVariant.of(id).animation());
            if (json == null || !json.contains("\"cast\"")) {
                missing.add(id + " summons but its rig has no 'cast' clip");
            }
        });
        assertTrue(missing.isEmpty(), String.join("\n", missing));
    }

    /**
     * A clip may only animate bones its rig actually has, and no keyframe may sit past the end of
     * the clip it belongs to.
     *
     * <p>Both are silent in play. GeckoLib drops an animation targeting a bone that is not in the
     * model without a word, so the part simply does not move; and a key past the end of a looping
     * clip is never reached, which shows up as a hitch at the seam once per loop and looks like
     * stutter rather than like data. The generator gets both right by construction — one bone list
     * writes the geometry and the clips — but the whole point of the rigs being plain files is that
     * an artist can replace one, and replacing one is exactly when the two drift apart.</p>
     */
    @Test
    void everyAnimatedBoneExistsOnTheRigItPlaysOn() {
        Set<String> problems = new TreeSet<>();
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            String geoJson = read("assets/teras/" + variant.model());
            String animJson = read("assets/teras/" + variant.animation());
            if (geoJson == null || animJson == null) {
                continue;   // everyVariantsAssetsExist owns the missing-file case
            }
            Set<String> bones = new TreeSet<>();
            com.google.gson.JsonParser.parseString(geoJson).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonArray("bones")
                    .forEach(b -> bones.add(b.getAsJsonObject().get("name").getAsString()));

            com.google.gson.JsonParser.parseString(animJson).getAsJsonObject()
                    .getAsJsonObject("animations").asMap().forEach((clip, node) -> {
                        com.google.gson.JsonObject body = node.getAsJsonObject();
                        double length = body.get("animation_length").getAsDouble();
                        if (!body.has("bones")) {
                            return;
                        }
                        body.getAsJsonObject("bones").asMap().forEach((bone, tracks) -> {
                            if (!bones.contains(bone)) {
                                problems.add(variant.id() + ": " + clip + " animates '" + bone
                                        + "', which " + variant.model() + " does not have");
                            }
                            tracks.getAsJsonObject().asMap().forEach((kind, track) -> {
                                if (!track.isJsonObject()) {
                                    return;   // a single static value, not a keyed track
                                }
                                for (String at : track.getAsJsonObject().keySet()) {
                                    if (Double.parseDouble(at) > length + 1e-6) {
                                        problems.add(variant.id() + ": " + clip + "." + bone + "."
                                                + kind + " has a key at " + at
                                                + ", past the clip end " + length);
                                    }
                                }
                            });
                        });
                    });
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * An emissive sheet may light almost nothing.
     *
     * <p>{@code RenderType.eyes} draws at full brightness through walls and weather, so every lit
     * texel is a shape floating in a dark room. A handful is a pair of eyes; a face's worth is a
     * glowing box, and a glowing box is indistinguishable from a bug — the queen's spinneret glowed
     * for one revision and became the automatic suspect for every stray light anyone saw near a
     * spider. The cap is deliberately far above the eight eye-cubes this ships (48 texels) and far
     * below one face of anything larger.</p>
     */
    @Test
    void emissiveSheetsLightOnlyPinpricks() {
        Set<String> problems = new TreeSet<>();
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            if (!variant.glows()) {
                continue;
            }
            byte[] png = readBytes("assets/teras/" + variant.glowTexture());
            if (png == null) {
                continue;   // everyVariantsAssetsExist owns the missing-file case
            }
            long opaque = countOpaque(png);
            if (opaque > 120) {
                problems.add(variant.id() + ": " + variant.glowTexture() + " lights " + opaque
                        + " texels. That is a glowing surface, not a pair of eyes");
            }
            if (opaque == 0) {
                problems.add(variant.id() + ": " + variant.glowTexture()
                        + " is fully transparent — the layer draws nothing at all");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Opaque pixel count of an 8-bit RGBA PNG, decoded far enough to read the alpha channel. */
    private static long countOpaque(byte[] png) {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(
                    new java.io.ByteArrayInputStream(png));
            long opaque = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) {
                        opaque++;
                    }
                }
            }
            return opaque;
        } catch (Exception e) {
            return 0;
        }
    }

    private static byte[] readBytes(String resource) {
        try (InputStream in = BestiaryAuditTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The infestation must not be one mesh at four scales. That is what it was, and the cost was a
     * floor whose chaff, elite, boss and swarm were the same silhouette — so nothing could be
     * identified before it acted, and the silverfish read as a small spider.
     */
    @Test
    void theInfestationDoesNotShareOneMesh() {
        Set<String> models = new TreeSet<>();
        for (String id : List.of("cria", "tejedora", "reina_madre", "lepisma_cueva")) {
            models.add(GeoEnemyVariant.of(id).model());
        }
        assertEquals(4, models.size(),
                "Infestadas' four enemies share a model file: " + models);
        // The silverfish is the one that is not even the same animal, so it shares no clips either.
        assertFalse(GeoEnemyVariant.of("lepisma_cueva").animation()
                        .equals(GeoEnemyVariant.of("cria").animation()),
                "the silverfish is on the arachnid animation set");
    }

    /** The boss carries a boss's kit; she was the only one in the bestiary carrying none. */
    @Test
    void theQueenHasAbilities() {
        List<es.boffmedia.teras.dungeon.ability.AbilityDef> defs =
                es.boffmedia.teras.dungeon.encounter.DungeonEnemyPacks.abilities()
                        .get("reina_madre");
        assertTrue(defs != null && !defs.isEmpty(), "reina_madre has no abilities");
        assertTrue(defs.stream().anyMatch(
                        d -> d.kind() == es.boffmedia.teras.dungeon.ability.AbilityKind.SUMMON
                                && d.arg().equals("geo:cria")),
                "the broodmother should summon the hatchlings her floor is already full of");
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

    /**
     * The ceiling web names the clip it actually plays. {@code SpiderCeilingWebGoal} fires
     * {@code Action.SHOOT} for every strand it drops, so this used to declare "jump" — a clip the
     * move never plays — and left the clip it does play out of the required set entirely. It only
     * escaped notice because the one rig carrying the behaviour happened to have both.
     */
    @Test
    void theCeilingWebDeclaresTheClipItPlays() {
        assertEquals("shoot", Behaviour.CEILING_WEB.clip());
        assertEquals("jump", Behaviour.LEAP.clip());
        assertTrue(BestiaryAudit.requiredClips(GeoEnemyVariant.of("reina_madre")).contains("shoot"),
                "the queen webs from overhead, so her rig owes a shoot clip");
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
