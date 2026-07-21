package es.boffmedia.teras.dungeon.piso;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The piso's relative table, and the depth scaling the tramo applies to it. All pure — no game.
 */
class EnemyTableTest {

    private static EnemyTable cuevas() {
        return new EnemyTable(3, 5,
                List.of(SpawnRef.of("saqueador_cuevas", 4),
                        SpawnRef.of("husk_guardian", 2).asElite(),
                        SpawnRef.entity("minecraft:silverfish", 3)),
                List.of(new EnemyTable.AmbientRef("entity", "minecraft:bat", 0, 3)));
    }

    @Test
    void relativeCountsAtBaselineAreTheAuthoredNumbers() {
        EnemyTable t = cuevas();
        assertEquals(3, t.countMinAt(1.0));
        assertEquals(5, t.countMaxAt(1.0));
    }

    @Test
    void depthRaisesTheWaveButSlowly() {
        EnemyTable t = cuevas();
        // Count grows on its own slow curve, never as fast as the raw multiplier.
        assertTrue(t.countMaxAt(2.0) > 5);
        assertTrue(t.countMaxAt(2.0) < 10, "count must not double when dificultad does");
    }

    @Test
    void eliteWeightRisesWithDepthAndOrdinaryDoesNot() {
        EnemyTable t = cuevas();
        SpawnRef eliteAtOne = elite(t.rosterAt(1.0));
        SpawnRef eliteAtDeep = elite(t.rosterAt(2.5));
        assertTrue(eliteAtDeep.peso() > eliteAtOne.peso(),
                "the roster must shift toward elites at depth");
        // A non-elite entry keeps its authored weight whatever the depth.
        assertEquals(4, ordinary(t.rosterAt(2.5), "saqueador_cuevas").peso());
    }

    @Test
    void emptyTableIsNeverFought() {
        assertTrue(EnemyTable.EMPTY.isEmpty());
        assertTrue(EnemyTable.EMPTY.rosterAt(1.0).isEmpty());
    }

    @Test
    void weightsAndCountsAreClamped() {
        SpawnRef ref = SpawnRef.of("x", 0);
        assertEquals(1, ref.peso());
        EnemyTable weird = new EnemyTable(9, 2, List.of(), null);
        assertEquals(9, weird.countMin());
        assertEquals(9, weird.countMax(), "countMax is pulled up to countMin, never below it");
        assertTrue(weird.ambientales().isEmpty());
    }

    @Test
    void plainRefSkipsAttributeWork() {
        assertTrue(SpawnRef.of("x", 1).isPlain());
        assertFalse(SpawnRef.of("x", 1).scaled(1.5, 1.0, 1.0).isPlain());
    }

    private static SpawnRef elite(List<SpawnRef> roster) {
        return roster.stream().filter(SpawnRef::elite).findFirst().orElseThrow();
    }

    private static SpawnRef ordinary(List<SpawnRef> roster, String id) {
        return roster.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }
}
