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
 * The loot tables against the catalog.
 *
 * <p>A gear drop is an item plus a {@code teras:gear_id} component, and the two have to agree: the
 * item must be the one that piece's {@link GearKind} is built on. They can disagree silently —
 * migration rebuilds a mismatched stack the moment a player picks it up, so the game looks correct
 * and the table is simply wrong. Same shape as every other "authored and never noticed" bug here,
 * and cheap to close because both halves are readable without a server.</p>
 *
 * <p>It caught one the day it was written: {@code martillo_rompemuros} is a hammer that rode on a
 * netherite axe while its definition said {@code SWORD}, because SWORD was the only melee kind that
 * existed when it was authored.</p>
 */
class GearLootTablesTest {

    private static final List<String> TABLES = List.of("treasure", "boss", "curse", "devil");

    /** Every loot entry in a table, as its own chunk of json. */
    private record Entry(String table, String item, String gearId) {}

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

    /**
     * Splits on the entry marker rather than matching name and id in one pattern. A single regex
     * spanning both happily pairs one entry's item with the next entry's gear id, which is how the
     * first version of this test reported four failures that were not real.
     */
    private static List<Entry> entriesOf(String table) {
        String json = read(table);
        List<Entry> entries = new ArrayList<>();
        Matcher starts = Pattern.compile("\"type\"\\s*:\\s*\"minecraft:item\"").matcher(json);
        List<Integer> offsets = new ArrayList<>();
        while (starts.find()) {
            offsets.add(starts.start());
        }
        for (int i = 0; i < offsets.size(); i++) {
            int from = offsets.get(i);
            int to = i + 1 < offsets.size() ? offsets.get(i + 1) : json.length();
            String chunk = json.substring(from, to);
            Matcher name = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(chunk);
            Matcher gear = Pattern.compile("\"teras:gear_id\"\\s*:\\s*\"([^\"]+)\"").matcher(chunk);
            if (name.find() && gear.find()) {
                entries.add(new Entry(table, name.group(1), gear.group(1)));
            }
        }
        return entries;
    }

    @Test
    void everyGearDropUsesTheItemItsKindIsBuiltOn() {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (String table : TABLES) {
            for (Entry entry : entriesOf(table)) {
                GearDef def = GearDefs.defaults().get(entry.gearId());
                if (def == null) {
                    continue;
                }
                checked++;
                String expected = "teras:" + def.kind().itemPath();
                if (!expected.equals(entry.item())) {
                    wrong.add(entry.table() + ": " + entry.gearId() + " (" + def.kind()
                            + ") drops as " + entry.item() + ", should be " + expected);
                }
            }
        }
        assertEquals(List.of(), wrong);
        assertTrue(checked >= 10, "only matched " + checked + " gear entries — the test is not "
                + "reading the tables properly");
    }

    /** An id the tables name but the catalog does not have is a component pointing at nothing. */
    @Test
    void everyGearIdInATableIsInTheCatalog() {
        for (String table : TABLES) {
            for (Entry entry : entriesOf(table)) {
                assertNotNull(GearDefs.defaults().get(entry.gearId()),
                        table + " drops unknown gear id '" + entry.gearId() + "'");
            }
        }
    }
}
