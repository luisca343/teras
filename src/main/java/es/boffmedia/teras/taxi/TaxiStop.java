package es.boffmedia.teras.taxi;

/**
 * One taxi destination: a point an admin stood on, with the facing they stood with.
 *
 * <p>Pure — no Minecraft types — so the id rules and the JSON round trip are unit-tested without a
 * server. Overworld only, which is why no dimension is stored: the SmartRotom taxi prices every
 * fare from the player's live overworld {@code x}/{@code z}, so a stop in another dimension would be
 * priced as if it were somewhere it is not.</p>
 *
 * @param id    what the web sends back to travel here. Lowercase, no spaces — it is a key, and it is
 *              also what the fare's ledger concept is built from ({@code "Taxi a <id>"}), so it ends
 *              up in the player's visible travel history
 * @param yaw   the facing an arriving passenger is given. Not on the wire and not needed there —
 *              arriving nose-first into a wall is a bad first frame, and storing it costs nothing
 */
public record TaxiStop(String id, double x, double y, double z, float yaw, float pitch) {

    /** Longest id we accept. Long enough to be descriptive, short enough to read in a ledger row. */
    public static final int MAX_ID_LENGTH = 48;

    public TaxiStop {
        id = normalizeId(id);
    }

    /**
     * {@code id} as it will be stored: trimmed and lowercased. Not a validity check — see
     * {@link #idProblem(String)} — because a record's compact constructor is the wrong place to
     * reject an admin's typo with a message they can act on.
     */
    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Why {@code id} is unusable, or {@code null} if it is fine.
     *
     * <p>Spaces are refused rather than substituted: the id travels to the web and comes back, and a
     * silent rewrite would mean the stop an admin created is not the one they named.</p>
     */
    public static String idProblem(String id) {
        String normalized = normalizeId(id);
        if (normalized.isEmpty()) {
            return "el id no puede estar vacío";
        }
        if (normalized.length() > MAX_ID_LENGTH) {
            return "el id no puede pasar de " + MAX_ID_LENGTH + " caracteres";
        }
        if (!normalized.matches("[a-z0-9_-]+")) {
            return "el id sólo admite letras, números, '-' y '_' (sin espacios ni acentos)";
        }
        return null;
    }
}
