package es.boffmedia.teras.dex.api;

import java.util.List;

/**
 * A player's whole Pokédex as national dex numbers, for {@code POST /updatedex}.
 *
 * <p>The backend reads {@code .SEEN} and {@code .CAUGHT} off this to bulk-write its own table, so the
 * field names are the wire and are upper-case deliberately. A dex number appears in <b>one</b> list:
 * catching a species replaces its seen entry, which is how 1.16.5 grouped it.</p>
 */
public record DexSnapshot(List<Integer> SEEN, List<Integer> CAUGHT) {
}
