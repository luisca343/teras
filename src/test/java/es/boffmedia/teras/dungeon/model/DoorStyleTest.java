package es.boffmedia.teras.dungeon.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of a door's two rooms decides how it looks, and what a style falls back to.
 *
 * <p>The rule is the whole door language: a frame is worth looking at only because the more special
 * room always wins it. Get the precedence wrong in one direction and a treasure door beside a boss
 * room reads as a boss door; wrong in the other and every corridor off the boss arena is framed like
 * the arena.</p>
 */
class DoorStyleTest {

    @Test
    void theMoreSpecialRoomWinsTheDoor() {
        assertEquals(RoomType.TREASURE, DoorStyle.winner(RoomType.NORMAL, RoomType.TREASURE));
        assertEquals(RoomType.TREASURE, DoorStyle.winner(RoomType.TREASURE, RoomType.NORMAL));
        assertEquals(RoomType.BOSS, DoorStyle.winner(RoomType.BOSS, RoomType.NORMAL));
        assertEquals(RoomType.SHOP, DoorStyle.winner(RoomType.START, RoomType.SHOP));
    }

    /** A corridor is the only thing NORMAL can win, and only against itself. */
    @Test
    void normalLosesToEverything() {
        for (RoomType type : RoomType.values()) {
            if (type == RoomType.NORMAL) {
                continue;
            }
            assertEquals(type, DoorStyle.winner(RoomType.NORMAL, type), type.name());
        }
    }

    /**
     * The three promises outrank the fights. A pacto room hung off the sala del sello touches the
     * boss's own chamber, and if BOSS won that edge the two would be the same door — which is the
     * one distinction the language exists to make.
     */
    @Test
    void thePromisesOutrankTheFights() {
        assertEquals(RoomType.DEVIL_DEAL, DoorStyle.winner(RoomType.BOSS, RoomType.DEVIL_DEAL));
        assertEquals(RoomType.ORDEN, DoorStyle.winner(RoomType.BOSS, RoomType.ORDEN));
        assertEquals(RoomType.EXIT, DoorStyle.winner(RoomType.EXIT, RoomType.BOSS));
    }

    /**
     * The sala del sello loses to what hangs off it.
     *
     * <p>This shipped wrong. Both satellites are appended by {@code PostRooms} onto the sello's
     * flanks, so <b>every</b> door they own is a door they share with EXIT — and with the sello
     * ranked above them, both flanks of that chamber wore its basalt and amethyst. Standing in the
     * sala del sello you saw two identical doors, in the same stone as the room you were in, where
     * the whole reason GRACIA is its own kind is that the two must read differently from exactly
     * that spot.</p>
     */
    @Test
    void theSatellitesOutrankTheChamberTheyHangOff() {
        assertEquals(RoomType.DEVIL_DEAL, DoorStyle.winner(RoomType.DEVIL_DEAL, RoomType.EXIT));
        assertEquals(RoomType.DEVIL_DEAL, DoorStyle.winner(RoomType.EXIT, RoomType.DEVIL_DEAL));
        assertEquals(RoomType.ORDEN, DoorStyle.winner(RoomType.ORDEN, RoomType.EXIT));
        assertEquals(RoomType.ORDEN, DoorStyle.winner(RoomType.EXIT, RoomType.ORDEN));
        assertNotEquals(DoorStyle.winner(RoomType.DEVIL_DEAL, RoomType.EXIT),
                DoorStyle.winner(RoomType.ORDEN, RoomType.EXIT),
                "the two flanks of the sala del sello must not be the same door");
    }

    /** Ties would make the winner depend on argument order, which is how a door flickers per build. */
    @Test
    void everyRoomTypeHasItsOwnRank() {
        Set<Integer> ranks = new java.util.HashSet<>();
        for (RoomType type : RoomType.values()) {
            assertTrue(ranks.add(DoorStyle.rank(type)), "duplicate rank for " + type);
        }
        assertEquals(RoomType.values().length, ranks.size());
    }

    /** Exhaustive, so a room type appended later cannot fall through with no rank at all. */
    @Test
    void everyRoomTypeIsRanked() {
        for (RoomType type : EnumSet.allOf(RoomType.class)) {
            assertTrue(DoorStyle.rank(type) >= 0, type.name());
        }
    }

    /**
     * An omitted accent or threshold is the frame's own block, not nothing. A style is written by
     * hand in a config, and half of them will name two blocks and stop — which has to build a plain
     * frame rather than a frame with holes in it.
     */
    @Test
    void anOmittedPartFallsBackToTheFrame() {
        DoorStyle style = new DoorStyle("minecraft:andesite", "", "", "", "", "", false);
        assertEquals("minecraft:andesite", style.acento());
        assertEquals("minecraft:andesite", style.umbral());
        assertFalse(style.lit(), "no lamp was named");
        assertFalse(style.hasGate(), "no gate was named");
    }

    /** A gate is what tells "opens when the boss falls" from "opens when this fight ends". */
    @Test
    void onlyAGateStyleClosesItsDoorway() {
        assertFalse(DoorStyle.frame("a", "b", "c", "d").hasGate());
        assertFalse(DoorStyle.tall("a", "b", "c", "d").hasGate());
        assertTrue(DoorStyle.gate("a", "b", "", "d", "panel", "marca").hasGate());
        assertNotEquals(DoorStyle.frame("a", "b", "c", "d"), DoorStyle.tall("a", "b", "c", "d"));
    }
}
