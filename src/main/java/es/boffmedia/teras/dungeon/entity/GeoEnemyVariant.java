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

    /**
     * A rig's footprint in blocks at scale 1.0, {@code {width, height}}, keyed by model.
     *
     * <p>A hitbox is one square column doing three jobs — hit target, collision, door clearance — so
     * it only approximates a non-square model. Height is the model's top; width is the trunk's
     * left-right breadth (x-extent of non-appendage bones), not its front-to-back depth. A wider box
     * connects on empty air beside the body, the fault a person-shaped column gave the short slime;
     * under-covering a long body (an abdomen, a nose) is the safe direction and what elongated
     * vanilla mobs do. Numbers come from {@code tools/preview_rig.py} and are held to this rule by
     * {@code RigGeometryTest}. The long spiders would under-cover badly here, so they carry extra
     * hit boxes down the body — see {@link EnemyHitboxParts}.</p>
     */
    private static final Map<String, float[]> RIG_FOOTPRINT = Map.ofEntries(
            Map.entry("geo/dungeon_guardian.geo.json", new float[] {1.00f, 2.00f}),
            Map.entry("geo/dungeon_raider.geo.json", new float[] {0.55f, 1.75f}),
            Map.entry("geo/dungeon_mastin.geo.json", new float[] {0.50f, 0.95f}),
            // A bat is a fist with wings; the wingspan is not the hitbox.
            Map.entry("geo/dungeon_murcielago.geo.json", new float[] {0.30f, 0.70f}),
            Map.entry("geo/dungeon_escarabajo.geo.json", new float[] {0.45f, 0.55f}),
            Map.entry("geo/dungeon_lepisma.geo.json", new float[] {0.30f, 0.30f}),
            Map.entry("geo/dungeon_hongo.geo.json", new float[] {0.60f, 0.90f}),
            Map.entry("geo/dungeon_musgo.geo.json", new float[] {0.75f, 0.55f}),
            Map.entry("geo/dungeon_golem.geo.json", new float[] {1.00f, 1.70f}),
            Map.entry("geo/dungeon_limo.geo.json", new float[] {0.75f, 0.70f}),
            Map.entry("geo/dungeon_gran_limo.geo.json", new float[] {1.85f, 1.45f}),
            Map.entry("geo/dungeon_spider_cria.geo.json", new float[] {0.45f, 0.70f}),
            Map.entry("geo/dungeon_spider_tejedora.geo.json", new float[] {0.60f, 0.85f}),
            Map.entry("geo/dungeon_cazadora.geo.json", new float[] {0.50f, 0.95f}),
            Map.entry("geo/dungeon_reina.geo.json", new float[] {0.80f, 1.00f}));

    /** Fallback for a model with no entry: the shape the entity type is sized for. */
    private static final float[] HUMANOID = {0.6f, 1.95f};

    /** No scaled side smaller than this, or the silverfish and bat are unhittable. */
    private static final float MIN_SIDE = 0.4f;

    public float hitboxWidth() {
        return RIG_FOOTPRINT.getOrDefault(model, HUMANOID)[0];
    }

    public float hitboxHeight() {
        return RIG_FOOTPRINT.getOrDefault(model, HUMANOID)[1];
    }

    /** Width the entity is sized to: rig breadth × scale, floored. */
    public float scaledWidth() {
        return Math.max(MIN_SIDE, hitboxWidth() * scale);
    }

    /** Height the entity is sized to: rig top × scale, floored. */
    public float scaledHeight() {
        return Math.max(MIN_SIDE, hitboxHeight() * scale);
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

    /**
     * Ids that were renamed, pointing at what they are called now.
     *
     * <p>A rename breaks every config and every saved entity that still names the old id, and the
     * break is invisible: {@link #of} substitutes the fallback, so Infestadas' floor boss came back
     * as a husk guardian wearing the name "Reina Cria" — the id is what the boss bar and the
     * ability table are keyed on too. Bumping {@code ConfigVersion} only <i>reports</i> that, and
     * the report is a log line during a fight nobody is reading logs through. Healing the id at the
     * boundary means no config has to be resynced for a rename, ever.</p>
     *
     * <p>Renames only. A deleted variant must <b>not</b> be repointed at something else: an id that
     * no longer means anything should be reported to whoever wrote it, not quietly replaced.</p>
     */
    private static final Map<String, String> RENAMED = Map.of("reina_cria", "reina_madre");

    /**
     * The current id for {@code id} — itself, unless it has been renamed. Apply this wherever an
     * enemy id arrives from a config file or from saved NBT, before it is used to look anything up.
     */
    public static String current(String id) {
        return RENAMED.getOrDefault(id, id);
    }

    private static final Map<String, GeoEnemyVariant> BUILT_IN = builtIn();

    public static GeoEnemyVariant of(String id) {
        GeoEnemyVariant variant = BUILT_IN.get(current(id));
        return variant != null ? variant : BUILT_IN.get(FALLBACK);
    }

    /**
     * Whether {@code id} names a real variant. {@link #of} has to fall back — an entity read from
     * NBT under a since-renamed id must still be something — but a spawn table naming a variant
     * that does not exist is a mistake, and the fallback is what hides it: the wrong enemy appears
     * and nothing anywhere says so. Callers that can report check this first.
     */
    public static boolean exists(String id) {
        return BUILT_IN.containsKey(current(id));
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
        // The cave humanoids came off the guardian rig for the same reason the spiders came off one
        // mesh: it existed. Seven variants on one vanilla-proportioned body meant the scavenger, the
        // archer, the crypt heavy and both bosses were the same silhouette at four scales, and the
        // only thing telling them apart was a texture nobody can read across a dark room. They share
        // a bone contract now — same names, one clip vocabulary — and nothing else: the raider
        // tapers upward and leans into its walk at 20 frames a stride, the guardian is a slab under
        // pauldrons at 32.
        String raider = "geo/dungeon_raider.geo.json";
        String raiderAnim = "animations/dungeon_raider.animation.json";
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
                Map.entry("saqueador_cuevas", GeoEnemyVariant.melee("saqueador_cuevas", raider,
                        "textures/entity/dungeon/raider_saqueador.png", raiderAnim,
                        0.95f, 20, 3, 0.30, 1, 24)),
                Map.entry("arquero_gruta", new GeoEnemyVariant("arquero_gruta", raider,
                        "textures/entity/dungeon/raider_arquero.png", raiderAnim,
                        0.95f, 16, 2, 0.27, 0, 32,
                        // BLINK is the archer's answer to being closed on: without it a shooter in
                        // a 21-wide room is a free target the moment anyone reaches it.
                        Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.RANGED, Behaviour.BLINK), 3f, 45)),
                // The second of the three projectile shapes (CONTENIDO §3.1): the heavy line. Where
                // the archer's arc says "keep moving sideways", the crossbow says "do not be
                // standing where you were" — one bolt for more than twice the damage, on a reload
                // long enough to be a window rather than a nuisance.
                //
                // Deliberately no BLINK. The archer's teleport is what stops a shooter being a free
                // target; this one answers being closed on by being worth closing on, which is the
                // only way the reload reads as an opening instead of an inconvenience. It is slower
                // and better armoured to match: the shape that stands its ground.
                Map.entry("ballestero_gruta", new GeoEnemyVariant("ballestero_gruta", raider,
                        "textures/entity/dungeon/raider_ballestero.png", raiderAnim,
                        0.95f, 22, 3, 0.24, 2, 34, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.RANGED), 7f, 100)),

                // The rest of the smugglers' outfit, both on the raider rig: an outfit is a texture
                // set, not a rig set, which is the whole return on a shared bone contract.
                //
                // The scavenger is the first enemy in the bestiary that does not want to fight. It
                // runs, it is carrying the floor's money, and chasing it costs the ground you were
                // holding — the first decision a player makes rather than a question they answer.
                Map.entry("carronero", new GeoEnemyVariant("carronero", raider,
                        "textures/entity/dungeon/raider_carronero.png", raiderAnim,
                        0.9f, 14, 2, 0.36, 0, 26, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.HUIDIZO), 0f, 0)),
                // The lookout. Kill it inside its countdown or it calls, and the wave grows. Its
                // sash is red for the same reason its ability is a timer: the enemy you are meant
                // to reach first has to be findable in a fight that is already happening.
                Map.entry("vigia", new GeoEnemyVariant("vigia", raider,
                        "textures/entity/dungeon/raider_vigia.png", raiderAnim,
                        0.95f, 18, 3, 0.33, 0, 32, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                // The dog. Fast, fragile, and the only thing on the floor that closes a gap the
                // party opened on purpose — which is what stops backing away from being a universal
                // answer to everything Cuevas has.
                Map.entry("mastin", new GeoEnemyVariant("mastin",
                        "geo/dungeon_mastin.geo.json",
                        "textures/entity/dungeon/mastin_contrabandista.png",
                        "animations/dungeon_mastin.animation.json",
                        0.9f, 16, 4, 0.42, 0, 30, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.LEAP), 0f, 0)),

                // The cave's own. Each on its own rig, because §44 is the whole reason this
                // bestiary is not four things at four scales.
                //
                // Ambient, and the only enemy here that is not an enemy: it is outside the kill
                // ledger, it barely hurts, and it exists so that something in Cuevas is above head
                // height. It is also the first FLYER the mod has ever actually flown.
                Map.entry("murcielago", new GeoEnemyVariant("murcielago",
                        "geo/dungeon_murcielago.geo.json",
                        "textures/entity/dungeon/murcielago_gruta.png",
                        "animations/dungeon_murcielago.animation.json",
                        0.9f, 6, 1, 0.30, 0, 16, Movement.FLYER,
                        java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                // The golem's lesson, taught small and taught first. It curls up and its crystals
                // come up with it, so a player learns "this one hurts to hit" on something that
                // cannot punish them for learning it slowly.
                Map.entry("escarabajo", new GeoEnemyVariant("escarabajo",
                        "geo/dungeon_escarabajo.geo.json",
                        "textures/entity/dungeon/escarabajo_geoda.png",
                        "animations/dungeon_escarabajo.animation.json",
                        "textures/entity/dungeon/escarabajo_geoda_glow.png",
                        0.9f, 18, 3, 0.24, 6, 20, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                // Same rig as the swarm, different rock. A retexture is the honest kind of reuse
                // when it is the same animal in a different part of the same cave.
                Map.entry("cristal_rastrero", new GeoEnemyVariant("cristal_rastrero",
                        "geo/dungeon_lepisma.geo.json",
                        "textures/entity/dungeon/cristal_rastrero.png",
                        "animations/dungeon_lepisma.animation.json",
                        "textures/entity/dungeon/cristal_rastrero_glow.png",
                        0.6f, 12, 2, 0.34, 2, 20,
                        Movement.GROUND, java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                // Slow, weak, and the only enemy on the floor that is more dangerous dead. Killing
                // it while standing next to it is the mistake; the sac is top-heavy and luminous so
                // that mistake is available to be avoided.
                Map.entry("hongo_bombardero", new GeoEnemyVariant("hongo_bombardero",
                        "geo/dungeon_hongo.geo.json",
                        "textures/entity/dungeon/hongo_bombardero.png",
                        "animations/dungeon_hongo.animation.json",
                        "textures/entity/dungeon/hongo_bombardero_glow.png",
                        0.9f, 14, 2, 0.16, 0, 14, Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                // ROOTED: it was already there. No chase, no approach, no wander — what it does is
                // punish walking past it, which makes a corridor a decision instead of a distance.
                Map.entry("musgo_agarrador", new GeoEnemyVariant("musgo_agarrador",
                        "geo/dungeon_musgo.geo.json",
                        "textures/entity/dungeon/musgo_agarrador.png",
                        "animations/dungeon_musgo.animation.json",
                        1.0f, 26, 4, 0.0, 4, 8, Movement.ROOTED,
                        java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),

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

                // El Cobrador: what an unpaid deuda sends, two floors on (PISOS §63b). Not part of
                // any piso's roster and never rolled into a wave — the run spawns exactly one, at
                // the floor's own entrance, and killing it is what forgives the debt.
                //
                // Tuned as a hunter rather than a wall: the longest follow range in the bestiary and
                // LEAP to close, because a collector the party can simply outrun is a fee, not a
                // consequence. Its health is deliberately mini-boss and not boss — the beat is
                // "settle up or fight for it", and a fight nobody can win is only the first of those
                // wearing the other's clothes.
                Map.entry("cobrador", new GeoEnemyVariant("cobrador", model,
                        "textures/entity/dungeon/guardian_cobrador.png", animation,
                        1.35f, 130, 10, 0.30, 8, 64, Movement.GROUND,
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
                // The floor's mini-boss, and deliberately the anti-weaver: no web of any kind, the
                // longest reach in the bestiary, and the speed to use it. Infestadas' other two
                // set-pieces hold height and throw silk, so a mini-boss that did the same would be
                // the queen at 60% — which is the mistake the four-spiders-one-mesh bestiary made,
                // committed in behaviour instead of in geometry. She closes. Her rig is long-legged
                // and light-bodied so that is legible before she moves, and her clips run at 18
                // frames against the chaff's 24 so it is unmistakable once she does.
                Map.entry("cazadora", new GeoEnemyVariant("cazadora",
                        "geo/dungeon_cazadora.geo.json",
                        "textures/entity/dungeon/spider_cazadora.png",
                        "animations/dungeon_cazadora.animation.json",
                        "textures/entity/dungeon/spider_cazadora_glow.png",
                        1.35f, 110, 9, 0.34, 3, 40,
                        Movement.CLIMBER,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.LEAP), 0f, 0)),
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
                // Cuevas' answer to the one thing every fight on the floor had in common: walk up
                // and swing. THORNS makes meleeing this a decision rather than the default, and the
                // speed is what keeps that from being unfair — it is slow enough to leave, in rooms
                // that give you somewhere to go. The seams are its telegraph: the only emissive
                // surface outside Infestadas, so "the one you should not hit" is legible in an
                // unlit room before it is in range.
                // Its armour is deliberately moderate. The lesson is "do not hit this", and a golem
                // that is also a wall turns that lesson into a slog: the party learns it in the
                // first three seconds and then spends a minute proving it.
                Map.entry("golem_geoda", new GeoEnemyVariant("golem_geoda",
                        "geo/dungeon_golem.geo.json",
                        "textures/entity/dungeon/golem_geoda.png",
                        "animations/dungeon_golem.animation.json",
                        "textures/entity/dungeon/golem_geoda_glow.png",
                        1.3f, 70, 6, 0.19, 8, 20,
                        Movement.GROUND, java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                // The third projectile shape, and the golem rig's second life. With the gólem
                // retired from every spawn table (CONTENIDO §0) the rig was standing idle, and the
                // ruling on its return was explicit: as a retexture, not a rig. This is that — the
                // crystal itself walking, throwing what the golem only wore.
                //
                // VOLLEY and nothing else, which is what makes it the lob: VolleyGoal marks the
                // ground where you *were* and lands there a moment later, so the answer is to have
                // moved rather than to have blocked. `onlyVolleys` keeps it off RangedAttackGoal,
                // so it never also spits a flat bolt at the target.
                Map.entry("cristalero", new GeoEnemyVariant("cristalero",
                        "geo/dungeon_golem.geo.json",
                        "textures/entity/dungeon/cristalero.png",
                        "animations/dungeon_golem.animation.json",
                        "textures/entity/dungeon/cristalero_glow.png",
                        0.95f, 30, 3, 0.20, 3, 26,
                        Movement.GROUND, java.util.EnumSet.of(Behaviour.VOLLEY), 5f, 90)),
                // Cuevas' mini-boss, replacing the retired gólem in the slot as well as on the rig.
                // MELEE beside VOLLEY on purpose: a caster that only lobs is solved by walking to
                // it, and this one swings when you arrive — into THORNS (DungeonEnemyPacks), which
                // is the shatter-nova written in the shipped vocabulary. So the fight is the golem's
                // old lesson at mini-boss scale, on the enemy that inherited its body: closing is
                // right, closing carelessly is not.
                Map.entry("cristalero_mayor", new GeoEnemyVariant("cristalero_mayor",
                        "geo/dungeon_golem.geo.json",
                        "textures/entity/dungeon/cristalero_mayor.png",
                        "animations/dungeon_golem.animation.json",
                        "textures/entity/dungeon/cristalero_mayor_glow.png",
                        1.6f, 120, 8, 0.21, 7, 34,
                        Movement.GROUND,
                        java.util.EnumSet.of(Behaviour.MELEE, Behaviour.VOLLEY), 8f, 70)),
                Map.entry("limo_cueva", new GeoEnemyVariant("limo_cueva", limo,
                        "textures/entity/dungeon/limo_cueva.png", limoAnim,
                        0.85f, 22, 3, 0.30, 0, 22,
                        Movement.HOPPER, java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                Map.entry("limo_mayor", new GeoEnemyVariant("limo_mayor", limo,
                        "textures/entity/dungeon/limo_mayor.png", limoAnim,
                        1.5f, 60, 5, 0.26, 4, 26,
                        Movement.HOPPER, java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)),
                // Cuevas' boss: the slimes' own animal, grown on what it has eaten. Floor 1 teaches
                // the verbs of the dungeon, and a first boss should teach exactly one of them — this
                // one splits, twice, and the answer is to kill what comes out of it. No caster
                // phase, no adds from off-screen, nothing that has to be explained: everything in
                // the fight is something the floor has already shown you, at a size that matters.
                //
                // Its two SUMMONs are the reason ability firing had to stop being once-per-kind:
                // the second was silently dead, which is exactly the class of fault this system
                // keeps producing — authored, plausible, inert.
                Map.entry("gran_limo", new GeoEnemyVariant("gran_limo",
                        "geo/dungeon_gran_limo.geo.json",
                        "textures/entity/dungeon/gran_limo.png",
                        "animations/dungeon_gran_limo.animation.json",
                        1.5f, 150, 8, 0.23, 4, 30,
                        Movement.HOPPER, java.util.EnumSet.of(Behaviour.MELEE), 0f, 0)));
    }
}
