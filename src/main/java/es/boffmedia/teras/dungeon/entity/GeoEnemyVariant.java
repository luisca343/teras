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

    /** A plain ground melee enemy — what every variant was before behaviours existed. */
    public static GeoEnemyVariant melee(String id, String model, String texture, String animation,
                                        float scale, double health, double damage, double speed,
                                        double armor, double followRange) {
        return new GeoEnemyVariant(id, model, texture, animation, scale, health, damage, speed,
                armor, followRange, Movement.GROUND,
                java.util.EnumSet.of(Behaviour.MELEE), 0f, 0);
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
     * The shipped set. All three share one model and animation file and differ by texture, scale
     * and stats — one authored skeleton stretched across a light/heavy/boss silhouette, which is
     * what keeps a first-party bestiary affordable without an art pipeline.
     */
    private static Map<String, GeoEnemyVariant> builtIn() {
        String model = "geo/dungeon_guardian.geo.json";
        String animation = "animations/dungeon_guardian.animation.json";
        String spider = "geo/dungeon_spider.geo.json";
        String spiderAnim = "animations/dungeon_spider.animation.json";
        return Map.ofEntries(
                Map.entry("husk_guardian", GeoEnemyVariant.melee("husk_guardian", model,
                        "textures/entity/dungeon/guardian_husk.png", animation,
                        1.0f, 24, 5, 0.28, 2, 24)),
                Map.entry("bone_sentinel", GeoEnemyVariant.melee("bone_sentinel", model,
                        "textures/entity/dungeon/guardian_bone.png", animation,
                        1.15f, 44, 8, 0.26, 6, 28)),
                Map.entry("warden_colossus", GeoEnemyVariant.melee("warden_colossus", model,
                        "textures/entity/dungeon/guardian_warden.png", animation,
                        1.6f, 140, 13, 0.23, 10, 36)),

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
                        Movement.GROUND, java.util.EnumSet.of(Behaviour.RANGED), 4f, 45)),

                // Tramo 1's boss and mini-boss. Distinct ids rather than reusing the two above
                // because they are the cave tuning: slower and far tougher, sized to a 21x21 arena
                // rather than to whatever room a wave happens to occupy.
                Map.entry("coloso_guardian", GeoEnemyVariant.melee("coloso_guardian", model,
                        "textures/entity/dungeon/guardian_warden.png", animation,
                        1.7f, 210, 12, 0.22, 12, 40)),
                Map.entry("centinela_hueso", GeoEnemyVariant.melee("centinela_hueso", model,
                        "textures/entity/dungeon/guardian_bone.png", animation,
                        1.25f, 90, 9, 0.25, 8, 32)),

                // The infestation. One rig at three scales, the same trick the guardians use — and
                // all three climb, which is what makes Infestadas' ledges contested where Cuevas'
                // are a safe perch.
                Map.entry("cria", new GeoEnemyVariant("cria", spider,
                        "textures/entity/dungeon/spider_cria.png", spiderAnim,
                        0.7f, 14, 3, 0.32, 0, 24,
                        Movement.CLIMBER, java.util.EnumSet.of(Behaviour.MELEE, Behaviour.LEAP),
                        0f, 0)),
                Map.entry("tejedora", new GeoEnemyVariant("tejedora", spider,
                        "textures/entity/dungeon/spider_tejedora.png", spiderAnim,
                        1.0f, 30, 4, 0.26, 2, 28,
                        Movement.CLIMBER,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.WEB_SHOT), 3f, 70)),
                Map.entry("reina_cria", new GeoEnemyVariant("reina_cria", spider,
                        "textures/entity/dungeon/spider_reina.png", spiderAnim,
                        2.2f, 190, 11, 0.24, 8, 34,
                        Movement.CLIMBER,
                        // CEILING_WEB, not WEB_SHOT: she ascends and drops dense strands rather than
                        // parking at range. LEAP gives her the pounce, MELEE the bite up close.
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.LEAP, Behaviour.CEILING_WEB),
                        6f, 55)));
    }
}
