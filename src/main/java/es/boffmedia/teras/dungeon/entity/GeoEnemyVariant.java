package es.boffmedia.teras.dungeon.entity;

import java.util.List;
import java.util.Map;

/**
 * An animated dungeon enemy: which GeckoLib model, texture and animation set it wears, and the
 * stats it fights with. Pure data — the entity reads it server-side for stats, the renderer reads
 * it client-side for assets, and the id travels between them on the entity's synced data.
 *
 * @param id       variant key, used in {@code enemies.json} and on the wire
 * @param model    {@code geo/…} model path under {@code assets/teras}
 * @param texture  {@code textures/…} path under {@code assets/teras}
 * @param animation {@code animations/…} path under {@code assets/teras}
 * @param glowTexture a second sheet drawn at full brightness over the first, or {@code ""} for
 *                    none. What it is for is the climbers: an enemy on an unlit ceiling is a dark
 *                    shape against dark stone, and eyes that ignore block light are the difference
 *                    between seeing it and being bitten by it. Also the only telegraph the queen's
 *                    ceiling web has — see {@code DungeonGeoEnemyGlowLayer}
 * @param scale    render scale; 1.0 is the model's authored size
 * @param health   max health
 * @param damage   melee damage
 * @param speed    movement speed attribute
 * @param armor    armour attribute
 * @param followRange how far it will chase
 * @param movement    how it navigates — ground, wall-climbing, flying
 * @param behaviours  the goals it composes: MELEE, RANGED, VOLLEY, WEB_SHOT, BLINK, LEAP
 * @param rangedDamage damage of one projectile; only read when a ranged behaviour is present
 * @param rangedCooldown ticks between shots
 */
public record GeoEnemyVariant(
        String id,
        String model,
        String texture,
        String animation,
        String glowTexture,
        float scale,
        double health,
        double damage,
        double speed,
        double armor,
        double followRange,
        Movement movement,
        java.util.Set<Behaviour> behaviours,
        float rangedDamage,
        int rangedCooldown) {

    /**
     * The same variant with no emissive sheet. Most of the bestiary has none — a humanoid lit by
     * the room it stands in needs no help being seen — so this keeps the glow out of every
     * declaration that does not use one.
     */
    public GeoEnemyVariant(String id, String model, String texture, String animation,
                           float scale, double health, double damage, double speed,
                           double armor, double followRange, Movement movement,
                           java.util.Set<Behaviour> behaviours, float rangedDamage,
                           int rangedCooldown) {
        this(id, model, texture, animation, "", scale, health, damage, speed, armor, followRange,
                movement, behaviours, rangedDamage, rangedCooldown);
    }

    /** A plain ground melee enemy — what every variant was before behaviours existed. */
    public static GeoEnemyVariant melee(String id, String model, String texture, String animation,
                                        float scale, double health, double damage, double speed,
                                        double armor, double followRange) {
        return new GeoEnemyVariant(id, model, texture, animation, scale, health, damage, speed,
                armor, followRange, Movement.GROUND,
                java.util.EnumSet.of(Behaviour.MELEE), 0f, 0);
    }

    /** Whether this variant draws a second, full-brightness pass over its base texture. */
    public boolean glows() {
        return glowTexture != null && !glowTexture.isBlank();
    }

    public boolean has(Behaviour behaviour) {
        return behaviours.contains(behaviour);
    }

    /** Whether any of its goals shoot, which is what decides if it needs a ranged attack at all. */
    public boolean shoots() {
        return behaviours.stream().anyMatch(Behaviour::isRanged);
    }

    /** The variant used when an entity carries an id nothing is registered under. */
    public static final String FALLBACK = "husk_guardian";

    private static final Map<String, GeoEnemyVariant> BUILT_IN = builtIn();

    public static GeoEnemyVariant of(String id) {
        GeoEnemyVariant variant = BUILT_IN.get(id);
        return variant != null ? variant : BUILT_IN.get(FALLBACK);
    }

    /**
     * Whether {@code id} names a real variant. {@link #of} has to fall back — an entity read from
     * NBT under a since-renamed id must still be something — but a spawn table naming a variant
     * that does not exist is a mistake, and the fallback is what hides it: the wrong enemy appears
     * and nothing anywhere says so. Callers that can report check this first.
     */
    public static boolean exists(String id) {
        return BUILT_IN.containsKey(id);
    }

    public static List<GeoEnemyVariant> all() {
        return List.copyOf(BUILT_IN.values());
    }

    /**
     * The shipped set. The humanoids share one model and animation file and differ by texture,
     * scale and stats — one authored skeleton stretched across a light/heavy/boss silhouette, which
     * is what keeps a first-party bestiary affordable without an art pipeline.
     *
     * <p>The arachnids share a <b>bone contract</b> rather than a file: same bone names, different
     * geometry, so they can drive the same clips while still reading as different animals. Sharing
     * the mesh too is what the infestation did before, and it made four enemies look like one at
     * four zoom levels. Every rig here is generated by {@code tools/author_enemy_assets.py} and is
     * meant to be replaced file-for-file by an artist without touching this class.</p>
     */
    private static Map<String, GeoEnemyVariant> builtIn() {
        String model = "geo/dungeon_guardian.geo.json";
        String animation = "animations/dungeon_guardian.animation.json";
        // Two rigs on one bone contract, not one rig at two scales. The juvenile and the weaver are
        // the same skeleton with different proportions — a bigger head and shorter legs against a
        // heavy spinneret abdomen — because a player has to be able to tell which of the two spits
        // web before it spits, and 0.7x of the same mesh is only the same enemy further away. The
        // shared contract is what lets both drive one animation file; see tools/author_enemy_assets.
        String criaModel = "geo/dungeon_spider_cria.geo.json";
        String tejedoraModel = "geo/dungeon_spider_tejedora.geo.json";
        String spiderAnim = "animations/dungeon_spider.animation.json";
        String limo = "geo/dungeon_limo.geo.json";
        String limoAnim = "animations/dungeon_limo.animation.json";
        return Map.ofEntries(
                Map.entry("husk_guardian", GeoEnemyVariant.melee("husk_guardian", model,
                        "textures/entity/dungeon/guardian_husk.png", animation,
                        1.0f, 24, 5, 0.28, 2, 24)),
                // LEAP is what makes an elite worth its slot: it closes the gap a party opens by
                // backing off, so kiting it is a decision rather than a default.
                Map.entry("bone_sentinel", new GeoEnemyVariant("bone_sentinel", model,
                        "textures/entity/dungeon/guardian_bone.png", animation,
                        1.15f, 44, 8, 0.26, 6, 28, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.LEAP), 0f, 0)),
                // VOLLEY gives the boss fight a second thing to do: a slam on the ground where you
                // stood, which is answered by moving rather than by out-healing.
                Map.entry("warden_colossus", new GeoEnemyVariant("warden_colossus", model,
                        "textures/entity/dungeon/guardian_warden.png", animation,
                        1.6f, 140, 13, 0.23, 10, 36, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.VOLLEY), 7f, 120)),

                // Cuevas' own pair. The existing guardians read as crypt, so caves get their own
                // chaff and their own shooter on the same rig — a texture each, no new model. The
                // archer is what makes a room's ranged perches worth authoring: it is the only
                // shipped humanoid that wants one.
                Map.entry("saqueador_cuevas", GeoEnemyVariant.melee("saqueador_cuevas", model,
                        "textures/entity/dungeon/raider_saqueador.png", animation,
                        0.95f, 20, 4, 0.30, 1, 24)),
                Map.entry("arquero_gruta", new GeoEnemyVariant("arquero_gruta", model,
                        "textures/entity/dungeon/raider_arquero.png", animation,
                        0.95f, 16, 2, 0.27, 0, 32,
                        // BLINK is the archer's answer to being closed on: without it a shooter in
                        // a 21-wide room is a free target the moment anyone reaches it.
                        Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.RANGED, Behaviour.BLINK), 4f, 45)),

                // Tramo 1's boss and mini-boss. Distinct ids rather than reusing the two above
                // because they are the cave tuning: slower and far tougher, sized to a 21x21 arena
                // rather than to whatever room a wave happens to occupy.
                Map.entry("coloso_guardian", new GeoEnemyVariant("coloso_guardian", model,
                        "textures/entity/dungeon/guardian_warden.png", animation,
                        1.7f, 210, 12, 0.22, 12, 40, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.VOLLEY), 8f, 110)),
                Map.entry("centinela_hueso", new GeoEnemyVariant("centinela_hueso", model,
                        "textures/entity/dungeon/guardian_bone.png", animation,
                        1.25f, 90, 9, 0.25, 8, 32, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.LEAP), 0f, 0)),

                // The infestation. All three climb, which is what makes Infestadas' ledges contested
                // where Cuevas' are a safe perch, and all three glow at the eyes — a climber on an
                // unlit ceiling is otherwise a dark shape against dark stone.
                Map.entry("cria", new GeoEnemyVariant("cria", criaModel,
                        "textures/entity/dungeon/spider_cria.png", spiderAnim,
                        "textures/entity/dungeon/spider_cria_glow.png",
                        0.7f, 14, 3, 0.32, 0, 24,
                        Movement.CLIMBER, java.util.EnumSet.of(Behaviour.MELEE, Behaviour.LEAP),
                        0f, 0)),
                Map.entry("tejedora", new GeoEnemyVariant("tejedora", tejedoraModel,
                        "textures/entity/dungeon/spider_tejedora.png", spiderAnim,
                        "textures/entity/dungeon/spider_tejedora_glow.png",
                        1.0f, 30, 4, 0.26, 2, 28,
                        Movement.CLIMBER,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.WEB_SHOT), 3f, 70)),
                // The floor boss. Her own rig and her own animation file: she is not the weaver
                // scaled up, and a boss walking the same cycle as the chaff around her is most of
                // what makes a boss look small. Named for what she does — the id used to be
                // `reina_cria`, which rendered on her own boss bar as "Reina Cria", the name of the
                // hatchlings swarming her.
                Map.entry("reina_madre", new GeoEnemyVariant("reina_madre",
                        "geo/dungeon_reina.geo.json",
                        "textures/entity/dungeon/spider_reina.png",
                        "animations/dungeon_reina.animation.json",
                        "textures/entity/dungeon/spider_reina_glow.png",
                        2.2f, 190, 11, 0.24, 8, 34,
                        Movement.CLIMBER,
                        // CEILING_WEB, not WEB_SHOT: she ascends and drops dense strands rather than
                        // parking at range. LEAP gives her the pounce, MELEE the bite up close.
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.LEAP, Behaviour.CEILING_WEB),
                        6f, 55)),

                // Cave chaff, first-party. Both of these were CustomNPCs clones wearing a vanilla
                // silverfish and slime, and both were structurally broken: a clone scales its
                // hitbox and its model by the same factor from a player-shaped base, so a small mob
                // could never look bigger than knee high without growing a hitbox too tall to fit
                // through a door — and a clone's walking and its melee damage are the same goal, so
                // a slime could not be made to bounce without also being made harmless. As geo
                // variants both size from their own rig and move however their Movement says.
                // Its own rig, and the reason is the same one that took it off a CustomNPCs clone:
                // it is not a spider. It rode the arachnid skeleton because that rig existed, and a
                // ground-bound cave scavenger sharing an anatomy with the climbers flattened the one
                // contrast Infestadas has against Cuevas.
                Map.entry("lepisma_cueva", new GeoEnemyVariant("lepisma_cueva",
                        "geo/dungeon_lepisma.geo.json",
                        "textures/entity/dungeon/lepisma_cueva.png",
                        "animations/dungeon_lepisma.animation.json",
                        "textures/entity/dungeon/lepisma_cueva_glow.png",
                        0.55f, 10, 2, 0.36, 0, 20,
                        // Deliberately not a climber: the swarm belongs on the floor, and leaving
                        // the ledges to Infestadas' spiders is what keeps the two pisos apart.
                        Movement.GROUND, java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                Map.entry("limo_cueva", new GeoEnemyVariant("limo_cueva", limo,
                        "textures/entity/dungeon/limo_cueva.png", limoAnim,
                        0.85f, 22, 4, 0.30, 0, 22,
                        Movement.HOPPER, java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                Map.entry("limo_mayor", new GeoEnemyVariant("limo_mayor", limo,
                        "textures/entity/dungeon/limo_mayor.png", limoAnim,
                        // The big one is genuinely big now: a geo hitbox scales with the model, so
                        // this is the size it looks, and it still clears a 3-high door at 1.5.
                        1.5f, 60, 7, 0.26, 4, 26,
                        Movement.HOPPER, java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)));
    }
}
