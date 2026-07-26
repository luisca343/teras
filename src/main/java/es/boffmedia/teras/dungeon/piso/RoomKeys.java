package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.ShapeFamily;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
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
     * Authored when the piso wants the feature, never owed. {@code exit} is la sala del sello:
     * a piso that supplies one gets the appended seal chamber behind its boss room, and a piso
     * without one falls back to carving the pit in the arena — absence is a choice, not a
     * validation failure, which is what keeps a rollout from bricking a run.
     *
     * <p>{@code orden} is la sala de la Orden, the grace chamber on the sello's far flank. Optional
     * for the same reason and one more: it is new content, so a piso that has not authored it
     * simply never offers the Orden, and the Acreedor's half of the arc still runs.</p>
     */
    public static final List<String> OPTIONAL = List.of("exit", "orden");

    /**
     * The single-cell shape, which is not optional: the start room and the bulk of every layout are
     * one cell, so a piso that declared no shapes at all would still have to build them.
     */
    public static final ShapeFamily MANDATORY_FAMILY = ShapeFamily.SINGLE;

    /**
     * Every key {@code shapes} obliges a piso to author — the thirteen, plus one {@code normal_}
     * room per large shape, plus {@code boss_big} when the piso allows 2×2 rooms at all.
     *
     * <p>{@code boss_big} rides the QUAD opt-out rather than standing on its own: the boss room's
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
        // The exit room has exactly one footprint — a 2×2 appended post-generation — so its key
        // carries no suffix: there is no `exit` single for `exit_big` to be distinguished from.
        if (roomKey.equals("exit")) {
            return ShapeFamily.BIG;
        }
        for (ShapeFamily family : ShapeFamily.values()) {
            if (family != ShapeFamily.SINGLE && roomKey.endsWith(family.suffix())) {
                return family;
            }
        }
        return ShapeFamily.SINGLE;
    }

    /** Where the shared table lives, relative to the classpath root. */
    private static final String MARKERS_RESOURCE = "/data/teras/dungeon/required_markers.txt";

    private static final Map<String, List<String>> REQUIRED_MARKERS = loadRequiredMarkers();

    /**
     * The markers a finished room of this key is expected to carry.
     *
     * <p>Read from a shared table rather than written here, because this is not the only audit that
     * asks. {@code RoomAudit} checks a room saved in the in-game editor; the Python room tool checks
     * the shipped templates as it generates them. They were hand-maintained mirrors and they drifted
     * the first time it mattered — §37 required {@code loot} on secret rooms in the tool and not in
     * Java, so the fix for "secrets pay nothing" only landed for half the rooms anyone could author.
     * One file, both readers.</p>
     *
     * <p>Absence is never fatal — every consumer falls back to a calculated position — so this is
     * what a room <i>should</i> declare, not what it must.</p>
     */
    public static List<String> requiredMarkers(String roomKey) {
        return REQUIRED_MARKERS.getOrDefault(roomKey, List.of());
    }

    /** Every room key the table says something about, for tests and tooling. */
    public static Set<String> keysWithRequiredMarkers() {
        return REQUIRED_MARKERS.keySet();
    }

    private static Map<String, List<String>> loadRequiredMarkers() {
        Map<String, List<String>> table = new java.util.LinkedHashMap<>();
        try (java.io.InputStream stream = RoomKeys.class.getResourceAsStream(MARKERS_RESOURCE)) {
            if (stream == null) {
                // Not recoverable by guessing: an empty table would silently pass every room.
                throw new IllegalStateException(MARKERS_RESOURCE + " is missing from the jar");
            }
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                if (comment >= 0) {
                    line = line.substring(0, comment);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    throw new IllegalStateException("bad line in " + MARKERS_RESOURCE + ": " + line);
                }
                List<String> markers = new ArrayList<>();
                for (String marker : line.substring(colon + 1).split(",")) {
                    if (!marker.isBlank()) {
                        markers.add(marker.trim());
                    }
                }
                table.put(line.substring(0, colon).trim(), List.copyOf(markers));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not read " + MARKERS_RESOURCE, e);
        }
        return Map.copyOf(table);
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
