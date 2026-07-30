package es.boffmedia.teras.audio;

import java.util.Locale;

/**
 * What may be used as a track name.
 *
 * <p>A track name reaches the filesystem — {@code config/teras/discos/<name>.<ext>} — so it is the
 * one place a path traversal could get in. Rather than sanitising, this accepts only a known-safe
 * alphabet, which cannot express {@code ..}, a separator, a drive letter or a NUL.</p>
 *
 * <p>Pure and tested; see {@code TrackNameTest}.</p>
 */
public final class TrackName {
    private TrackName() {}

    /** Long enough for a song title, short enough to stay well inside any filesystem limit. */
    public static final int MAX_LENGTH = 48;

    /**
     * Normalises {@code raw} to its canonical form: trimmed, lowercased, spaces to underscores.
     *
     * <p>Lowercased because the store must behave the same on a case-insensitive filesystem as on a
     * case-sensitive one — otherwise {@code Cancion} and {@code cancion} are one track on Windows
     * and two on Linux.</p>
     *
     * @return the normalised name, or {@code null} if it is not usable
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return isValid(name) ? name : null;
    }

    /** True when {@code name} is exactly a canonical, safe track name. */
    public static boolean isValid(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!allowed) {
                return false;
            }
        }
        // A leading dash would make the name look like a flag to anything that ever shells out, and
        // a name of only punctuation is not a name.
        return name.charAt(0) != '-' && !name.chars().allMatch(c -> c == '_' || c == '-');
    }
}
