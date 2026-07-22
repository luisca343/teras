package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract between a marker placed in a template and the code that reads it.
 *
 * <p>Three times now, content has been authored, shipped and never once used: the bestiary that
 * never spawned (§33), the enemy tables read from the wrong file (§25), and the {@code loot} marker
 * both secret templates carried that nothing looked at (§37). The last one is the shape this file
 * exists to make impossible — a room key that is <i>required</i> to carry a marker no room of that
 * type ever reads.</p>
 */
class MarkerContractTest {

    /**
     * The invariant the {@code door} marker taught us to write this way. "Every marker must have a
     * consumer" would have deleted it, and it is deliberate: an author places it to see where the
     * generator will cut doorways. Requiring one, though, is meaningless — so that is what is banned.
     */
    @Test
    void noRoomKeyMayRequireAnAnnotation() {
        List<String> offenders = new ArrayList<>();
        for (String key : RoomKeys.keysWithRequiredMarkers()) {
            for (String marker : RoomKeys.requiredMarkers(key)) {
                if (MarkerContract.useOf(marker) == MarkerContract.Use.ANNOTATION) {
                    offenders.add(key + " requires " + marker);
                }
            }
        }
        assertEquals(List.of(), offenders,
                "a marker nothing is meant to read cannot be a requirement");
    }

    /** A required marker that is not in the vocabulary at all is a typo nobody would ever see. */
    @Test
    void everyRequiredMarkerIsInTheVocabulary() {
        List<String> unknown = new ArrayList<>();
        for (String key : RoomKeys.keysWithRequiredMarkers()) {
            for (String marker : RoomKeys.requiredMarkers(key)) {
                if (MarkerContract.useOf(marker) == null) {
                    unknown.add(key + " -> " + marker);
                }
            }
        }
        assertEquals(List.of(), unknown);
    }

    /**
     * §37 itself, as a rule. A room key required to carry {@code loot} whose room type is not in the
     * dispatch table is a pedestal nothing pays out at — which is exactly what both secret rooms
     * were, from the day they were authored until someone went to write a fifth one.
     */
    @Test
    void everyRoomRequiredToCarryLootActuallyRollsIt() {
        List<String> silent = new ArrayList<>();
        for (String key : RoomKeys.keysWithRequiredMarkers()) {
            if (!RoomKeys.requiredMarkers(key).contains("loot")) {
                continue;
            }
            RoomType type = roomTypeOf(key);
            if (type != null && MarkerContract.lootSource(type) == null) {
                silent.add(key);
            }
        }
        assertEquals(List.of(), silent,
                "these rooms have a loot pedestal and nothing rolls anything at it");
    }

    /** The four that pay out, held to the list so removing one is a deliberate act. */
    @Test
    void theLootRoomsAreTheOnesExpected() {
        assertEquals(java.util.Set.of(RoomType.TREASURE, RoomType.SECRET, RoomType.SUPER_SECRET),
                MarkerContract.lootRooms());
        assertNull(MarkerContract.lootSource(RoomType.NORMAL), "a normal room pays out nothing");
        assertNull(MarkerContract.lootSource(RoomType.SHOP), "the shop sells, it does not give");
        assertNull(MarkerContract.lootSource(RoomType.CURSE),
                "the curse room trades, it does not pay: its rewards come off the market's own "
                        + "pedestals, and the price of entry is taken at the spiked doorway");
    }

    /** A qualified marker is still its base kind: {@code decoracion:techo} is {@code decoracion}. */
    @Test
    void qualifiersDoNotHideTheKind() {
        assertEquals(MarkerContract.Use.RUNTIME, MarkerContract.useOf("decoracion:techo"));
        assertEquals(MarkerContract.Use.RUNTIME, MarkerContract.useOf("shopslot:2"));
        assertEquals(MarkerContract.Use.ANNOTATION, MarkerContract.useOf("door:n"));
        assertNull(MarkerContract.useOf("inventado"));
        assertNull(MarkerContract.useOf(null));
    }

    /** The kinds the room tool and the editor both hand out have to be the same set. */
    @Test
    void theVocabularyCoversEveryMarkerTheShippedRoomsUse() {
        for (String marker : List.of("spawn", "loot", "boss", "trapdoor", "shopslot", "challenge",
                "sacrifice", "arcade", "deal", "nido", "ambiente", "inicio", "decoracion", "door")) {
            assertNotNull(MarkerContract.useOf(marker), marker + " is not in the vocabulary");
        }
        assertTrue(MarkerContract.vocabulary().size() >= 14);
    }

    /** Room keys map onto room types by name; the loot rule above depends on it holding. */
    private static RoomType roomTypeOf(String roomKey) {
        String base = roomKey.replaceAll("_(large|l|big)$", "");
        try {
            return RoomType.valueOf(base.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
