package es.boffmedia.teras.dungeon.gear;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loot tables, now that they no longer name gear.
 *
 * <h2>What this test used to be</h2>
 *
 * <p>Tables named pieces outright — an item plus a {@code teras:gear_id} component — and the two had
 * to agree, because migration silently rebuilds a mismatched stack on pickup: the game looked
 * correct while the table was wrong. This test existed to catch that, and did on the day it was
 * written ({@code martillo_rompemuros} was a hammer riding a netherite axe while its definition said
 * SWORD, because SWORD was the only melee kind that existed when it was authored).</p>
 *
 * <h2>What it is now</h2>
 *
 * <p>That whole bug class is gone, because the tables no longer name pieces at all: they ask
 * {@code teras:gear_aleatorio} for a rarity and the catalog answers with a piece. So the job flipped
 * from "do the two catalogs agree" to <b>"is there still only one catalog"</b>. A hardcoded
 * {@code gear_id} reintroduced anywhere brings the second one back, and with it a piece
 * {@code gear.json} can never change and a rarity that means nothing.</p>
 */
class GearLootTablesTest {

    private static final List<String> TABLES = List.of("treasure", "boss", "devil");

    private static String read(String table) {
        String path = "data/teras/loot_table/dungeon/" + table + ".json";
        try (InputStream stream = GearLootTablesTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(stream, path + " is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    void noTableNamesAPieceOfGear() {
        List<String> offenders = new ArrayList<>();
        for (String table : TABLES) {
            if (read(table).contains("teras:gear_id")) {
                offenders.add(table);
            }
        }
        assertEquals(List.of(), offenders,
                "a hardcoded gear_id makes the table a second catalog: the piece it names can no "
                        + "longer be retuned from gear.json, and a piece added there can never drop");
    }

    @Test
    void gearComesFromTheCatalogFunction() {
        int functions = 0;
        for (String table : TABLES) {
            Matcher uses = Pattern.compile("teras:gear_aleatorio").matcher(read(table));
            while (uses.find()) {
                functions++;
            }
        }
        assertTrue(functions >= TABLES.size(),
                "only " + functions + " gear_aleatorio uses across " + TABLES.size()
                        + " tables — a dungeon table that drops no gear at all is probably a mistake");
    }

    /**
     * A function whose weights sum to zero draws nothing, and an empty stack is indistinguishable
     * from a table that meant to give nothing — the silent-blank shape that made the boss pedestal
     * look broken in the first place.
     */
    @Test
    void everyGearFunctionCanActuallyDrawSomething() {
        Pattern block = Pattern.compile("\"function\"\\s*:\\s*\"teras:gear_aleatorio\"(.*?)\\}");
        int checked = 0;
        for (String table : TABLES) {
            Matcher found = block.matcher(read(table));
            while (found.find()) {
                checked++;
                int total = weight(found.group(1), "comun", 50)
                        + weight(found.group(1), "raro", 35)
                        + weight(found.group(1), "epico", 15);
                assertTrue(total > 0, table + " has a gear_aleatorio with every weight at zero");
            }
        }
        assertTrue(checked > 0, "the test found no gear_aleatorio block to check");
    }

    private static int weight(String json, String key, int fallback) {
        Matcher found = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return found.find() ? Integer.parseInt(found.group(1)) : fallback;
    }

    /** The boss is the floor's payoff, and it must not be able to hand over nothing. */
    @Test
    void theBossAlwaysDropsAPiece() {
        String boss = read("boss");
        int firstPool = boss.indexOf("\"entries\"");
        int secondPool = boss.indexOf("\"entries\"", firstPool + 1);
        String pool = boss.substring(firstPool, secondPool < 0 ? boss.length() : secondPool);
        assertTrue(pool.contains("teras:gear_aleatorio"),
                "the boss table's first pool should be the guaranteed gear drop");
        assertTrue(!pool.contains("minecraft:empty"),
                "the boss's gear pool must not roll empty — it was 57% empty, which is what made "
                        + "killing a floor boss give nothing more than half the time");
    }
}
