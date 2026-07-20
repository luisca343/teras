package es.boffmedia.teras.dungeon.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a room is <b>built</b> as, rather than which orientation the layout happened to want.
 *
 * <p>Eight {@link RoomShape}s collapse to four families, because a vertical 2×1 is a horizontal one
 * turned ninety degrees and the four L orientations are one L turned four ways. Authoring per
 * orientation meant four L templates that had to be kept consistent with each other by hand — four
 * builds, no extra variety, and four places for them to drift apart.</p>
 *
 * <p>A piso declares the families it supports; the generator expands each into the concrete shapes
 * it may produce. Declining a family is what excuses its template.</p>
 */
public enum ShapeFamily {
    /** One cell. Never optional: the start room and most of every layout are single cells. */
    SINGLE,
    /** Two cells in a line — 2×1, authored horizontal and rotated for vertical. */
    LARGE,
    /** Three cells in an L, authored as the top-left orientation and rotated for the other three. */
    L,
    /** Four cells — 2×2. Rotation preserves the footprint, so it is authored once and used as-is. */
    BIG;

    /** The suffix a room key of this family carries; SINGLE adds none. */
    public String suffix() {
        return this == SINGLE ? "" : "_" + name().toLowerCase(Locale.ROOT);
    }

    /** Every concrete shape this family covers. */
    public Set<RoomShape> shapes() {
        Set<RoomShape> shapes = EnumSet.noneOf(RoomShape.class);
        for (RoomShape shape : RoomShape.values()) {
            if (shape.family() == this) {
                shapes.add(shape);
            }
        }
        return shapes;
    }

    /** The concrete shapes a set of families allows, for the carver. */
    public static Set<RoomShape> shapesOf(Set<ShapeFamily> families) {
        Set<RoomShape> shapes = EnumSet.noneOf(RoomShape.class);
        for (ShapeFamily family : families) {
            shapes.addAll(family.shapes());
        }
        return shapes;
    }
}
