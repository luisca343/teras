package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.ShapeFamily;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The room vocabulary a piso must supply, and how the shape opt-out narrows it.
 *
 * <p>Thirteen rooms are required of every piso whatever it declares; the rest exist only because a
 * piso asked for the shape that needs them. A piso that never generates L-shapes is not missing
 * four rooms — it has four fewer to author, which is the only lever that cuts authoring cost without
 * reintroducing the fallback between pisos that {@code themes} used to have.</p>
 *
 * <p>No Minecraft here: this is what validation is built on, and validation has to run in tests.</p>
 */
public final class RoomKeys {
    private RoomKeys() {}

    /** Required of every piso regardless of the shapes it declares. */
    public static final List<String> REQUIRED = List.of(
            "start", "normal", "boss", "mini_boss", "shop", "treasure",
            "secret", "super_secret", "challenge", "curse", "sacrifice",
            "arcade", "devil_deal");

    /**
     * The single-cell shape, which is not optional: the start room and the bulk of every layout are
     * one cell, so a piso that declared no shapes at all would still have to build them.
     */
    public static final ShapeFamily MANDATORY_FAMILY = ShapeFamily.SINGLE;

    /**
     * Every key {@code shapes} obliges a piso to author — the thirteen, plus one {@code normal_}
     * room per large shape, plus {@code boss_quad} when the piso allows 2×2 rooms at all.
     *
     * <p>{@code boss_quad} rides the QUAD opt-out rather than standing on its own: the boss room's
     * footprint is chosen from the shapes the floor can produce, so a piso without QUAD simply never
     * places a 2×2 boss chamber and must not be asked to build one.</p>
     */
    public static Set<String> requiredFor(Set<ShapeFamily> families) {
        Set<String> keys = new LinkedHashSet<>(REQUIRED);
        for (ShapeFamily family : families) {
            if (family == ShapeFamily.SINGLE) {
                continue;
            }
            keys.add("normal" + family.suffix());
            if (family == ShapeFamily.BIG) {
                keys.add("boss_big");
            }
        }
        return keys;
    }

    /** The key a room draws from: its type plus its family, never its orientation. */
    public static String keyFor(String type, RoomShape shape) {
        return type + shape.family().suffix();
    }

    /** The family a room key is authored at, read off its suffix. */
    public static ShapeFamily familyFor(String roomKey) {
        for (ShapeFamily family : ShapeFamily.values()) {
            if (family != ShapeFamily.SINGLE && roomKey.endsWith(family.suffix())) {
                return family;
            }
        }
        return ShapeFamily.SINGLE;
    }

    /**
     * The markers a finished room of this key is expected to carry.
     *
     * <p>Here rather than in the editor because the editor is no longer the only thing that asks:
     * the audit checks the same list, and two copies of "what a shop room needs" drift the moment
     * one of them gains a room type. Absence is never fatal — every consumer falls back to a
     * calculated position — so this is what a room *should* declare, not what it must.</p>
     */
    public static List<String> requiredMarkers(String roomKey) {
        if (roomKey.startsWith("boss")) {
            return List.of("boss", "trapdoor");
        }
        return switch (roomKey) {
            case "mini_boss" -> List.of("boss");
            case "treasure" -> List.of("loot");
            case "shop" -> List.of("shopslot");
            // The curse room pays out where its loot marker stands; without one the reward lands
            // in the middle of the room, which works but reads like a bug.
            case "curse" -> List.of("loot");
            case "sacrifice" -> List.of("sacrifice");
            case "arcade" -> List.of("arcade");
            case "devil_deal" -> List.of("deal");
            // The challenge plate is where the fight starts, so its marker is load-bearing on top
            // of the wave spawns.
            case "challenge" -> List.of("spawn", "challenge");
            // Where the party lands. Without it arrival falls back to the room's centre, which is
            // only safe while that centre happens to be empty floor — a start chamber built around
            // any central feature teleports the party inside it.
            case "start" -> List.of("inicio");
            // Every normal room, whatever its footprint — keys carry the shape family, never the
            // orientation.
            case "normal", "normal_large", "normal_l", "normal_big" -> List.of("spawn");
            default -> List.of();
        };
    }

    /**
     * A representative shape for a key, for the editor's pad: the family's base orientation, which
     * is the one a template is authored in.
     */
    public static RoomShape shapeFor(String roomKey) {
        return switch (familyFor(roomKey)) {
            case SINGLE -> RoomShape.SINGLE;
            case LARGE -> RoomShape.HORIZONTAL;
            case L -> RoomShape.L_TOP_LEFT;
            case BIG -> RoomShape.QUAD;
        };
    }
}
