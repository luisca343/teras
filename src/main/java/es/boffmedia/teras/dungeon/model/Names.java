package es.boffmedia.teras.dungeon.model;

/**
 * Turning an id into something a player can read.
 *
 * <p>Ids are the mod's identifiers everywhere — {@code escudo_hyliano}, {@code reina_madre} — and
 * three separate places now need a name for one: an item with no lang entry, an elite's nameplate,
 * and a boss bar. Lang entries only ever exist for what shipped in code, and gear proved what
 * happens without a floor under that: a piece defined in config rendered as
 * {@code item.teras.escudo_hyliano} in a player's hand.</p>
 *
 * <p>Here rather than beside any one caller because the second copy was written within an hour of
 * the first, and because it has to be <b>pure</b> to be tested: anything importing
 * {@code Component} cannot be loaded in a unit test, which is how the enemy version was found.</p>
 */
public final class Names {
    private Names() {}

    /** {@code reina_madre} reads as "Reina Madre". Not a translation; far better than an id. */
    public static String fromId(String id) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        StringBuilder out = new StringBuilder();
        for (String word : id.split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? id : out.toString();
    }
}
