package es.boffmedia.teras.shiny;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who has been shown which shiny, and when they may be shown it again. Pure and tested — the cue's
 * whole cadence lives here, so "how often does a shiny sparkle at you" is answerable from one file.
 *
 * <p><b>Keyed by the Pokémon's UUID, not its network id.</b> The network id is per-load and gets
 * recycled, so a shiny whose chunk unloaded and came back was a different Pokémon to this ledger and
 * chimed all over again. The UUID survives unload, reload and a dimension change.</p>
 *
 * <p><b>Per player.</b> The 1.16.5 tracker kept one server-wide set, so a shiny sparkled once for
 * whoever happened to be nearest and stayed silent for everyone else forever after.</p>
 *
 * <p><b>Bounded by size, never by age.</b> An age-based expiry cannot coexist with "only once": the
 * row <i>is</i> the memory of having shown it, so evicting it re-arms the cue. Eviction is therefore
 * least-recently-seen once the map gets implausibly large, which on any real server never happens —
 * the ledger holds one row per shiny each player has actually laid eyes on.</p>
 */
public final class ShinyLedger {

    /**
     * Rows kept before the least-recently-seen are dropped. A player meeting {@value} distinct
     * shinies in one server uptime is not a case worth spending memory to be exact about; the cap
     * exists only so a pathological world cannot grow this map without limit.
     */
    static final int MAX_ENTRIES = 20_000;

    private final Map<Key, Row> rows = new HashMap<>();

    private record Key(UUID player, UUID pokemon) {}

    /**
     * @param shown when the cue last fired — what the repeat interval is measured against
     * @param seen  when this pair was last <i>considered</i>, fired or not. Only drives eviction, so
     *              a shiny a player keeps meeting is never the one dropped
     */
    private record Row(long shown, long seen) {}

    /**
     * Claims the right to sparkle {@code pokemon} at {@code player} on tick {@code now}, given a
     * repeat interval of {@code intervalTicks}. Returns false — and does not move the interval — if
     * the cue already fired for this pair too recently.
     *
     * <p>{@code intervalTicks} of zero or less means <b>once ever</b>, which is the shipped default:
     * the first claim succeeds and no later one does.</p>
     */
    public boolean claim(UUID player, UUID pokemon, long now, int intervalTicks) {
        Key key = new Key(player, pokemon);
        Row row = rows.get(key);
        if (row != null && (intervalTicks <= 0 || now - row.shown() < intervalTicks)) {
            // Refused, but still seen: this is what keeps a shiny the player is standing next to at
            // the top of the eviction order instead of aging out and chiming again.
            rows.put(key, new Row(row.shown(), now));
            return false;
        }
        rows.put(key, new Row(now, now));
        enforceCap();
        return true;
    }

    /** Whether {@code player} has already been shown {@code pokemon}. */
    public boolean hasSeen(UUID player, UUID pokemon) {
        return rows.containsKey(new Key(player, pokemon));
    }

    /**
     * Drops every entry for {@code player}.
     *
     * <p><b>Not called on logout</b>, deliberately. It was, and that made a relog re-announce every
     * shiny standing around — precisely the repetition "only once" is meant to remove. It survives
     * for {@code /teras shiny olvidar}, where forgetting is the point.</p>
     */
    public void forget(UUID player) {
        rows.keySet().removeIf(key -> key.player().equals(player));
    }

    /** Live entry count. */
    public int size() {
        return rows.size();
    }

    /** Empties the ledger — server stopping, or an admin re-arming the cue. */
    public void clear() {
        rows.clear();
    }

    /** Drops least-recently-seen rows until the map is back inside {@link #MAX_ENTRIES}. */
    private void enforceCap() {
        while (rows.size() > MAX_ENTRIES) {
            rows.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().seen()))
                    .map(Map.Entry::getKey)
                    .ifPresent(rows::remove);
        }
    }
}
