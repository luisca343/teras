package es.boffmedia.teras.plot.model;

import java.util.UUID;

/**
 * One row of the append-only {@code plot_transaction} ledger — never updated except to stamp
 * {@code backendRef}, never deleted. This is the reconciliation record against starbank: a row
 * whose {@code backendRef} is still null is a local sale the backend has not confirmed, which is
 * the set a startup sweep retries.
 */
public record PlotTransaction(long id, String regionName, Kind kind, UUID buyer, UUID seller,
                              long price, String backendRef, long createdAt) {

    /** Mirrors the {@code CHECK} constraint on the table; the json key is what SQLite stores. */
    public enum Kind {
        PURCHASE("purchase"),
        RESALE("resale"),
        ADMIN_GRANT("admin_grant"),
        REVOKE("revoke"),
        EXPIRE("expire");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Kind fromKey(String key) {
            for (Kind kind : values()) {
                if (kind.key.equals(key)) return kind;
            }
            return null;
        }
    }

    /** Whether the backend has acknowledged this row. */
    public boolean isConfirmed() {
        return backendRef != null;
    }
}
