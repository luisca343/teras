package es.boffmedia.teras.dungeon.gear;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        return new GearDef(id, GearKind.SWORD, rarity, List.of(
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

        // Weapons. Attack speed is a delta on the vanilla base (-2.4 bare-handed), so the hammer's
        // -0.4 is a real cost and the fang's +0.6 is one paid for elsewhere.
        put(map, "minecraft:diamond_sword",
                sword("espada_abisal", RARO, 7.0, 0.2, GearAbility.VAMPIRISMO, 0.10));
        put(map, "minecraft:netherite_sword",
                sword("colmillo_diablo", EPICO, 5.0, 0.6, GearAbility.DESGARRO, 3.0));
        put(map, "minecraft:netherite_axe",
                sword("martillo_rompemuros", EPICO, 9.0, -0.4, GearAbility.ONDA, 0.30));
        put(map, "minecraft:golden_sword", new GearDef("hoja_maldita", GearKind.SWORD, RARO, List.of(
                new GearDef.Stat(GearStat.ATTACK_DAMAGE, 8.0, FLAT),
                // The curse: it hits hard and leaves you softer for carrying it.
                new GearDef.Stat(GearStat.ARMOR, -2.0, FLAT)),
                GearAbility.BOTIN, 15, "", ""));

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
                GearAbility.BOTIN, 10, "", ""));
        put(map, "minecraft:diamond_boots", new GearDef("botas_fantasma", GearKind.BOOTS, RARO, List.of(
                new GearDef.Stat(GearStat.ARMOR, 2.0, FLAT),
                new GearDef.Stat(GearStat.MOVEMENT_SPEED, 0.15, FRACTION_OF_BASE)),
                GearAbility.NINGUNA, 0, "", ""));
        put(map, "minecraft:golden_helmet", new GearDef("corona_jefe", GearKind.HELMET, EPICO, List.of(
                new GearDef.Stat(GearStat.ARMOR, 3.0, FLAT),
                new GearDef.Stat(GearStat.ARMOR_TOUGHNESS, 2.0, FLAT),
                new GearDef.Stat(GearStat.MAX_HEALTH, 4.0, FLAT)),
                GearAbility.NINGUNA, 0, "", ""));
        put(map, "minecraft:golden_chestplate", new GearDef("alas_fenix", GearKind.CHESTPLATE, EPICO, List.of(
                new GearDef.Stat(GearStat.ARMOR, 4.0, FLAT)),
                GearAbility.FENIX_MENOR, 1, "", ""));

        // Charms: no stat line at all, so the ability is the whole item.
        put(map, "minecraft:heart_of_the_sea", new GearDef("talisman_sangre", GearKind.CHARM, EPICO, List.of(),
                GearAbility.VAMPIRISMO, 0.05, "", ""));
        put(map, "minecraft:emerald", new GearDef("amuleto_avaro", GearKind.CHARM, COMUN, List.of(),
                GearAbility.BOTIN, 5, "", ""));

        return map;
    }

    private static void put(Map<String, GearDef> map, String baseItem, GearDef def) {
        map.put(def.id(), def.withBaseItem(baseItem));
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
            GearDef base = loaded.get(id);
            if (base == null) {
                warnings.add("gear.json mentions unknown gear '" + id + "', ignoring");
                continue;
            }
            try {
                loaded.put(id, apply(base, root.getAsJsonObject(id), warnings));
            } catch (Exception e) {
                warnings.add("skipping bad gear.json entry '" + id + "': " + e);
            }
        }
        return new Merge(loaded, warnings);
    }

    public record Merge(Map<String, GearDef> defs, List<String> warnings) {}

    private static GearDef apply(GearDef base, JsonObject json, List<String> warnings) {
        GearDef def = base;
        if (json.has("magnitud")) {
            def = def.withMagnitude(json.get("magnitud").getAsDouble());
        }
        if (json.has("skin")) {
            String skinId = normalizeSkinId(json.get("skin").getAsString(), base.id(), warnings);
            String skinType = canonicalSkinType(
                    json.has("skinType") ? json.get("skinType").getAsString() : def.skinType());
            def = def.withSkin(skinId, skinType);
        }
        if (json.has("stats")) {
            JsonObject stats = json.getAsJsonObject("stats");
            for (String key : stats.keySet()) {
                if (GearStat.byKey(key) == null) {
                    warnings.add("gear '" + def.id() + "' names unknown stat '" + key + "'");
                }
            }
            List<GearDef.Stat> replacement = new ArrayList<>();
            for (GearDef.Stat stat : def.stats()) {
                String key = stat.stat().key();
                replacement.add(stats.has(key)
                        ? new GearDef.Stat(stat.stat(), stats.get(key).getAsDouble(), stat.operation())
                        : stat);
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
            entry.addProperty("magnitud", def.magnitude());
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
