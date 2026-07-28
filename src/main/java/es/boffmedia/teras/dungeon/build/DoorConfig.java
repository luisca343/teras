package es.boffmedia.teras.dungeon.build;

import es.boffmedia.teras.dungeon.model.DoorStyle;
import es.boffmedia.teras.dungeon.model.RoomType;
import es.boffmedia.teras.util.YamlConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The door language, in three families: the piso's own rock for an ordinary passage, copper for the
 * rooms about money, blackstone for the danger you walk into on purpose — and one of a kind for
 * each of the three promises.
 *
 * <p>Three families is few enough to learn in one floor, which is the whole point of the frame: a
 * player who has seen one treasure door knows the next one from across a room, on a piso that did
 * not exist when they learned it. That is why {@code specials} is dungeon-wide and {@code normal}
 * is not — a piso's own {@code puertas} block overrides the ordinary frame.</p>
 *
 * <p>All copper is <b>waxed</b>. Unwaxed would oxidise green over a server's lifetime, and a sign
 * that changes colour on its own stops being a sign.</p>
 */
public record DoorConfig(boolean relief, int sealCloseTicks, DoorStyle normal,
                         Map<RoomType, DoorStyle> specials) {

    static DoorConfig defaults() {
        Map<RoomType, DoorStyle> specials = new LinkedHashMap<>();

        // Copper — the rooms about money.
        specials.put(RoomType.TREASURE, DoorStyle.frame(
                "minecraft:waxed_cut_copper", "minecraft:gold_block",
                "minecraft:waxed_copper_bulb[lit=true,powered=false]",
                "minecraft:waxed_cut_copper"));
        // Wood, and the only frame in the dungeon that is not rock: a shop has to read as somebody
        // built it, from across the room, before you are close enough to see a single ware.
        specials.put(RoomType.SHOP, DoorStyle.frame(
                "minecraft:stripped_dark_oak_wood", "minecraft:waxed_cut_copper",
                "minecraft:lantern", "minecraft:waxed_cut_copper"));
        specials.put(RoomType.ARCADE, DoorStyle.frame(
                "minecraft:waxed_exposed_cut_copper", "minecraft:amethyst_block",
                "minecraft:verdant_froglight", "minecraft:waxed_exposed_cut_copper"));

        // Blackstone — the danger you walk into on purpose.
        specials.put(RoomType.CHALLENGE, DoorStyle.frame(
                "minecraft:polished_blackstone_bricks", "minecraft:chiseled_polished_blackstone",
                "minecraft:soul_lantern", "minecraft:polished_blackstone"));
        // Unlit, both of them. A room that charges blood should not be the brightest thing in sight.
        specials.put(RoomType.SACRIFICE, DoorStyle.frame(
                "minecraft:red_nether_bricks", "minecraft:nether_wart_block",
                "", "minecraft:red_nether_bricks"));
        // The spikes stay: they are the warning, and the frame only frames them.
        specials.put(RoomType.CURSE, DoorStyle.frame(
                "minecraft:blackstone", "minecraft:dripstone_block",
                "", "minecraft:blackstone"));
        specials.put(RoomType.MINI_BOSS, DoorStyle.frame(
                "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone",
                "minecraft:soul_lantern", "minecraft:polished_blackstone"));
        specials.put(RoomType.BOSS, DoorStyle.tall(
                "minecraft:polished_blackstone_bricks", "minecraft:crying_obsidian",
                "minecraft:soul_lantern", "minecraft:polished_blackstone"));

        // The three promises. One of a kind each, and never reused anywhere else in the dungeon —
        // which is what lets a player tell "this opens when I win this fight" from "this opens when
        // the floor's boss falls" without being told either.
        specials.put(RoomType.DEVIL_DEAL, DoorStyle.gate(
                "minecraft:polished_blackstone", "minecraft:crying_obsidian",
                "", "minecraft:polished_blackstone",
                "minecraft:polished_blackstone", "teras:marca_pacto"));
        specials.put(RoomType.ORDEN, DoorStyle.gate(
                "minecraft:smooth_quartz", "minecraft:gold_block",
                "", "minecraft:smooth_quartz",
                "minecraft:smooth_quartz", "teras:marca_orden"));
        // The sala del sello's own frame, written at the reveal rather than at the build: the wall
        // has to be solid until the boss dies. Its accent is the seal's lit rune.
        specials.put(RoomType.EXIT, DoorStyle.tall(
                "minecraft:polished_basalt", "minecraft:amethyst_block",
                "minecraft:amethyst_block", "minecraft:polished_basalt"));

        // The fallback ordinary frame — Cuevas' andesite, since a piso that declares no puertas is
        // most likely the one being authored against Cuevas as a starting point.
        DoorStyle normal = DoorStyle.frame("minecraft:polished_andesite", "minecraft:chiseled_tuff",
                "minecraft:shroomlight", "minecraft:andesite");
        // Six ticks, top course first. Long enough to read as a gate falling and short enough that
        // nobody walks out under it: the opening is sealed from the top down, so the last course to
        // land is the one at head height.
        return new DoorConfig(true, 6, normal, specials);
    }

    static DoorConfig read(YamlConfig yaml, DoorConfig previous) {
        YamlConfig doors = yaml.section("puertas");
        YamlConfig specialBlock = doors.section("especiales");
        Map<RoomType, DoorStyle> specials = new LinkedHashMap<>(previous.specials);
        for (RoomType type : RoomType.values()) {
            DoorStyle current = specials.get(type);
            if (current == null) {
                continue;
            }
            specials.put(type, style(
                    specialBlock.section(type.name().toLowerCase(java.util.Locale.ROOT)), current));
        }
        return new DoorConfig(
                doors.bool("relieve", previous.relief),
                Math.max(1, doors.integer("ticksCierre", previous.sealCloseTicks)),
                style(doors.section("normal"), previous.normal),
                specials);
    }

    /**
     * One door style, key by key over {@code fallback}. Per-key rather than all-or-nothing so a
     * server can repaint a single lamp without restating a frame it never meant to change — and so
     * a style gaining a part later does not blank it out of every config already on disk.
     */
    private static DoorStyle style(YamlConfig section, DoorStyle fallback) {
        return new DoorStyle(
                section.string("marco", fallback.marco()),
                section.string("acento", fallback.acento()),
                section.string("luz", fallback.luz()),
                section.string("umbral", fallback.umbral()),
                section.string("porton", fallback.porton()),
                section.string("marca", fallback.marca()),
                section.bool("alta", fallback.alta()));
    }
}
