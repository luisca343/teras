package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a room key resolves to templates, now that the folder is the answer.
 *
 * <p>The property under test throughout is that {@code pesos} cannot change <i>membership</i>. That
 * asymmetry is the whole reason the old {@code salas} list was retired: two places answering "which
 * rooms exist" is how a room ends up on disk and never drawn, or drawn and not on disk, with nothing
 * anywhere saying so.</p>
 */
class RoomPoolIndexTest {

    private static FloorDef piso(String id, Set<ShapeFamily> shapes, List<String> hereda,
                                 Map<String, Map<String, Double>> pesos) {
        return new FloorDef(id, id, "", shapes, 7, "", "", MechanicDef.NONE,
                EnumSet.of(Curse.LOST), List.of(), List.of(), hereda, Map.of(), pesos,
                EnemyTable.EMPTY, DecorTables.EMPTY);
    }

    private static FloorDef cuevas() {
        return piso("cuevas", EnumSet.of(ShapeFamily.SINGLE), null, Map.of());
    }

    private static RoomPoolIndex index(String... paths) {
        return RoomPoolIndex.of(List.of(paths));
    }

    @Test
    void everyFileInTheFolderIsAVariant() {
        RoomPoolIndex index = index(
                "dungeon/cuevas/normal/repisa",
                "dungeon/cuevas/normal/pozo",
                "dungeon/cuevas/normal/columnas");
        List<RoomVariant> pool = index.pool(cuevas(), "normal");
        assertEquals(List.of("columnas", "pozo", "repisa"),
                pool.stream().map(RoomVariant::name).toList(),
                "alphabetical, so the indices sala editar takes do not move between sessions");
        assertEquals("teras:dungeon/cuevas/normal/repisa",
                pool.get(2).template());
        assertTrue(pool.stream().allMatch(v -> v.weight() == FloorDef.DEFAULT_WEIGHT));
    }

    /** The old flat layout is inert rather than half-working — {@code piso migrar} moves it in. */
    @Test
    void theFlatLayoutIsNotDiscovered() {
        assertTrue(index("dungeon/cuevas/normal").pool(cuevas(), "normal").isEmpty());
    }

    /** A parked file is a room being worked on, not one to build. */
    @Test
    void underscoredFilesAreIgnored() {
        RoomPoolIndex index = index("dungeon/cuevas/normal/repisa", "dungeon/cuevas/normal/_wip");
        assertEquals(1, index.pool(cuevas(), "normal").size());
    }

    @Test
    void inheritedSetsAddToThePoolAndCarryTheirPrefix() {
        RoomPoolIndex index = index(
                "dungeon/cuevas/diablo/propio",
                "dungeon/comun/diablo/altar");
        List<RoomVariant> pool = index.pool(cuevas(), "diablo");
        assertEquals(List.of("propio", "comun/altar"),
                pool.stream().map(RoomVariant::name).toList(),
                "the piso's own first, then each inherited set in declared order");
        assertEquals("comun", pool.get(1).set());
        assertEquals("altar", pool.get(1).file());
    }

    /**
     * The set name is part of a variant's identity, so two sets holding the same file name are two
     * entries rather than a shadowing rule someone has to remember.
     */
    @Test
    void sameFileNameInTwoSetsDoesNotCollide() {
        RoomPoolIndex index = index(
                "dungeon/cuevas/diablo/altar",
                "dungeon/comun/diablo/altar",
                "dungeon/huesos/diablo/altar");
        FloorDef shares = piso("cuevas", EnumSet.of(ShapeFamily.SINGLE),
                List.of("comun", "huesos"), Map.of());
        assertEquals(List.of("altar", "comun/altar", "huesos/altar"),
                index.pool(shares, "diablo").stream().map(RoomVariant::name).toList());
    }

    @Test
    void anEmptyHeredaSharesNothing() {
        RoomPoolIndex index = index("dungeon/cuevas/normal/repisa", "dungeon/comun/normal/generica");
        FloorDef alone = piso("cuevas", EnumSet.of(ShapeFamily.SINGLE), List.of(), Map.of());
        assertEquals(List.of("repisa"),
                index.pool(alone, "normal").stream().map(RoomVariant::name).toList());
        // and absent means the default set, which is a different statement
        assertEquals(2, index.pool(cuevas(), "normal").size());
    }

    @Test
    void pesosAdjustOddsAndZeroSwitchesOff() {
        RoomPoolIndex index = index(
                "dungeon/cuevas/normal/repisa",
                "dungeon/cuevas/normal/geoda",
                "dungeon/comun/normal/generica");
        FloorDef tuned = piso("cuevas", EnumSet.of(ShapeFamily.SINGLE), null,
                Map.of("normal", Map.of("geoda", 0.2, "comun/generica", 0.0)));
        assertEquals(0.2, tuned.peso("normal", "geoda"));
        List<RoomVariant> pool = index.pool(tuned, "normal");
        assertEquals(List.of("geoda", "repisa"), pool.stream().map(RoomVariant::name).toList());
        // Still declared, so it can be listed and turned back on — off is not the same as gone.
        assertEquals(3, index.declared(tuned, "normal").size());
    }

    /** The property the whole design rests on: a weight can never conjure a room. */
    @Test
    void aPesoForAMissingFileAddsNothingAndIsReported() {
        RoomPoolIndex index = index("dungeon/cuevas/normal/repisa");
        FloorDef wrong = piso("cuevas", EnumSet.of(ShapeFamily.SINGLE), null,
                Map.of("normal", Map.of("inventada", 5.0)));
        assertEquals(1, index.pool(wrong, "normal").size());
        assertTrue(index.problems(wrong).stream().anyMatch(p -> p.contains("inventada")));
    }

    @Test
    void anUnknownInheritedSetIsReportedRatherThanFatal() {
        RoomPoolIndex index = index("dungeon/cuevas/normal/repisa");
        FloorDef typo = piso("cuevas", EnumSet.of(ShapeFamily.SINGLE), List.of("hueso"), Map.of());
        assertEquals(1, index.pool(typo, "normal").size());
        assertTrue(index.problems(typo).stream().anyMatch(p -> p.contains("hueso")));
    }

    @Test
    void inheritingItselfWouldDoubleEveryRoom() {
        RoomPoolIndex index = index("dungeon/cuevas/normal/repisa");
        FloorDef silly = piso("cuevas", EnumSet.of(ShapeFamily.SINGLE), List.of("cuevas"), Map.of());
        assertEquals(1, index.pool(silly, "normal").size());
        assertFalse(index.problems(silly).isEmpty());
    }

    /**
     * What a piso owes follows its shapes, and an empty folder is fatal because there is no fallback
     * between pisos — the check that used to walk a declared list now walks the layout.
     */
    @Test
    void emptyKeysAreExactlyWhatIsOwedAndMissing() {
        FloorDef wide = piso("cuevas", EnumSet.allOf(ShapeFamily.class), null, Map.of());
        assertEquals(wide.requiredRooms().size(), RoomPoolIndex.EMPTY.emptyKeys(wide).size());
        assertEquals(17, wide.requiredRooms().size());

        RoomPoolIndex index = index("dungeon/cuevas/normal/repisa");
        FloorDef tight = piso("cuevas", EnumSet.of(ShapeFamily.SINGLE), null, Map.of());
        assertFalse(index.emptyKeys(tight).contains("normal"));
        assertTrue(index.emptyKeys(tight).contains("boss"));
    }

    /** Every variant zeroed leaves nothing to draw, which is as fatal as an empty folder. */
    @Test
    void zeroingEverythingEmptiesTheKey() {
        RoomPoolIndex index = index("dungeon/cuevas/normal/repisa");
        FloorDef off = piso("cuevas", EnumSet.of(ShapeFamily.SINGLE), null,
                Map.of("normal", Map.of("repisa", 0.0)));
        assertTrue(index.emptyKeys(off).contains("normal"));
    }
}
