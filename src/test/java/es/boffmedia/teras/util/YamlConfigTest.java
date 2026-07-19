package es.boffmedia.teras.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlConfigTest {

    @TempDir
    Path directory;

    private static YamlConfig parse(String yaml) {
        return YamlConfig.parse(new StringReader(yaml));
    }

    @Test
    void readsScalarsOfEachType() {
        YamlConfig yaml = parse("""
                home: "http://teras.es/smartrotom"
                httpPort: 34370
                httpEnabled: true
                """);
        assertEquals("http://teras.es/smartrotom", yaml.string("home", "fallback"));
        assertEquals(34370, yaml.integer("httpPort", 1));
        assertTrue(yaml.bool("httpEnabled", false));
    }

    @Test
    void missingKeysKeepTheirDefaults() {
        YamlConfig yaml = parse("home: \"x\"\n");
        assertEquals("fallback", yaml.string("API_URL", "fallback"));
        assertEquals(99, yaml.integer("httpPort", 99));
        assertTrue(yaml.bool("httpEnabled", true));
        assertFalse(yaml.has("API_URL"));
    }

    /** An explicit null is "not set", not a value — otherwise it would blank a real default. */
    @Test
    void explicitNullCountsAsAbsent() {
        YamlConfig yaml = parse("apiToken:\n");
        assertFalse(yaml.has("apiToken"));
        assertEquals("keep-me", yaml.string("apiToken", "keep-me"));
    }

    @Test
    void commentsAndBlankLinesAreIgnored() {
        YamlConfig yaml = parse("""
                # a leading comment
                httpPort: 25565   # trailing comment

                # another
                httpBind: "0.0.0.0"
                """);
        assertEquals(25565, yaml.integer("httpPort", 0));
        assertEquals("0.0.0.0", yaml.string("httpBind", ""));
    }

    @Test
    void readsNestedBlocks() {
        YamlConfig yaml = parse("""
                sql:
                  use: true
                  dsn: "jdbc:mysql://10.0.0.5:3306/teras"
                  tablePrefix: "teras_"
                """);
        assertTrue(yaml.has("sql"));
        YamlConfig sql = yaml.section("sql");
        assertTrue(sql.bool("use", false));
        assertEquals("jdbc:mysql://10.0.0.5:3306/teras", sql.string("dsn", ""));
        assertEquals("teras_", sql.string("tablePrefix", ""));
    }

    /**
     * The one place YAML genuinely differs from JSON: JSON keys are always strings, YAML types
     * them. A payout table written the natural way has Integer keys, which must still be
     * addressable — and iterable — as strings.
     */
    @Test
    void numericKeysAreUsableAsStrings() {
        YamlConfig pagos = parse("""
                pagos:
                  1: 1000
                  2: 500
                  participacion: 50
                """).section("pagos");

        assertEquals(java.util.Set.of("1", "2", "participacion"), pagos.keys());
        assertEquals(1000, pagos.integer("1", 0));
        assertEquals(50, pagos.integer("participacion", 0));
        // Iterating into a String must not throw, which is what an unnormalised map would do.
        for (String key : pagos.keys()) {
            assertFalse(key.isEmpty());
        }
    }

    /** Same hazard one level up: a top-level numeric or boolean key must not break the wrapper. */
    @Test
    void nonStringTopLevelKeysDoNotBreakParsing() {
        YamlConfig yaml = parse("""
                1: "uno"
                true: "si"
                home: "x"
                """);
        assertEquals("uno", yaml.string("1", ""));
        assertEquals("si", yaml.string("true", ""));
        assertEquals("x", yaml.string("home", ""));
    }

    @Test
    void missingSectionIsEmptyRatherThanNull() {
        assertFalse(parse("home: \"x\"\n").section("sql").has("use"));
    }

    @Test
    void readsLists() {
        YamlConfig inline = parse("puntosGp: [10, 8, 6]\n");
        assertEquals(3, inline.list("puntosGp").size());
        YamlConfig block = parse("""
                puntosGp:
                  - 10
                  - 8
                """);
        assertEquals(2, block.list("puntosGp").size());
    }

    /** A password of digits, or a version like 1.10, must not be silently retyped. */
    @Test
    void quotedScalarsStayStrings() {
        YamlConfig yaml = parse("""
                password: "12345"
                httpBind: "127.0.0.1"
                """);
        assertEquals("12345", yaml.string("password", ""));
        assertEquals("127.0.0.1", yaml.string("httpBind", ""));
    }

    /** YAML 1.1 habits die hard: an unquoted no/yes is a boolean, which surprises people. */
    @Test
    void unquotedBooleanLikeStringsCoerceSensibly() {
        assertFalse(parse("httpEnabled: false\n").bool("httpEnabled", true));
        assertTrue(parse("httpEnabled: \"true\"\n").bool("httpEnabled", false),
                "a quoted true is an admin mistake worth honouring, not ignoring");
    }

    @Test
    void wrongTypeFallsBackInsteadOfThrowing() {
        YamlConfig yaml = parse("""
                httpPort: "not a number"
                httpEnabled: "maybe"
                sql: "not a block"
                puntosGp: "not a list"
                """);
        assertEquals(34370, yaml.integer("httpPort", 34370));
        assertTrue(yaml.bool("httpEnabled", true));
        assertFalse(yaml.section("sql").has("use"));
        assertTrue(yaml.list("puntosGp").isEmpty());
    }

    @Test
    void malformedFileReadsEmptyRatherThanThrowing() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "home: \"x\"\n  bad: indentation: here\n");
        assertEquals("fallback", YamlConfig.read(file).string("home", "fallback"));
    }

    @Test
    void missingFileReadsEmpty() {
        assertFalse(YamlConfig.read(directory.resolve("nope.yml")).has("home"));
    }

    /** A duplicated key is an admin editing mistake that would otherwise silently pick one. */
    @Test
    void duplicateKeysAreRejected() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "httpPort: 1\nhttpPort: 2\n");
        assertEquals(34370, YamlConfig.read(file).integer("httpPort", 34370));
    }

    @Test
    void writeIsAtomicAndLeavesNoTempFile() throws Exception {
        Path file = directory.resolve("config.yml");
        YamlConfig.write(file, "# hello\nhttpPort: 1\n");
        YamlConfig.write(file, "# hello\nhttpPort: 2\n");
        assertEquals(2, YamlConfig.read(file).integer("httpPort", 0));
        try (var entries = Files.list(directory)) {
            assertEquals(1, entries.count(), "the .tmp file must not survive the move");
        }
    }

    @Test
    void writtenCommentsSurviveAReRead() throws Exception {
        Path file = directory.resolve("config.yml");
        YamlConfig.write(file, "# explain the key\nhttpPort: 7\n");
        assertTrue(Files.readString(file).contains("# explain the key"),
                "comments are the whole reason this is YAML");
        assertEquals(7, YamlConfig.read(file).integer("httpPort", 0));
    }
}
