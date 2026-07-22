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

    /**
     * The cave humanoids and the crypt's are not one rig either. Seven variants shared the vanilla
     * player box — the scavenger, the archer, the crypt heavy, both bosses — so the only thing
     * separating a boss from chaff was a scale factor and a texture, neither of which reads across
     * an unlit room. They keep a bone contract; they do not keep a body.
     */
    @Test
    void theCaveHumanoidsAndTheCryptsAreNotOneRig() {
        String raider = GeoEnemyVariant.of("saqueador_cuevas").model();
        String guardian = GeoEnemyVariant.of("husk_guardian").model();
        assertFalse(raider.equals(guardian),
                "the cave raiders are still on the guardian rig: " + raider);
        assertEquals(raider, GeoEnemyVariant.of("arquero_gruta").model(),
                "the archer belongs to the raiders — a shared rig is the point, one rig is not");
        // Timing separates them as much as proportion does: a heavy that walks at the scavenger's
        // cadence is a scavenger wearing armour.
        assertFalse(GeoEnemyVariant.of("saqueador_cuevas").animation()
                        .equals(GeoEnemyVariant.of("husk_guardian").animation()),
                "raider and guardian share a clip file, so they walk at the same speed");
    }

    /**
     * The golem is the floor's answer to "walk up and swing", so the two things that make it that
     * must both be present: damage coming back, and a light on it saying so before it is in range.
     */
    @Test
    void theGeodeGolemPunishesMeleeAndSaysSo() {
        GeoEnemyVariant golem = GeoEnemyVariant.of("golem_geoda");
        assertEquals("golem_geoda", golem.id(), "the golem is not registered");
        assertTrue(golem.glows(), "its crystal seams are its telegraph");
        List<es.boffmedia.teras.dungeon.ability.AbilityDef> defs =
                es.boffmedia.teras.dungeon.encounter.DungeonEnemyPacks.abilities()
                        .get("golem_geoda");
        assertTrue(defs != null && defs.stream().anyMatch(
                        d -> d.kind() == es.boffmedia.teras.dungeon.ability.AbilityKind.THORNS),
                "a geode golem without THORNS is a slow zombie with a light on it");
    }

    /**
     * The huntress is the mini-boss Infestadas never had, and she is not the queen at 60%: her own
     * rig, her own clips, and nothing that throws silk. A mini-boss that rehearsed the boss's tricks
     * would spend the fight's first surprise an hour early.
     */
    @Test
    void theHuntressIsNotTheQueenScaledDown() {
        GeoEnemyVariant huntress = GeoEnemyVariant.of("cazadora");
        GeoEnemyVariant queen = GeoEnemyVariant.of("reina_madre");
        assertFalse(huntress.model().equals(queen.model()));
        assertFalse(huntress.animation().equals(queen.animation()));
        assertFalse(huntress.animation().equals(GeoEnemyVariant.of("tejedora").animation()),
                "she runs the chaff's cycle at the chaff's speed");
        assertFalse(huntress.has(Behaviour.WEB_SHOT) || huntress.has(Behaviour.CEILING_WEB),
                "the hunter webs — which is the weaver's job and the queen's");
        assertTrue(huntress.has(Behaviour.LEAP), "closing the gap is the whole of her");
        assertEquals(Movement.CLIMBER, huntress.movement(), "every spider on this floor climbs");
    }

    /**
     * A hitbox is the rig's footprint, not a person's.
     *
     * <p>The entity type is sized for a humanoid and every variant used to be that column times its
     * scale, so a slime two thirds of a block tall was hittable to nearly two: a swing over empty
     * floor connected, and one at the slime often did not. The upright rigs keep the default because
     * for them it is correct.</p>
     */
    @Test
    void hitboxesFollowTheRigRatherThanTheEntityType() {
        GeoEnemyVariant slime = GeoEnemyVariant.of("limo_cueva");
        assertTrue(slime.hitboxHeight() < 1.0f,
                "a knee-high blob must not carry a person's hitbox: " + slime.hitboxHeight());
        assertTrue(slime.hitboxWidth() > slime.hitboxHeight(),
                "a blob is wider than it is tall, and its hitbox should say so");

        GeoEnemyVariant guardian = GeoEnemyVariant.of("husk_guardian");
        assertEquals(1.95f, guardian.hitboxHeight(), 0.001f,
                "the upright rigs are the shape the entity type is sized for; leave them alone");

        // The golem is the wide one: its trunk, not its reach — arms that hang outside the body are
        // not what a hit lands on.
        assertTrue(GeoEnemyVariant.of("golem_geoda").hitboxWidth() > guardian.hitboxWidth());
    }

    /**
     * Two abilities of one kind on one enemy must sit at different health thresholds.
     *
     * <p>{@code AbilityEngine} fires each kind once per threshold, so two SUMMONs at the same
     * percentage are one summon and one line of config that does nothing. Before the threshold was
     * part of that key it was worse — the <i>second</i> SUMMON never fired at all, whatever its
     * percentage — which is how the splitting boss was written twice and split once.</p>
     */
    @Test
    void repeatedAbilitiesSitAtDistinctThresholds() {
        Set<String> problems = new TreeSet<>();
        es.boffmedia.teras.dungeon.encounter.DungeonEnemyPacks.abilities()
                .forEach((enemy, defs) -> {
                    Set<String> seen = new TreeSet<>();
                    for (es.boffmedia.teras.dungeon.ability.AbilityDef def : defs) {
                        String key = def.kind() + "@" + Math.round(def.param("healthPct", -1) * 100);
                        if (!seen.add(key)) {
                            problems.add(enemy + " declares " + def.kind()
                                    + " twice at the same threshold; only one of them fires");
                        }
                    }
                });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** The floor-1 boss splits twice, into the chaff its floor is already made of. */
    @Test
    void theGreatSlimeSplitsTwice() {
        List<es.boffmedia.teras.dungeon.ability.AbilityDef> defs =
                es.boffmedia.teras.dungeon.encounter.DungeonEnemyPacks.abilities().get("gran_limo");
        assertTrue(defs != null, "gran_limo has no abilities, so the boss is a health bar that hops");
        List<es.boffmedia.teras.dungeon.ability.AbilityDef> summons = defs.stream()
                .filter(d -> d.kind() == es.boffmedia.teras.dungeon.ability.AbilityKind.SUMMON)
                .toList();
        assertEquals(2, summons.size(), "the whole fight is that it splits, twice");
        for (var summon : summons) {
            assertEquals("geo:limo_cueva", summon.arg(),
                    "it should split into the slime the floor already fields, not something new");
        }
        assertFalse(GeoEnemyVariant.of("gran_limo").glows(),
                "a glow in this bestiary means 'there is a rule here'; a boss under a boss bar in a "
                        + "lit arena is already announced");
    }

    /**
     * The cave's own fauna are five animals, not one animal five times.
     *
     * <p>The crystal crawler is the deliberate exception and the shape reuse is meant to take: it is
     * the silverfish rig in the geode's colours, because it is the same animal living in a different
     * part of the same rock. Sharing a rig is honest when the two things are the same thing.</p>
     */
    @Test
    void theCaveFaunaAreDistinctAnimals() {
        Set<String> models = new TreeSet<>();
        for (String id : List.of("murcielago", "escarabajo", "hongo_bombardero",
                "musgo_agarrador", "mastin")) {
            assertTrue(GeoEnemyVariant.exists(id), id + " is not registered");
            models.add(GeoEnemyVariant.of(id).model());
        }
        assertEquals(5, models.size(), "cave fauna sharing a rig: " + models);
        assertEquals(GeoEnemyVariant.of("lepisma_cueva").model(),
                GeoEnemyVariant.of("cristal_rastrero").model(),
                "the crawler is the silverfish in another colour, and should share its rig");
        assertFalse(GeoEnemyVariant.of("cristal_rastrero").texture()
                        .equals(GeoEnemyVariant.of("lepisma_cueva").texture()),
                "...but not its sheet, or it is the same enemy twice");
    }

    /**
     * A movement mode is only worth declaring if something honours it. Both of these were declared
     * long before they moved anything: {@code FLYER} had navigation and a ground move control, so a
     * flyer planned a route through the air and walked the floor under it.
     */
    @Test
    void theNewMovementModesAreActuallyUsed() {
        assertEquals(Movement.FLYER, GeoEnemyVariant.of("murcielago").movement());
        assertEquals(Movement.ROOTED, GeoEnemyVariant.of("musgo_agarrador").movement());
        assertEquals(0.0, GeoEnemyVariant.of("musgo_agarrador").speed(), 0.0001,
                "a ROOTED variant declaring a speed has written a number nothing reads");
        for (Movement movement : Movement.values()) {
            assertTrue(movement.isImplemented(), movement + " is declared but honoured by nothing");
        }
    }

    /** The scavenger's whole design: it does not fight, and it is carrying something. */
    @Test
    void theScavengerFleesAndIsWorthChasing() {
        GeoEnemyVariant scavenger = GeoEnemyVariant.of("carronero");
        assertTrue(scavenger.has(Behaviour.HUIDIZO));
        assertFalse(scavenger.has(Behaviour.MELEE),
                "an enemy that flees and closes does neither");
        List<es.boffmedia.teras.dungeon.ability.AbilityDef> defs =
                es.boffmedia.teras.dungeon.encounter.DungeonEnemyPacks.abilities().get("carronero");
        assertTrue(defs != null && defs.stream().anyMatch(
                        d -> d.kind() == es.boffmedia.teras.dungeon.ability.AbilityKind.TESORO),
                "without a reason to chase it, an enemy that runs away only wastes your time");
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
