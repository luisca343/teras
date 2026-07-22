package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped pisos against the templates in the jar. A piso is authored in two places at once — its
 * declared {@code formas} and a folder of {@code .nbt} per room key — and only the first is
 * compiled. Since there is <b>no fallback between pisos</b>, an empty folder is not a cosmetic gap:
 * it drops the piso out of selection entirely, and a tramo that loses its last piso cannot build a
 * floor at all.
 *
 * <p>At runtime {@code PisoCatalog.validateTemplates} catches this and logs it. This catches it at
 * build time instead, which is the difference between a failed test and a server that starts and
 * then cannot run a dungeon.</p>
 */
class ShippedPisoTest {

    private static final String ROOT = "data/teras/structure/dungeon/";

    /** Every shipped template, as the paths {@link RoomPoolIndex} indexes. */
    private static RoomPoolIndex shippedIndex() {
        List<String> paths = new ArrayList<>();
        for (String piso : List.of("cuevas", "cuevas_infestadas")) {
            for (String key : RoomKeys.requiredFor(EnumSet.allOf(ShapeFamily.class))) {
                for (String name : namesIn(piso, key)) {
                    paths.add(RoomPoolIndex.ROOT + piso + "/" + key + "/" + name);
                }
            }
        }
        return RoomPoolIndex.of(paths);
    }

    /**
     * The jar's own resources are not walkable as a directory from a unit test in every build
     * layout, so this probes for the names the generator writes rather than listing the folder.
     * {@code tools/author_cuevas_rooms.py} is the other half of this pair.
     */
    private static final List<String> AUTHORED = List.of(
            // one per key
            "cupula", "repisa", "anillo", "columna", "alcoba", "pedestal", "rendija", "geoda",
            "galerias", "santuario", "altar", "vitrina", "circulo", "garganta", "codo", "terrazas",
            "oculo",
            // the extra variants
            "pozo", "columnas", "derrumbe", "balcon", "columnata", "manantial", "mirador",
            "anfiteatro", "cuatro_pilares", "alacena", "veta", "derrumbado", "burbuja",
            // Infestadas only
            "nidal", "capullos");

    private static List<String> namesIn(String piso, String key) {
        List<String> found = new ArrayList<>();
        for (String name : AUTHORED) {
            URL url = ShippedPisoTest.class.getClassLoader()
                    .getResource(ROOT + piso + "/" + key + "/" + name + ".nbt");
            if (url != null) {
                found.add(name);
            }
        }
        return found;
    }

    private static FloorDef piso(String id, Set<ShapeFamily> shapes) {
        return new FloorDef(id, id, "", shapes, 7, "", "", MechanicDef.NONE,
                EnumSet.noneOf(es.boffmedia.teras.dungeon.model.Curse.class),
                List.of(), List.of(), List.of(), java.util.Map.of(), java.util.Map.of(),
                EnemyTable.EMPTY, DecorTables.EMPTY);
    }

    /** Cuevas declares every family, so it owes all 17 rooms and every folder must hold one. */
    @Test
    void cuevasShipsEveryRoomItDeclares() {
        FloorDef cuevas = piso("cuevas", EnumSet.allOf(ShapeFamily.class));
        assertEquals(List.of(), shippedIndex().emptyKeys(cuevas));
    }

    /** Infestadas ships its own copies — nothing is shared between these two at runtime. */
    @Test
    void infestadasShipsItsOwnRooms() {
        FloorDef infestadas = piso("cuevas_infestadas",
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE));
        assertEquals(List.of(), shippedIndex().emptyKeys(infestadas));
    }

    /**
     * The layout is load-bearing: a template one folder up, or named after its key rather than
     * itself, is invisible. Nothing falls back to it.
     */
    @Test
    void templatesLiveOneFolderPerRoomKey() {
        RoomPoolIndex index = shippedIndex();
        FloorDef cuevas = piso("cuevas", EnumSet.allOf(ShapeFamily.class));
        List<RoomVariant> pool = index.pool(cuevas, "normal_big");
        assertEquals(3, pool.size());
        assertTrue(pool.stream()
                        .anyMatch(v -> v.template().equals("teras:dungeon/cuevas/normal_big/terrazas")),
                "terrazas is still there, now beside anfiteatro and cuatro_pilares");

        Set<String> flat = new TreeSet<>();
        for (String key : cuevas.requiredRooms()) {
            if (ShippedPisoTest.class.getClassLoader()
                    .getResource(ROOT + "cuevas/" + key + ".nbt") != null) {
                flat.add(key);
            }
        }
        assertTrue(flat.isEmpty(), "these still ship in the retired flat layout: " + flat);
    }

    /**
     * The point of the folder layout, held to a number: the most-placed room on any floor has real
     * variety, and Infestadas has rooms Cuevas does not — without which it is a palette over shared
     * geometry, which is the themes idea the pisos redesign deleted.
     */
    @Test
    void normalHasSeveralVariantsAndInfestadasHasItsOwn() {
        RoomPoolIndex index = shippedIndex();
        FloorDef cuevas = piso("cuevas", EnumSet.allOf(ShapeFamily.class));
        FloorDef infestadas = piso("cuevas_infestadas", EnumSet.allOf(ShapeFamily.class));
        assertEquals(5, index.pool(cuevas, "normal").size());
        assertEquals(7, index.pool(infestadas, "normal").size());
        // A secret found twice in one run must not be the same pocket twice.
        assertEquals(5, index.pool(cuevas, "secret").size());

        List<String> onlyInfested = index.pool(infestadas, "normal").stream()
                .map(RoomVariant::name)
                .filter(n -> index.pool(cuevas, "normal").stream().noneMatch(v -> v.name().equals(n)))
                .toList();
        assertEquals(List.of("capullos", "nidal"), onlyInfested);
    }

    /** A piso narrowed to one family owes fewer rooms — the lever that makes a variant affordable. */
    @Test
    void narrowingShapesGenuinelyReducesWhatIsOwed() {
        int all = RoomKeys.requiredFor(EnumSet.allOf(ShapeFamily.class)).size();
        int tight = RoomKeys.requiredFor(
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE)).size();
        assertTrue(tight < all, "narrowing formas did not reduce the required rooms");
    }
}
