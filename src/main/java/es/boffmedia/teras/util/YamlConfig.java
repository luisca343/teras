package es.boffmedia.teras.util;

import es.boffmedia.teras.Teras;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Read-side helper for the admin-facing YAML configs. Deliberately thin: it exposes the same
 * "is the key present, else keep the default" shape the JSON configs used, so the config classes
 * kept their structure when they moved off Gson.
 *
 * <p><b>Reading only.</b> Nothing here serializes a config back out, and that is the point —
 * YAML is here for comments, and every YAML serializer drops them. Defaults and migrations are
 * written by rendering a commented template ({@link #write}), never by dumping an object graph, so
 * the explanations next to each key survive. This is also why the data stores stayed JSON: they are
 * rewritten on every mutation, which no amount of care makes comment-safe.</p>
 */
public final class YamlConfig {

    private final Map<String, Object> values;

    /**
     * Keys are normalised to strings on the way in. This is the one place YAML differs from JSON
     * in a way that bites: JSON object keys are always strings, but YAML types them, so a payout
     * table written the natural way —
     * <pre>
     * pagos:
     *   1: 1000
     * </pre>
     * yields {@link Integer} keys. Without this, every lookup by {@code "1"} would miss and any
     * iteration into a {@code String} would throw {@link ClassCastException}.
     */
    private YamlConfig(Map<?, ?> values) {
        if (values == null || values.isEmpty()) {
            this.values = Map.of();
            return;
        }
        Map<String, Object> normalised = new java.util.LinkedHashMap<>(values.size());
        values.forEach((key, value) -> normalised.put(String.valueOf(key), value));
        this.values = normalised;
    }

    /** An empty config — what a missing or unreadable file resolves to. */
    public static YamlConfig empty() {
        return new YamlConfig(Map.of());
    }

    /**
     * Parses YAML. Uses {@link SafeConstructor}, so a config file can only produce plain maps,
     * lists and scalars — never arbitrary instantiated classes.
     */
    public static YamlConfig parse(Reader reader) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object loaded = new Yaml(new SafeConstructor(options)).load(reader);
        return loaded instanceof Map<?, ?> map ? new YamlConfig(map) : empty();
    }

    /** Parses a file, returning {@link #empty()} (and logging) rather than throwing. */
    public static YamlConfig read(Path file) {
        if (!Files.exists(file)) return empty();
        try (Reader reader = Files.newBufferedReader(file)) {
            return parse(reader);
        } catch (Exception e) {
            Teras.LOGGER.error("Could not parse {} — falling back to defaults. Fix the YAML "
                    + "(check indentation and that values with ':' or '#' are quoted): {}",
                    file.getFileName(), e.getMessage());
            return empty();
        }
    }

    /**
     * Writes {@code content} verbatim, atomically. Verbatim because the content is a rendered
     * template complete with its comments; a temp file plus {@code ATOMIC_MOVE} because a config
     * truncated by a crash would take the server's identity and credentials with it.
     */
    public static void write(Path file, String content) throws java.io.IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, content);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Whether the key is present and not explicitly null. */
    public boolean has(String key) {
        return values.get(key) != null;
    }

    public String string(String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean b) return b;
        if (value == null) return fallback;
        // A hand-edited "true" in quotes is a mistake worth honouring rather than silently ignoring.
        String text = String.valueOf(value).trim();
        if (text.equalsIgnoreCase("true")) return true;
        if (text.equalsIgnoreCase("false")) return false;
        warnType(key, value, "a boolean");
        return fallback;
    }

    public int integer(String key, int fallback) {
        Number number = number(key);
        return number == null ? fallback : number.intValue();
    }

    public double doubleValue(String key, double fallback) {
        Number number = number(key);
        return number == null ? fallback : number.doubleValue();
    }

    public long longValue(String key, long fallback) {
        Number number = number(key);
        return number == null ? fallback : number.longValue();
    }

    /** A nested block, or an empty config when the key is absent or not a block. */
    public YamlConfig section(String key) {
        Object value = values.get(key);
        if (value instanceof Map<?, ?> map) return new YamlConfig(map);
        if (value != null) warnType(key, value, "a block of keys");
        return empty();
    }

    /** A list of values, or an empty list when the key is absent or not a list. */
    public List<Object> list(String key) {
        Object value = values.get(key);
        if (value instanceof List<?> list) return List.copyOf(list);
        if (value != null) warnType(key, value, "a list");
        return List.of();
    }

    /** Every key present at this level, for callers that iterate rather than look up. */
    public java.util.Set<String> keys() {
        return values.keySet();
    }

    /** The raw value, for callers that do their own coercion (BigDecimal, enums...). */
    public Object raw(String key) {
        return values.get(key);
    }

    private Number number(String key) {
        Object value = values.get(key);
        if (value instanceof Number n) return n;
        if (value == null) return null;
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            warnType(key, value, "a number");
            return null;
        }
    }

    /** A wrong type is an admin typo, so name the key and what was expected, then carry on. */
    private static void warnType(String key, Object value, String expected) {
        Teras.LOGGER.warn("Config key '{}' should be {} but is '{}' — ignoring it and keeping the "
                + "default", key, expected, value);
    }
}
