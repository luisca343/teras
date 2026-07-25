package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.RoomType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonGeneratorTest {

    private static final GenConfig CONFIG = GenConfig.defaults();

    @Test
    void sameInputsRebuildTheSameFloor() {
        DungeonLayout a = DungeonGenerator.generate(CONFIG, 3, Set.of(Curse.LOST), "repeat");
        DungeonLayout b = DungeonGenerator.generate(CONFIG, 3, Set.of(Curse.LOST), "repeat");

        assertEquals(LayoutAscii.render(a), LayoutAscii.render(b));
        assertEquals(a.doors().size(), b.doors().size());
        assertEquals(a.attempt(), b.attempt());
        assertEquals(a.baseSeed(), b.baseSeed());
    }

    @Test
    void differentSeedsProduceDifferentFloors() {
        DungeonLayout a = DungeonGenerator.generate(CONFIG, 3, Set.of(), "alpha");
        DungeonLayout b = DungeonGenerator.generate(CONFIG, 3, Set.of(), "beta");
        assertNotEquals(LayoutAscii.render(a), LayoutAscii.render(b));
    }

    @Test
    void missingSeedGetsARandomReproducibleOne() {
        DungeonLayout layout = DungeonGenerator.generate(CONFIG, 1, Set.of(), null);
        assertFalse(layout.seedString().isBlank());

        DungeonLayout again = DungeonGenerator.generate(CONFIG, 1, Set.of(), layout.seedString());
        assertEquals(LayoutAscii.render(layout), LayoutAscii.render(again));
    }

    @Test
    void invalidStagesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DungeonGenerator.generate(CONFIG, 0, Set.of(), "s"));
        assertThrows(IllegalArgumentException.class,
                () -> DungeonGenerator.generate(CONFIG, 13, Set.of(), "s"));
    }

    @Test
    void layoutEchoesItsInputs() {
        DungeonLayout layout = DungeonGenerator.generate(CONFIG, 6, Set.of(Curse.LABYRINTH), "echo");
        assertEquals(6, layout.floor());
        assertEquals(Set.of(Curse.LABYRINTH), layout.curses());
        assertEquals("echo", layout.seedString());
        assertTrue(layout.attempt() < CONFIG.maxAttempts());
    }

    @Test
    void doorKindsRankSecrecyOverBoss() {
        assertEquals(DoorKind.HIDDEN, DoorKind.between(RoomType.SUPER_SECRET, RoomType.BOSS));
        assertEquals(DoorKind.SECRET_CRACK, DoorKind.between(RoomType.NORMAL, RoomType.SECRET));
        assertEquals(DoorKind.BOSS, DoorKind.between(RoomType.MINI_BOSS, RoomType.NORMAL));
        assertEquals(DoorKind.OPEN, DoorKind.between(RoomType.NORMAL, RoomType.TREASURE));
    }

    @Test
    void startRoomAlwaysHasAtLeastOneDoor() {
        DungeonLayout layout = DungeonGenerator.generate(CONFIG, 1, Set.of(), "doors");
        assertFalse(layout.doorsOf(layout.start()).isEmpty());
    }
}
