package es.boffmedia.teras.dungeon.encounter;

import es.boffmedia.teras.dungeon.ability.AbilityDef;
import es.boffmedia.teras.dungeon.ability.AbilityKind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in dungeon bestiary, installed into CustomNPCs by
 * {@code /teras dungeon enemigos instalar}. Plain data: no Minecraft, no CustomNPCs.
 *
 * <p>Skins ship with Teras (see {@link #skin}), so a fresh server has a working bestiary on day
 * one. They are meant to be edited afterwards in CustomNPCs' own NPC editor — installing writes
 * clones, it does not own them, and re-installing only fills in the ones that are missing unless
 * explicitly asked to overwrite.</p>
 *
 * <p>The curve is deliberate: {@code CHAFF} dies in a couple of hits and comes in numbers,
 * {@code ELITE} punishes standing still, and the bosses are slow but hit hard enough that a room
 * has to be fought rather than walked through.</p>
 */
public final class DungeonEnemyPacks {
    private DungeonEnemyPacks() {}

    /** Clone tab the installer writes into; kept off tab 0 so it never mixes with hand-made NPCs. */
    public static final int TAB = 7;

    /**
     * Skins live in Teras, not vanilla. CustomNPCs renders NPCs on the <b>player</b> model, which
     * samples the left arm at (32,48) and the left leg at (16,48); most vanilla humanoid mob
     * textures never fill those — skeleton/stray/wither_skeleton are still 64x32, and zombie/husk
     * are 64x64 with an empty bottom-left quadrant, because their own models mirror the right
     * limbs instead. Used directly they render an NPC missing its left arm and leg.
     */
    private static String skin(String name) {
        return "teras:textures/entity/dungeon/npc/" + name + ".png";
    }

    /**
     * Rank-and-file: cheap, fast, spawned several at a time. The skeleton guard shoots — a wave of
     * pure melee lets a party hold one doorway and win, so every floor gets something that reaches.
     */
    public static List<EnemyPreset> chaff() {
        return List.of(
                EnemyPreset.melee("esqueleto_guardia", "Guardia Esquelético", skin("guardia_esqueleto"),
                        14, 3, 18, 2, 1, 16, 5, 5,
                        "minecraft:bow", "", "minecraft:bone", 45, 3, 6, "")
                        .ranged(3, 12, 1, 30, 0, 0),
                EnemyPreset.melee("zombi_carcelero", "Carcelero Zombi", skin("carcelero_zombi"),
                        20, 4, 22, 2, 2, 14, 4, 5,
                        "minecraft:wooden_axe", "minecraft:leather_helmet",
                        "minecraft:rotten_flesh", 60, 3, 7, ""),
                EnemyPreset.melee("husk_arenoso", "Husk de Arena", skin("husk_arena"),
                        18, 4, 20, 2, 1, 15, 5, 5,
                        "", "", "minecraft:sand", 30, 2, 5, ""),
                EnemyPreset.melee("ahogado_pozo", "Ahogado del Pozo", skin("ahogado_pozo"),
                        16, 3, 16, 3, 1, 16, 5, 5,
                        "minecraft:trident", "", "minecraft:prismarine_shard", 35, 4, 8, "")
                        .traits(0.2f, 0f, true, 0));
    }

    /** Elites: fewer per room, hit harder, worth the detour. */
    public static List<EnemyPreset> elites() {
        return List.of(
                EnemyPreset.melee("esqueleto_glacial", "Esqueleto Glacial", skin("esqueleto_glacial"),
                        30, 6, 16, 3, 2, 20, 6, 5,
                        "minecraft:bow", "minecraft:chainmail_helmet",
                        "minecraft:blue_ice", 40, 8, 14, "")
                        .ranged(5, 14, 2, 40, 2, 4)
                        .traits(0.3f, 0f, false, 0),
                EnemyPreset.melee("piglin_saqueador", "Piglin Saqueador", skin("piglin_saqueador"),
                        34, 7, 18, 2, 3, 18, 6, 5,
                        "minecraft:golden_axe", "minecraft:golden_helmet",
                        "minecraft:gold_ingot", 50, 8, 15, "")
                        .traits(0f, 0.15f, true, 0),
                EnemyPreset.melee("vindicador_celda", "Vindicador de Celda", skin("vindicador_celda"),
                        38, 8, 20, 2, 2, 20, 6, 5,
                        "minecraft:iron_axe", "", "minecraft:emerald", 35, 10, 18, "")
                        .traits(0f, 0.25f, false, 0));
    }

    /** Mini-bosses: one per room, slow and heavy, and each wears a boss bar. */
    public static List<EnemyPreset> miniBosses() {
        return List.of(
                EnemyPreset.melee("bruto_mazmorra", "Bruto de la Mazmorra", skin("bruto_mazmorra"),
                        90, 11, 24, 3, 4, 24, 5, 6,
                        "minecraft:golden_axe", "minecraft:iron_helmet",
                        "minecraft:gold_block", 100, 30, 45, "husk_guardian")
                        .traits(0.25f, 0.2f, false, 1),
                EnemyPreset.melee("esqueleto_wither_menor", "Centinela del Wither", skin("centinela_wither"),
                        80, 10, 20, 3, 3, 24, 6, 6,
                        "minecraft:iron_sword", "", "minecraft:coal_block", 100, 30, 45, "")
                        .traits(0.35f, 0.1f, false, 1)
                        .meleeEffect(20, 4));
    }

    /** Floor bosses: the trapdoor is behind these. */
    public static List<EnemyPreset> bosses() {
        return List.of(
                EnemyPreset.melee("senor_celdas", "Señor de las Celdas", skin("senor_celdas"),
                        180, 14, 22, 3, 4, 32, 6, 7,
                        "minecraft:diamond_sword", "minecraft:diamond_helmet",
                        "minecraft:diamond", 100, 80, 120, "bone_sentinel")
                        .traits(0.4f, 0.25f, false, 1)
                        .meleeEffect(18, 5),
                EnemyPreset.melee("coloso_hueso", "Coloso de Hueso", skin("coloso_hueso"),
                        220, 16, 28, 4, 6, 32, 4, 8,
                        "minecraft:netherite_axe", "", "minecraft:bone_block", 100, 90, 140, "warden_colossus")
                        .traits(0.5f, 0.3f, false, 2));
    }

    /**
     * What each enemy does beyond swinging, as the shipped default for {@code enemies.json}'s
     * {@code abilities} block. Only the enemies that earn one carry an ability: chaff stays chaff,
     * and a floor where everything has a trick has none.
     *
     * <p>Bosses get two phases — adds first, then enrage lower down — so the fight has a shape
     * instead of a single health bar.</p>
     */
    public static Map<String, List<AbilityDef>> abilities() {
        Map<String, List<AbilityDef>> table = new LinkedHashMap<>();

        table.put("husk_arenoso", List.of(
                new AbilityDef(AbilityKind.ON_HIT, "minecraft:slowness",
                        Map.of("duration", 60.0, "amplifier", 0.0))));
        table.put("ahogado_pozo", List.of(
                new AbilityDef(AbilityKind.ON_HIT, "minecraft:mining_fatigue",
                        Map.of("duration", 80.0, "amplifier", 0.0))));

        table.put("esqueleto_glacial", List.of(
                new AbilityDef(AbilityKind.ON_HIT, "minecraft:slowness",
                        Map.of("duration", 100.0, "amplifier", 1.0))));
        table.put("piglin_saqueador", List.of(
                new AbilityDef(AbilityKind.ENRAGE, "",
                        Map.of("healthPct", 0.35, "speedMult", 0.35, "damageMult", 0.4))));
        table.put("vindicador_celda", List.of(
                new AbilityDef(AbilityKind.CLEAVE, "",
                        Map.of("radius", 3.0, "fraction", 0.5))));

        table.put("bruto_mazmorra", List.of(
                new AbilityDef(AbilityKind.CLEAVE, "", Map.of("radius", 3.5, "fraction", 0.6)),
                new AbilityDef(AbilityKind.ENRAGE, "",
                        Map.of("healthPct", 0.4, "speedMult", 0.3, "damageMult", 0.5))));
        table.put("esqueleto_wither_menor", List.of(
                new AbilityDef(AbilityKind.ON_HIT, "minecraft:wither",
                        Map.of("duration", 80.0, "amplifier", 0.0)),
                new AbilityDef(AbilityKind.THORNS, "", Map.of("fraction", 0.2))));

        table.put("senor_celdas", List.of(
                new AbilityDef(AbilityKind.SUMMON, "cnpc:" + TAB + ":esqueleto_guardia",
                        Map.of("healthPct", 0.6, "count", 3.0, "spread", 2.5)),
                new AbilityDef(AbilityKind.ENRAGE, "",
                        Map.of("healthPct", 0.3, "speedMult", 0.35, "damageMult", 0.5)),
                new AbilityDef(AbilityKind.CLEAVE, "", Map.of("radius", 3.5, "fraction", 0.5))));
        table.put("coloso_hueso", List.of(
                new AbilityDef(AbilityKind.SUMMON, "cnpc:" + TAB + ":zombi_carcelero",
                        Map.of("healthPct", 0.65, "count", 2.0, "spread", 3.0)),
                new AbilityDef(AbilityKind.THORNS, "", Map.of("fraction", 0.3)),
                new AbilityDef(AbilityKind.ENRAGE, "",
                        Map.of("healthPct", 0.25, "speedMult", 0.4, "damageMult", 0.6))));

        // Infestadas' boss, on the same three-ability shape the other two floor bosses use. She was
        // the only boss in the bestiary carrying none, which left the fight a health bar that walks
        // at you and webs the ceiling on a cooldown.
        //
        // SUMMON spawns `geo:cria` — the hatchlings the floor is already full of and the ones its
        // nests crack open, so the adds need no new enemy and read immediately as hers. Adds enter
        // the room's kill ledger, so the room cannot clear while they live.
        // The spore sac. Everything it is worth is in ESTALLIDO, which is the first ability in the
        // system that fires after its owner is dead: it is slow, it is weak, and killing it from
        // arm's length is the mistake. Radius is small on purpose — one step is the whole answer,
        // and a cloud you cannot walk out of is not a lesson, it is a tax.
        table.put("hongo_bombardero", List.of(
                new AbilityDef(AbilityKind.ESTALLIDO, "minecraft:poison",
                        Map.of("radius", 3.0, "magnitud", 2.0, "duration", 100.0))));

        // The scavenger is carrying the floor's money and TESORO is where that becomes true. Without
        // it, an enemy that runs away is only an enemy that wastes your time.
        table.put("carronero", List.of(
                new AbilityDef(AbilityKind.TESORO, "", Map.of("magnitud", 12.0))));

        // The lookout: eight seconds from first seeing you, then it calls, and it can call again.
        // Reinforcements are saqueadores — the wave it grows is the wave the floor already has, so
        // the threat is the count and not a surprise.
        table.put("vigia", List.of(
                new AbilityDef(AbilityKind.ALERTA, "geo:saqueador_cuevas",
                        Map.of("ticks", 160.0, "count", 2.0, "spread", 3.0))));

        // Its curl is an animation; this is what the curl means. Small, because the beetle is where
        // the lesson is free — the golem is where it costs.
        table.put("escarabajo", List.of(
                new AbilityDef(AbilityKind.THORNS, "", Map.of("fraction", 0.2))));

        // Cuevas' boss, and the whole fight: it splits at two thirds and again at a third, into the
        // slime the floor is already full of. Nothing else — no phase change to read, no add from
        // somewhere else in the room. A first boss is where a player finds out what a boss fight in
        // this dungeon *is*, and one idea executed cleanly teaches that better than three.
        //
        // Two SUMMONs on one enemy only became possible when ability firing stopped being keyed on
        // the kind alone; before that the second was config that looked authored and did nothing.
        // ENRAGE is the last quarter and spends itself on speed: a slime that gets frantic, not one
        // that starts hitting like the next floor down.
        table.put("gran_limo", List.of(
                new AbilityDef(AbilityKind.SUMMON, "geo:limo_cueva",
                        Map.of("healthPct", 0.66, "count", 2.0, "spread", 3.0)),
                new AbilityDef(AbilityKind.SUMMON, "geo:limo_cueva",
                        Map.of("healthPct", 0.33, "count", 3.0, "spread", 3.5)),
                new AbilityDef(AbilityKind.ENRAGE, "",
                        Map.of("healthPct", 0.25, "speedMult", 0.3, "damageMult", 0.15))));

        // The geode's whole reason to exist: hitting it hurts. A third of the melee damage comes
        // back, which is enough to change what a party does rather than only what it spends —
        // nothing else in Cuevas asks a question that swinging harder does not answer.
        table.put("golem_geoda", List.of(
                new AbilityDef(AbilityKind.THORNS, "", Map.of("fraction", 0.35))));

        // Infestadas' mini-boss. CLEAVE is her pounce landing on whoever was standing together, and
        // ENRAGE spends almost all of itself on speed — a wounded hunter gets harder to escape
        // rather than harder to trade with, which is the one thing the queen's enrage does not do.
        // No SUMMON: adds are the queen's signature, and a mini-boss that opened with them would
        // spend the boss's first surprise an hour early.
        table.put("cazadora", List.of(
                new AbilityDef(AbilityKind.CLEAVE, "", Map.of("radius", 3.0, "fraction", 0.5)),
                new AbilityDef(AbilityKind.ENRAGE, "",
                        Map.of("healthPct", 0.35, "speedMult", 0.5, "damageMult", 0.2)),
                // Her abdomen is a hit box too, so she can be flanked — but she is already the fast
                // one, so a flank spends itself on bite, not more speed, and the arc is tighter
                // because a mini-boss this nimble is meant to be hard to get behind in the first place.
                new AbilityDef(AbilityKind.FLANK_RAGE, "",
                        Map.of("rearArc", 100.0, "speedMult", 0.2, "damageMult", 0.5))));

        table.put("reina_madre", List.of(
                new AbilityDef(AbilityKind.SUMMON, "geo:cria",
                        Map.of("healthPct", 0.6, "count", 3.0, "spread", 3.5)),
                new AbilityDef(AbilityKind.ON_HIT, "minecraft:poison",
                        Map.of("duration", 80.0, "amplifier", 0.0)),
                new AbilityDef(AbilityKind.ENRAGE, "",
                        Map.of("healthPct", 0.3, "speedMult", 0.3, "damageMult", 0.45)),
                // Her abdomen is now a hit box, so flanking the queen is a real option — and one she
                // answers. Stacks on top of the health enrage; a wounded, flanked queen is both.
                new AbilityDef(AbilityKind.FLANK_RAGE, "",
                        Map.of("rearArc", 120.0, "speedMult", 0.5, "damageMult", 0.35))));

        // The animated enemies are reachable from enemies.json as `geo` entries, so they get the
        // same treatment; their ids are GeoEnemyVariant's, not clone names.
        table.put("bone_sentinel", List.of(
                new AbilityDef(AbilityKind.CLEAVE, "", Map.of("radius", 3.0, "fraction", 0.5))));
        table.put("warden_colossus", List.of(
                new AbilityDef(AbilityKind.THORNS, "", Map.of("fraction", 0.25)),
                new AbilityDef(AbilityKind.ENRAGE, "",
                        Map.of("healthPct", 0.3, "speedMult", 0.3, "damageMult", 0.5))));

        return table;
    }

    /**
     * The vanilla-flavoured mobs, as clones rendered with the real mob's model.
     *
     * <p>These fight (or, for the bat, drift) exactly like the vanilla mobs they resemble, but a
     * clone is never a vanilla monster — so Pixelmon's spawn-replacement, which swaps joining
     * monsters for Pokémon, leaves them alone. That is the whole reason they are here rather than as
     * plain {@code entity:} ids: on a Pixelmon server the vanilla ones are deleted the instant they
     * spawn. What is lost with the model is the entity's own logic — this slime does <b>not</b>
     * split — which the tables account for by treating them as ordinary chaff.</p>
     */
    /**
     * The largest display size a clone may carry and still get through a doorway.
     *
     * <p>CustomNPCs scales a clone's <b>hitbox and its model by the same factor</b>
     * ({@code EntityNPCInterface.getDimensions} → {@code size.scale(getSize() * 0.2f)}) from a
     * player-shaped base of 0.6 × 1.8. A door is {@code alturaPuerta} = 3 blocks, so
     * {@code 1.8 × 0.2 × size ≤ 3} caps size at 8 — above it an enemy simply cannot follow the
     * party through the room it spawned in.</p>
     *
     * <p>This is the ceiling on how big a clone rendered as a <i>small</i> vanilla mob can look:
     * a silverfish model is about a sixth of a player's height, so even at the maximum it is knee
     * high. Making cave chaff genuinely bigger means making it a first-party
     * {@code GeoEnemyVariant}, whose hitbox and model scale together from its own rig
     * ({@code DungeonGeoEnemy.getDefaultDimensions}) with no such ratio baked in.</p>
     */
    public static final int MAX_CLONE_SIZE = 8;

    /** Vanilla entity models whose clones should bounce rather than walk. */
    private static final java.util.Set<String> HOPPING_MODELS =
            java.util.Set.of("minecraft:slime", "minecraft:magma_cube");

    /** The shipped preset with this id, or null — the bestiary is small and looked up by name. */
    public static EnemyPreset byId(String id) {
        for (EnemyPreset preset : all()) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Whether a clone should hop. Read off the model it renders as rather than a field, so a
     * slime an admin clones by hand moves like one too, with no code and no preset.
     */
    public static boolean hopsLikeASlime(EnemyPreset preset) {
        return preset != null && HOPPING_MODELS.contains(preset.entityModel());
    }

    /**
     * Retired. The cave swarm and the slimes were CustomNPCs clones wearing a vanilla silverfish
     * and slime, and both are now first-party {@code GeoEnemyVariant}s — the only form in which
     * they could be sized and made to move correctly. Clones stay the right answer for humanoids,
     * where the model and the NPC's own player-shaped base agree; they are the wrong answer for
     * anything whose silhouette does not, because a clone scales hitbox and model together from
     * that base. See {@link #MAX_CLONE_SIZE}.
     */
    public static List<EnemyPreset> vanillaLike() {
        return List.of();
    }

    /** Every preset, in install order. */
    public static List<EnemyPreset> all() {
        List<EnemyPreset> all = new java.util.ArrayList<>();
        all.addAll(chaff());
        all.addAll(elites());
        all.addAll(miniBosses());
        all.addAll(bosses());
        all.addAll(vanillaLike());
        return List.copyOf(all);
    }
}
