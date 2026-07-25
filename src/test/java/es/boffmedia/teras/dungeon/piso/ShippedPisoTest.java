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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    /**
     * Every template shipped under {@code piso/key}, read from the folder itself.
     *
     * <p>This used to be a hardcoded list of room names, and it drifted the moment it mattered: the
     * six §66 variants and every §68 one were invisible to it, so a test whose whole job is "the
     * folders still hold rooms" was answering about a subset somebody remembered to add. Listing the
     * directory is the same discovery the runtime does, and it cannot fall behind the content.</p>
     */
    private static List<String> namesIn(String piso, String key) {
        URL folder = ShippedPisoTest.class.getClassLoader().getResource(ROOT + piso + "/" + key);
        if (folder == null || !"file".equals(folder.getProtocol())) {
            return List.of();
        }
        String[] files = new java.io.File(java.net.URLDecoder.decode(
                folder.getPath(), java.nio.charset.StandardCharsets.UTF_8)).list();
        if (files == null) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        for (String file : files) {
            if (file.endsWith(".nbt")) {
                found.add(file.substring(0, file.length() - ".nbt".length()));
            }
        }
        java.util.Collections.sort(found);
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
     * The shared set, which is now two rooms and not one.
     *
     * <p>Both satellites of la sala del sello live in {@code comun} because neither belongs to the
     * floor it visits, and both are <b>optional keys</b> — absence is a design choice, never a
     * validation failure. That is exactly what makes a missing one invisible: la Orden was fully
     * built, tested and inert for a whole slice because {@code hasOrdenRoom} found no template and
     * silently forced her chance to zero. A folder is what switches her on, so a test has to be what
     * says the folder is still there.</p>
     */
    @Test
    void theSharedSetShipsBothSatellites() {
        // `hereda` defaults to `comun` only when it is null; the helper above passes an empty list,
        // which is a piso that inherits nothing at all.
        FloorDef inheritor = new FloorDef("cuevas", "cuevas", "",
                EnumSet.allOf(ShapeFamily.class), 7, "", "", MechanicDef.NONE,
                EnumSet.noneOf(es.boffmedia.teras.dungeon.model.Curse.class),
                List.of(), List.of(), null, java.util.Map.of(), java.util.Map.of(),
                EnemyTable.EMPTY, DecorTables.EMPTY);
        RoomPoolIndex index = RoomPoolIndex.of(List.of(
                RoomPoolIndex.ROOT + RoomPoolIndex.DEFAULT_SET + "/devil_deal/pacto",
                RoomPoolIndex.ROOT + RoomPoolIndex.DEFAULT_SET + "/orden/capilla"));
        for (String key : List.of("devil_deal", "orden")) {
            assertTrue(ShippedPisoTest.class.getClassLoader().getResource(
                            ROOT + RoomPoolIndex.DEFAULT_SET + "/" + key + "/"
                                    + (key.equals("orden") ? "capilla" : "pacto") + ".nbt") != null,
                    "the shared " + key + " room is not in the jar");
            assertFalse(index.pool(inheritor, key).isEmpty(),
                    "a piso that inherits 'comun' cannot draw its " + key + " room");
        }
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
        // A lower bound, not a count: `normal` is most of every floor, so it is the one key where
        // more is always right and a number here would only ever be a chore to update. Ten is what
        // §69 authored it to; dropping below that is a regression worth failing over.
        assertTrue(index.pool(cuevas, "normal").size() >= 10,
                "normal is the most-walked room and lost variants");
        // A secret found twice in one run must not be the same pocket twice.
        assertEquals(5, index.pool(cuevas, "secret").size());

        List<String> onlyInfested = index.pool(infestadas, "normal").stream()
                .map(RoomVariant::name)
                .filter(n -> index.pool(cuevas, "normal").stream().noneMatch(v -> v.name().equals(n)))
                .sorted()
                .toList();
        assertEquals(List.of("capullos", "mudas", "nidal", "sumidero"), onlyInfested);
    }

    /**
     * Infestadas' arena is <b>hers</b>, and it is the only one she has.
     *
     * <p>A boss room is a recognized room (§66): its power comes from being the same place every
     * time, so a piso may hold exactly one. Infestadas therefore <i>replaces</i> the derived cave
     * arena rather than adding to it — a queen fought in a cave that happens to have webs in it is
     * the themes idea the piso model deleted, arrived at from the other direction.</p>
     */
    @Test
    void infestadasBossIsItsOwnAndTheOnlyOne() {
        RoomPoolIndex index = shippedIndex();
        FloorDef infestadas = piso("cuevas_infestadas", EnumSet.allOf(ShapeFamily.class));
        assertEquals(List.of("telar"),
                index.pool(infestadas, "boss").stream().map(RoomVariant::name).toList());
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
