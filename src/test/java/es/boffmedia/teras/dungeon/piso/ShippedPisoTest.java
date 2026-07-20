package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped {@code cuevas} piso against the templates in the jar. A piso is authored in two places
 * at once — its declared {@code formas} and a {@code .nbt} per room key — and only the first is
 * compiled. Since there is <b>no fallback between pisos</b>, a renamed or missing template is not a
 * cosmetic gap: it drops the piso out of selection entirely, and a tramo that loses its last piso
 * cannot build a floor at all.
 *
 * <p>At runtime {@code PisoCatalog.validateTemplates} catches this and logs it. This catches it at
 * build time instead, which is the difference between a failed test and a server that starts and
 * then cannot run a dungeon.</p>
 */
class ShippedPisoTest {

    private static final String ROOT = "data/teras/structure/dungeon/cuevas/";

    private static boolean exists(String roomKey) {
        try (InputStream stream = ShippedPisoTest.class.getClassLoader()
                .getResourceAsStream(ROOT + roomKey + ".nbt")) {
            return stream != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Cuevas declares every family, so it owes all 17 rooms. */
    @Test
    void cuevasShipsEveryRoomItDeclares() {
        Set<String> missing = new TreeSet<>();
        for (String key : RoomKeys.requiredFor(EnumSet.allOf(ShapeFamily.class))) {
            if (!exists(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "cuevas is missing templates for: " + missing);
    }

    /**
     * The naming convention is load-bearing: with no {@code salas} block a room key resolves to
     * {@code teras:dungeon/<piso>/<key>} and nothing else. A template whose file name drifts from
     * its key is invisible.
     */
    @Test
    void conventionalPathMatchesTheShippedLayout() {
        RoomVariant variant = RoomVariant.conventional("cuevas", "normal_big");
        assertTrue(variant.template().equals("teras:dungeon/cuevas/normal_big"),
                "convention changed to " + variant.template()
                        + " but the templates still live under " + ROOT);
        assertTrue(exists("normal_big"));
    }

    /** Infestadas ships its own copies — nothing is shared between pisos at runtime. */
    @Test
    void infestadasShipsItsOwnRooms() {
        Set<String> missing = new TreeSet<>();
        for (String key : RoomKeys.requiredFor(
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE))) {
            try (InputStream stream = ShippedPisoTest.class.getClassLoader()
                    .getResourceAsStream("data/teras/structure/dungeon/cuevas_infestadas/"
                            + key + ".nbt")) {
                if (stream == null) {
                    missing.add(key);
                }
            } catch (Exception e) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "cuevas_infestadas is missing: " + missing);
    }

    /** A piso narrowed to one family owes fewer rooms — the lever that makes a variant affordable. */
    @Test
    void narrowingShapesGenuinelyReducesWhatIsOwed() {
        int all = RoomKeys.requiredFor(EnumSet.allOf(ShapeFamily.class)).size();
        int tight = RoomKeys.requiredFor(
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE)).size();
        assertTrue(tight < all, "narrowing formas did not reduce the required rooms");
        for (String key : RoomKeys.requiredFor(
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE))) {
            assertTrue(exists(key),
                    "cuevas cannot even satisfy the narrowed set; missing " + key);
        }
    }
}
