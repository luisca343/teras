package es.boffmedia.teras.dungeon.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static es.boffmedia.teras.dungeon.gear.GearDef.Rarity.COMUN;
import static es.boffmedia.teras.dungeon.gear.GearDef.Rarity.EPICO;
import static es.boffmedia.teras.dungeon.gear.GearDef.Rarity.RARO;
import static es.boffmedia.teras.dungeon.gear.GearOp.FLAT;
import static es.boffmedia.teras.dungeon.gear.GearOp.FRACTION_OF_BASE;

/**
 * The dungeon gear catalog. Definitions are code-authored so the set is always complete and the
 * items always register; {@code config/teras/dungeons/gear.json} may override the tunable half —
 * ability magnitudes, stat amounts and the Armourer's Workshop skin ids — without a rebuild.
 * {@link GearConfig} owns the file; this class owns the data and the merge.
 *
 * <p>Same defaults-in-code shape as {@code SpawnTables}/{@code Abilities}: an absent file means the
 * built-ins, and one malformed entry costs that entry rather than the catalog.</p>
 *
 * <p>Skin ids ship blank on purpose. A skin has to exist in the server's AW library before it can
 * be named, so the game half works from the first run and the visuals land when the skins are
 * authored — set {@code skin} in {@code gear.json} and {@code /teras dungeon reload}.</p>
 */
public final class GearDefs {
    private GearDefs() {}

    private static Map<String, GearDef> defs = defaults();

    /** A stat value is a bare number, or {@code {"amount": x, "op": "fraction_of_base"}}. */
    private static double amountOf(com.google.gson.JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            return json.has("amount") ? json.get("amount").getAsDouble() : 0;
        }
        return element.getAsDouble();
    }

    private static GearOp operationOf(com.google.gson.JsonElement element, GearOp fallback) {
        if (!element.isJsonObject()) {
            return fallback;
        }
        JsonObject json = element.getAsJsonObject();
        if (!json.has("op")) {
            return fallback;
        }
        try {
            return GearOp.valueOf(json.get("op").getAsString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Bumped by every load. Stamped onto each piece so an already-made one can tell it was cut
     * against an older catalog and re-stamp itself — the mechanism that lets a retune reach gear
     * that is already in someone's inventory.
     */
    private static int generation;

    public static Map<String, GearDef> all() {
        return defs;
    }

    public static int generation() {
        return generation;
    }

    /** Null when {@code id} is not gear, so callers can use it as the test. */
    public static GearDef get(String id) {
        return defs.get(id);
    }

    static void replaceAll(Map<String, GearDef> loaded) {
        defs = Map.copyOf(loaded);
        generation++;
    }

    private static GearDef sword(String id, GearDef.Rarity rarity, double damage, double speed,
                                GearAbility ability, double magnitude) {
        return melee(id, GearKind.SWORD, rarity, damage, speed, ability, magnitude);
    }

    /**
     * A gadget: no stat line at all, so the ability is the whole item — the same shape a charm has,
     * and for the same reason. What separates the two is that this one is spent.
     *
     * <p>Magnitude, radius and cooldown are all left to the ability's own defaults, which is what
     * makes a gadget one line here and a retune one number in {@code gear.json}.</p>
     */
    private static GearDef gadget(String id, GearAbility effect) {
        return new GearDef(id, GearKind.GADGET, COMUN, List.of(),
                List.of(AbilityDef.of(effect, effect.defaultMagnitude())), "", "");
    }

    /** The axe profile: same shape as a sword, different item and a heavier, slower stat line. */
    private static GearDef axe(String id, GearDef.Rarity rarity, double damage, double speed,
                               GearAbility ability, double magnitude) {
        return melee(id, GearKind.AXE, rarity, damage, speed, ability, magnitude);
    }

    private static GearDef melee(String id, GearKind kind, GearDef.Rarity rarity, double damage,
                                 double speed, GearAbility ability, double magnitude) {
        return new GearDef(id, kind, rarity, List.of(
                new GearDef.Stat(GearStat.ATTACK_DAMAGE, damage, FLAT),
                new GearDef.Stat(GearStat.ATTACK_SPEED, speed, FLAT)),
                ability, magnitude, "", "");
    }

    /**
     * Every piece is a vanilla base item plus a {@code teras:gear_id} component — no registered
     * items, no sprites; the AW skin is the intended look and the base is the silhouette until one
     * is authored. Bases are picked for shape and rarity, and deliberately avoid items with coded
     * behaviour: no mace (fall-smash mechanics), no totem (death save), no elytra (flight).
     *
     * <p>Stamped modifiers REPLACE the base item's own attribute line, so the numbers here are
     * each piece's full stat block — a diamond sword base contributes zero damage of its own.</p>
     */
    public static Map<String, GearDef> defaults() {
        Map<String, GearDef> map = new LinkedHashMap<>();

        // --- floor 1 -----------------------------------------------------------------------------
        // The previous expedition's kit and what the cave grew. Numbers that would embarrass a
        // floor-3 chest, on purpose: what a first floor's loot is for is the first small decision
        // and the first hint that gear has a personality, not power a player will keep.
        //
        // A COMUN piece with an ability is the shape worth repeating here. A stat line alone is
        // legible but forgettable; one small rule attached to it is what makes a player choose
        // between two things that are numerically the same.
        put(map, "minecraft:iron_sword",
                sword("machete_contrabandista", COMUN, 3.0, 0.9, GearAbility.NINGUNA, 0));
        // Slime on the blade. It does not kill faster, it decides who reaches whom — which is the
        // only kind of power a floor-1 weapon should have.
        put(map, "minecraft:stone_sword",
                sword("fisga_enlimada", COMUN, 2.5, 0.3, GearAbility.VISCOSO, 2.5));
        // The cosh: the archer's answer. A shooter that keeps getting shoved never gets its
        // cooldown back, and that is worth more on this floor than two points of damage.
        put(map, "minecraft:wooden_axe",
                axe("cachiporra", COMUN, 4.0, -0.6, GearAbility.EMPUJE, 1.1));

        put(map, "minecraft:leather_helmet", new GearDef("casco_prospector", GearKind.HELMET, COMUN,
                List.of(new GearDef.Stat(GearStat.ARMOR, 1.0, FLAT)),
                // The lamp, and the most valuable thing on a floor lit at 7 — Infestadas at 4. It
                // is the piece that changes how the floor is played without touching a number, and
                // it turns the climbers' eye-glow from a jump scare into information.
                // Three seconds of outline, refreshed every two: the number is the piece's, not a
                // fallback, because a magnitude of zero on a piece that has an ability is how you
                // ship an ability that does nothing.
                GearAbility.LINTERNA, 3, "", ""));
        put(map, "minecraft:leather_boots", new GearDef("botas_limo", GearKind.BOOTS, COMUN,
                List.of(new GearDef.Stat(GearStat.ARMOR, 1.0, FLAT),
                        new GearDef.Stat(GearStat.MOVEMENT_SPEED, 0.05, FRACTION_OF_BASE)),
                // Slime soles. The archers stand on ledges, and this is what makes going up there
                // a route rather than a commitment.
                GearAbility.CAIDA_SUAVE, 1.0, "", ""));
        // The plain one. Every floor needs a piece that is only a number, or the ones that are not
        // stop reading as special.
        put(map, "minecraft:leather_leggings", new GearDef("rodilleras_espeleologo",
                GearKind.LEGGINGS, COMUN,
                List.of(new GearDef.Stat(GearStat.ARMOR, 2.0, FLAT)),
                GearAbility.NINGUNA, 0, "", ""));
        // A barrel lid with a handle nailed to it: blocks like a shield, weighs like a lid.
        put(map, "minecraft:shield", new GearDef("tapa_barril", GearKind.SHIELD, COMUN,
                List.of(new GearDef.Stat(GearStat.ARMOR, 1.0, FLAT),
                        new GearDef.Stat(GearStat.MOVEMENT_SPEED, 0.04, FRACTION_OF_BASE)),
                GearAbility.NINGUNA, 0, "", ""));

        // The gadgets. Floor 1 is where the dungeon should teach that there is a button as well as
        // a swing, so all four are COMUN and all four are cheap: what they cost is a cooldown.
        put(map, "minecraft:firework_rocket", gadget("bengala", GearAbility.BENGALA));
        put(map, "minecraft:tnt", gadget("petardo_minero", GearAbility.PETARDO));
        put(map, "minecraft:slime_ball", gadget("frasco_limo", GearAbility.FRASCO));
        put(map, "minecraft:fishing_rod", gadget("garfio", GearAbility.GARFIO));

        // Weapons. Attack speed is a delta on the vanilla base (-2.4 bare-handed), so the hammer's
        // -0.4 is a real cost and the fang's +0.6 is one paid for elsewhere.
        put(map, "minecraft:diamond_sword",
                sword("espada_abisal", RARO, 6.0, 0.2, GearAbility.VAMPIRISMO, 0.10));
        put(map, "minecraft:netherite_sword",
                sword("colmillo_diablo", EPICO, 5.0, 0.6, GearAbility.DESGARRO, 3.0));
        // An axe, not a sword: it is a hammer, it rode on a netherite axe, and now that AXE is a
        // kind of its own the def can say so. It was SWORD only because that was the only melee
        // kind that existed.
        put(map, "minecraft:netherite_axe",
                axe("martillo_rompemuros", EPICO, 9.0, -0.4, GearAbility.ONDA, 0.15));
        put(map, "minecraft:golden_sword", new GearDef("hoja_maldita", GearKind.SWORD, RARO, List.of(
                new GearDef.Stat(GearStat.ATTACK_DAMAGE, 8.0, FLAT),
                // The curse: it hits hard and leaves you softer for carrying it.
                new GearDef.Stat(GearStat.ARMOR, -2.0, FLAT)),
                GearAbility.BOTIN, 4, "", ""));

        // Armour. The base's worn model shows on the body until the AW skin covers it, so the
        // material is picked to read right on its own: chainmail common, diamond rare, gold boss.
        put(map, "minecraft:chainmail_helmet", new GearDef("yelmo_laberinto", GearKind.HELMET, COMUN, List.of(
                new GearDef.Stat(GearStat.ARMOR, 2.0, FLAT),
                new GearDef.Stat(GearStat.MOVEMENT_SPEED, 0.10, FRACTION_OF_BASE)),
                GearAbility.NINGUNA, 0, "", ""));
        put(map, "minecraft:diamond_chestplate", new GearDef("coraza_abisal", GearKind.CHESTPLATE, RARO, List.of(
                new GearDef.Stat(GearStat.ARMOR, 6.0, FLAT),
                new GearDef.Stat(GearStat.ARMOR_TOUGHNESS, 1.0, FLAT)),
                GearAbility.ESPINAS, 0.15, "", ""));
        put(map, "minecraft:chainmail_leggings", new GearDef("grebas_saqueador", GearKind.LEGGINGS, COMUN, List.of(
                new GearDef.Stat(GearStat.ARMOR, 4.0, FLAT)),
                GearAbility.BOTIN, 3, "", ""));
        put(map, "minecraft:diamond_boots", new GearDef("botas_fantasma", GearKind.BOOTS, RARO, List.of(
                new GearDef.Stat(GearStat.ARMOR, 2.0, FLAT),
                new GearDef.Stat(GearStat.MOVEMENT_SPEED, 0.15, FRACTION_OF_BASE)),
                GearAbility.NINGUNA, 0, "", ""));
        put(map, "minecraft:golden_helmet", new GearDef("corona_jefe", GearKind.HELMET, EPICO, List.of(
                new GearDef.Stat(GearStat.ARMOR, 3.0, FLAT),
                new GearDef.Stat(GearStat.ARMOR_TOUGHNESS, 2.0, FLAT),
                new GearDef.Stat(GearStat.MAX_HEALTH, 2.0, FLAT)),
                GearAbility.NINGUNA, 0, "", ""));
        put(map, "minecraft:golden_chestplate", new GearDef("alas_fenix", GearKind.CHESTPLATE, EPICO, List.of(
                new GearDef.Stat(GearStat.ARMOR, 4.0, FLAT)),
                GearAbility.FENIX_MENOR, 1, "", ""));

        // Charms: no stat line at all, so the ability is the whole item.
        put(map, "minecraft:heart_of_the_sea", new GearDef("talisman_sangre", GearKind.CHARM, EPICO, List.of(),
                GearAbility.VAMPIRISMO, 0.05, "", ""));
        put(map, "minecraft:emerald", new GearDef("amuleto_avaro", GearKind.CHARM, COMUN, List.of(),
                GearAbility.BOTIN, 2, "", ""));

        return map;
    }

    /**
     * The first argument used to be the vanilla item the piece rode on. Gear has its own items now
     * ({@code GearItems}), derived from the kind, so it is kept only as a comment on where each
     * piece came from — and ignored.
     */
    /**
     * One entry of {@code habilidades}. Accepts a bare name ({@code "ONDA"}) as well as the object
     * form, because an ability with no numbers is a legitimate and common thing to write and should
     * not need an empty params block.
     */
    private static AbilityDef readAbility(com.google.gson.JsonElement element, String gearId,
                                          List<String> warnings) {
        String name;
        Map<String, String> params = new LinkedHashMap<>();
        if (element.isJsonPrimitive()) {
            name = element.getAsString();
        } else if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            name = json.has("id") ? json.get("id").getAsString() : "";
            if (json.has("params") && json.get("params").isJsonObject()) {
                JsonObject raw = json.getAsJsonObject("params");
                for (String key : raw.keySet()) {
                    try {
                        params.put(key, raw.get(key).getAsString());
                    } catch (Exception e) {
                        warnings.add("gear '" + gearId + "' ability param '" + key
                                + "' is not a value; skipped");
                    }
                }
            }
        } else {
            warnings.add("gear '" + gearId + "' has an habilidades entry that is neither a name "
                    + "nor an object; skipped");
            return null;
        }
        try {
            GearAbility ability = GearAbility.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return ability == GearAbility.NINGUNA ? null : new AbilityDef(ability, params);
        } catch (IllegalArgumentException e) {
            warnings.add("gear '" + gearId + "' names unknown habilidad '" + name + "'. Valid: "
                    + java.util.Arrays.toString(GearAbility.values()));
            return null;
        }
    }

    private static void put(Map<String, GearDef> map, String formerBase, GearDef def) {
        map.put(def.id(), def);
    }

    /**
     * Merges {@code gear.json} over the built-ins. Overrides are per-field: anything the file omits
     * keeps its built-in value, so a server that only wants to name a skin does not have to restate
     * the stat line to keep it.
     *
     * @return the merged catalog, and the warnings worth logging alongside it
     */
    public static Merge merge(JsonObject root) {
        Map<String, GearDef> loaded = defaults();
        List<String> warnings = new ArrayList<>();
        if (root == null) {
            return new Merge(loaded, warnings);
        }
        for (String id : root.keySet()) {
            try {
                GearDef base = loaded.get(id);
                if (base == null) {
                    // A piece the code does not know is a NEW piece, not a typo. This used to be
                    // ignored with a warning, which made gear.json an override layer over a
                    // code-authored catalog and nothing more — you could retune a piece and never
                    // add one, in a design whose whole point was that a piece is a component on a
                    // shared item and therefore needs no code at all.
                    GearDef created = create(id, root.getAsJsonObject(id), warnings);
                    if (created != null) {
                        loaded.put(id, created);
                    }
                    continue;
                }
                loaded.put(id, apply(base, root.getAsJsonObject(id), warnings));
            } catch (Exception e) {
                warnings.add("skipping bad gear.json entry '" + id + "': " + e);
            }
        }
        return new Merge(loaded, warnings);
    }

    public record Merge(Map<String, GearDef> defs, List<String> warnings) {}

    /**
     * A piece that exists only in {@code gear.json}.
     *
     * <p>{@code tipo} is the one field with no sensible default: it decides which of the eight
     * first-party items the piece is built on, which slot it applies from, and which Armourer's
     * Workshop skin type it takes. Everything else falls back — a stat-less, ability-less common is
     * a legal, if dull, piece.</p>
     *
     * <p>A new piece is mechanically complete the moment it is written. It has no <b>look</b> until
     * someone authors its AW skin, which is the one thing config genuinely cannot supply.</p>
     */
    private static GearDef create(String id, JsonObject json, List<String> warnings) {
        String rawKind = json.has("tipo") ? json.get("tipo").getAsString()
                : json.has("kind") ? json.get("kind").getAsString() : "";
        GearKind kind = null;
        if (!rawKind.isBlank()) {
            try {
                kind = GearKind.valueOf(rawKind.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warnings.add("gear.json gives new gear '" + id + "' unknown tipo '" + rawKind
                        + "'. Valid: " + java.util.Arrays.toString(GearKind.values()));
                return null;
            }
        } else if (json.has("skinType")) {
            // Inferred, because a skinType has already said what the piece is. Every kind has a
            // distinct AW skin type, so this cannot be ambiguous.
            kind = GearKind.bySkinType(canonicalSkinType(json.get("skinType").getAsString()));
        }
        if (kind == null) {
            warnings.add("gear.json defines new gear '" + id + "' with no 'tipo' and no usable "
                    + "'skinType' — it needs one of "
                    + java.util.Arrays.toString(GearKind.values()) + " to know what it is");
            return null;
        }
        GearDef.Rarity rarity = GearDef.Rarity.COMUN;
        if (json.has("rareza")) {
            try {
                rarity = GearDef.Rarity.valueOf(
                        json.get("rareza").getAsString().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warnings.add("gear '" + id + "' has unknown rareza '" + json.get("rareza")
                        + "', using " + rarity);
            }
        }
        GearDef skeleton = new GearDef(id, kind, rarity, List.of(), List.of(), "", "", "");
        return apply(skeleton, json, warnings);
    }

    private static GearDef apply(GearDef base, JsonObject json, List<String> warnings) {
        GearDef def = base;
        if (json.has("magnitud")) {
            def = def.withMagnitude(json.get("magnitud").getAsDouble());
        }
        // The ability, not just its magnitude. Without this a piece's effect is fixed in Java: an
        // ability nothing in the catalog happens to use — QUEMAZON was one — is unreachable no
        // matter what an admin writes, and retuning a piece stops at "how much" without ever
        // reaching "of what". Both halves of a piece's behaviour belong to the same file.
        if (json.has("habilidad")) {
            String name = json.get("habilidad").getAsString().trim().toUpperCase(Locale.ROOT);
            // Blank means "no ability", the same as NINGUNA. It is what an admin writes for a piece
            // that is a pure stat stick, and warning about it would be noise on a correct file.
            if (name.isEmpty()) {
                name = GearAbility.NINGUNA.name();
            }
            try {
                def = def.withAbility(GearAbility.valueOf(name));
            } catch (IllegalArgumentException e) {
                warnings.add("gear '" + def.id() + "' names unknown habilidad '" + name
                        + "' — keeping " + def.abilities() + ". Valid: "
                        + java.util.Arrays.toString(GearAbility.values()));
            }
        }
        // The list form, read after the legacy pair so it wins when a file carries both.
        if (json.has("habilidades") && json.get("habilidades").isJsonArray()) {
            List<AbilityDef> parsed = new ArrayList<>();
            for (var element : json.getAsJsonArray("habilidades")) {
                AbilityDef ability = readAbility(element, def.id(), warnings);
                if (ability != null) {
                    parsed.add(ability);
                }
            }
            def = def.withAbilities(parsed);
        }
        if (json.has("nombre")) {
            def = def.withNombre(json.get("nombre").getAsString());
        }
        if (json.has("skin")) {
            String skinId = normalizeSkinId(json.get("skin").getAsString(), base.id(), warnings);
            String skinType = canonicalSkinType(
                    json.has("skinType") ? json.get("skinType").getAsString() : def.skinType());
            def = def.withSkin(skinId, skinType);
        }
        if (json.has("stats")) {
            JsonObject stats = json.getAsJsonObject("stats");
            List<GearDef.Stat> replacement = new ArrayList<>();
            java.util.Set<String> handled = new java.util.LinkedHashSet<>();
            // Existing lines first, keeping their operation: a retune says "how much", not "how".
            for (GearDef.Stat stat : def.stats()) {
                String key = stat.stat().key();
                handled.add(key);
                replacement.add(stats.has(key)
                        ? new GearDef.Stat(stat.stat(), amountOf(stats.get(key)),
                                operationOf(stats.get(key), stat.operation()))
                        : stat);
            }
            // Then anything the piece did not already have. Without this a new piece could never
            // get a stat line at all — it starts with none — and an existing one could only ever be
            // retuned, never given something it lacked.
            for (String key : stats.keySet()) {
                GearStat stat = GearStat.byKey(key);
                if (stat == null) {
                    warnings.add("gear '" + def.id() + "' names unknown stat '" + key + "'");
                    continue;
                }
                if (!handled.contains(stat.key())) {
                    replacement.add(new GearDef.Stat(stat, amountOf(stats.get(key)),
                            operationOf(stats.get(key), GearOp.FLAT)));
                }
            }
            def = def.withStats(List.copyOf(replacement));
        }
        return def;
    }

    /** AW's DataDomain prefixes. Anything else in front of a skin id will never load. */
    private static final java.util.Set<String> SKIN_DOMAINS =
            java.util.Set.of("fs", "rs", "ws", "db", "ln", "ks", "kv", "sp");

    /**
     * Brings a configured skin id to the exact shape AW resolves. A server-library identifier is
     * {@code ws:} + the file's path relative to {@code armourers_workshop/skin-library/}, with a
     * leading slash and the {@code .armour} extension kept — read out of {@code SkinLibraryFile}'s
     * bytecode, because nothing about it is guessable: {@code ws:29467} looks right and resolves to
     * nothing. Admins get to write the human version ({@code docs/mi skin}); this makes it exact.
     */
    static String normalizeSkinId(String raw, String gearId, List<String> warnings) {
        String id = raw.trim().replace('\\', '/');
        if (id.isEmpty()) {
            return "";
        }
        String domain = id.length() > 3 && id.charAt(2) == ':' ? id.substring(0, 2) : null;
        if (domain == null) {
            // A bare path can only mean the server library.
            domain = "ws";
            id = "ws:" + id;
        }
        if (domain.equals("ws")) {
            String path = id.substring(3);
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".armour")) {
                path = path + ".armour";
            }
            return "ws:" + path;
        }
        if (!SKIN_DOMAINS.contains(domain)) {
            warnings.add("gear '" + gearId + "' skin '" + raw + "' has unknown domain '"
                    + domain + ":' (use ws: for the server library, db: for a database id)");
        }
        return id;
    }

    /**
     * Maps the type names people (and our own older config files) actually write to AW's
     * registered ones. The generated {@code gear.json} shipped {@code item_sword}-style names for a
     * while, so stale files must keep working after the rename.
     */
    static String canonicalSkinType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "item_sword" -> "sword";
            case "armor_head", "helmet" -> "head";
            case "armor_chest", "chestplate" -> "chest";
            case "armor_legs", "leggings" -> "legs";
            case "armor_feet", "boots" -> "feet";
            default -> s;
        };
    }

    /** The starting point written on first run. */
    public static JsonObject renderDefaults() {
        JsonObject root = new JsonObject();
        for (GearDef def : defaults().values()) {
            JsonObject entry = new JsonObject();
            // Written so an admin copying an entry as the template for a new piece gets the one
            // field a new piece cannot do without.
            entry.addProperty("tipo", def.kind().name());
            // Blank means "use the lang entry, or a name derived from the id" — written out so the
            // field is discoverable rather than folklore.
            entry.addProperty("nombre", def.nombre());
            // Written out even though it is the built-in value: an override an admin cannot see is
            // one they will never use, and the ability is the half of a piece worth discovering.
            // Both forms. `habilidades` is the real one; `habilidad`/`magnitud` are still written
            // for a piece with exactly one ability so an admin's existing edits keep working and
            // the file does not change shape under them.
            JsonArray abilities = new JsonArray();
            for (AbilityDef ability : def.abilities()) {
                JsonObject one = new JsonObject();
                one.addProperty("id", ability.ability().name());
                JsonObject params = new JsonObject();
                ability.params().forEach(params::addProperty);
                if (params.size() > 0) {
                    one.add("params", params);
                }
                abilities.add(one);
            }
            entry.add("habilidades", abilities);
            if (def.abilities().size() == 1) {
                AbilityDef only = def.abilities().get(0);
                entry.addProperty("habilidad", only.ability().name());
                entry.addProperty("magnitud", only.magnitude(only.ability().defaultMagnitude()));
            } else if (def.abilities().isEmpty()) {
                entry.addProperty("habilidad", GearAbility.NINGUNA.name());
                entry.addProperty("magnitud", 0);
            }
            entry.addProperty("skin", def.skinId());
            entry.addProperty("skinType", def.effectiveSkinType());
            JsonObject stats = new JsonObject();
            for (GearDef.Stat stat : def.stats()) {
                stats.addProperty(stat.stat().key(), stat.amount());
            }
            entry.add("stats", stats);
            root.add(def.id(), entry);
        }
        return root;
    }
}
