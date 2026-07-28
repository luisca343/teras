package es.boffmedia.teras.dungeon.instance;

import es.boffmedia.teras.dungeon.piso.DungeonDef;
import es.boffmedia.teras.dungeon.piso.TierDef;
import es.boffmedia.teras.dungeon.piso.WeightedRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the ascensor remembers. The rules worth pinning are the two that decide whether a lift is a
 * checkpoint or a lie: an unlock is refused for a tramo that does not exist, and it never goes
 * backwards.
 */
class ElevatorLedgerTest {

    private static final UUID ANA = UUID.nameUUIDFromBytes("ana".getBytes());
    private static final UUID BEA = UUID.nameUUIDFromBytes("bea".getBytes());
    private static final String CRIPTA = "cripta";

    private static TierDef tier(int largo) {
        return new TierDef(largo, 1.0, List.of(new WeightedRef("cuevas", 1)),
                List.of("jefe"), List.of("minijefe"));
    }

    /** Three tramos of two: floors 1-2, 3-4, 5-6. */
    private static DungeonDef cripta() {
        return new DungeonDef(CRIPTA, "La Cripta", 1, List.of(tier(2), tier(2), tier(2)));
    }

    @BeforeEach
    void clear() {
        ElevatorLedger.reset(null);
    }

    @Test
    void nobodyStartsWithAnything() {
        assertEquals(0, ElevatorLedger.deepest(ANA, CRIPTA));
    }

    @Test
    void clearingATramoUnlocksTheNextOne() {
        assertTrue(ElevatorLedger.unlock(cripta(), ANA, CRIPTA, 1));
        assertEquals(1, ElevatorLedger.deepest(ANA, CRIPTA));
    }

    /**
     * The whole "is there anywhere to go" rule. The lift on the last floor of the last tramo still
     * stands — it is the monument to finishing — but there is no tramo 3 to bank, so nothing is
     * recorded and the party is told nothing.
     */
    @Test
    void theLastTramoBanksNothing() {
        assertFalse(ElevatorLedger.unlock(cripta(), ANA, CRIPTA, 3));
        assertEquals(0, ElevatorLedger.deepest(ANA, CRIPTA));
    }

    /** Replaying a tramo already owned is not news, so nothing is said and nothing is written. */
    @Test
    void anUnlockNeverGoesBackwards() {
        assertTrue(ElevatorLedger.unlock(cripta(), ANA, CRIPTA, 2));
        assertFalse(ElevatorLedger.unlock(cripta(), ANA, CRIPTA, 1));
        assertFalse(ElevatorLedger.unlock(cripta(), ANA, CRIPTA, 2));
        assertEquals(2, ElevatorLedger.deepest(ANA, CRIPTA));
    }

    /** Per player, so a veteran cannot hand a newcomer depth they have never seen. */
    @Test
    void unlocksArePerPlayer() {
        ElevatorLedger.unlock(cripta(), ANA, CRIPTA, 2);
        assertEquals(2, ElevatorLedger.deepest(ANA, CRIPTA));
        assertEquals(0, ElevatorLedger.deepest(BEA, CRIPTA));
    }

    /** And per dungeon: clearing La Cripta says nothing about anywhere else. */
    @Test
    void unlocksArePerDungeon() {
        ElevatorLedger.unlock(cripta(), ANA, CRIPTA, 2);
        assertEquals(0, ElevatorLedger.deepest(ANA, "otra"));
    }

    @Test
    void tramoZeroIsTheEntranceAndIsNeverRecorded() {
        assertFalse(ElevatorLedger.unlock(cripta(), ANA, CRIPTA, 0));
        assertEquals(0, ElevatorLedger.deepest(ANA, CRIPTA));
    }

    // --- the arithmetic the unlock is boarded with -----------------------------------------------

    /** Tramo index to the stage it opens at: the conversion boarding makes. */
    @Test
    void firstStageOfEachTramo() {
        DungeonDef cripta = cripta();
        assertEquals(List.of(1, 3, 5),
                List.of(cripta.firstStageOf(0), cripta.firstStageOf(1), cripta.firstStageOf(2)));
    }

    /** Uneven tramos still add up, and a tramo that does not exist has no stage. */
    @Test
    void firstStageHandlesUnevenTramosAndOverflow() {
        DungeonDef uneven = new DungeonDef("u", "U", 1, List.of(tier(3), tier(1), tier(4)));
        assertEquals(List.of(1, 4, 5),
                List.of(uneven.firstStageOf(0), uneven.firstStageOf(1), uneven.firstStageOf(2)));
        assertEquals(0, uneven.firstStageOf(3));
        assertFalse(uneven.hasTramo(3));
    }

    /** Boarding a tramo lands on its first floor, which {@code locate} must agree is in it. */
    @Test
    void boardingLandsInsideTheTramoItUnlocked() {
        DungeonDef cripta = cripta();
        for (int tramo = 0; tramo < 3; tramo++) {
            DungeonDef.Position at = cripta.locate(cripta.firstStageOf(tramo));
            assertEquals(tramo, at.tierIndex());
            assertEquals(0, at.indexInTier());
        }
    }
}
