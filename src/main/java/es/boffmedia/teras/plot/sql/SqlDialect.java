package es.boffmedia.teras.plot.sql;

/**
 * The handful of places where SQLite and MySQL disagree about SQL. Everything else in
 * {@link es.boffmedia.teras.plot.PlotDatabase} is portable, so this stays a small enum rather than
 * a driver abstraction — the point is to name the differences, not to hide them.
 *
 * <p>Both engines ship, and both are load-bearing: SQLite needs no setup, so the tests and
 * single-player run against a temp file; MySQL is the deployment where the SmartRotom backend
 * shares the database and reads plot ownership directly.</p>
 */
public enum SqlDialect {
    SQLITE,
    MYSQL;

    /** Picks the dialect from a JDBC url. Anything unrecognised is treated as MySQL-ish. */
    public static SqlDialect fromDsn(String dsn) {
        return dsn != null && dsn.startsWith("jdbc:sqlite:") ? SQLITE : MYSQL;
    }

    /** Auto-incrementing surrogate key for the ledger. */
    public String autoIncrementPrimaryKey() {
        return this == SQLITE
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "BIGINT AUTO_INCREMENT PRIMARY KEY";
    }

    /**
     * A string column. MySQL cannot index a bare {@code TEXT}, and every one of these is either a
     * primary key or part of one, so they carry an explicit length there.
     */
    public String text(int maxLength) {
        return this == SQLITE ? "TEXT" : "VARCHAR(" + maxLength + ")";
    }

    /**
     * Upsert clause following an {@code INSERT}. SQLite (like Postgres) names the conflicting
     * columns and uses {@code excluded.x}; MySQL infers the key and uses {@code VALUES(x)}.
     *
     * @param keyColumns    the conflicting key, for the dialects that want it named
     * @param assignments   {@code column} names to overwrite from the attempted insert
     */
    public String upsert(String[] keyColumns, String... assignments) {
        StringBuilder sql = new StringBuilder();
        if (this == SQLITE) {
            sql.append("ON CONFLICT(").append(String.join(", ", keyColumns)).append(") DO UPDATE SET ");
            for (int i = 0; i < assignments.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(assignments[i]).append(" = excluded.").append(assignments[i]);
            }
        } else {
            sql.append("ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < assignments.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(assignments[i]).append(" = VALUES(").append(assignments[i]).append(")");
            }
        }
        return sql.toString();
    }

    /**
     * Index over the ledger's pending set. SQLite can make this partial
     * ({@code WHERE backend_ref IS NULL}), which is exactly the rows a reconciliation sweep wants;
     * MySQL has no partial indexes, so it gets a plain one and filters at query time.
     */
    public String pendingIndex(String indexName, String table, String column) {
        String base = "CREATE INDEX " + indexName + " ON " + table + "(" + column + ")";
        return this == SQLITE ? base + " WHERE " + column + " IS NULL" : base;
    }

    /**
     * Whether {@code CREATE INDEX} accepts {@code IF NOT EXISTS}. MySQL does not, so index
     * creation there has to tolerate the "already exists" error instead.
     */
    public boolean supportsCreateIndexIfNotExists() {
        return this == SQLITE;
    }

    /** A truth column. SQLite has no boolean type and stores 0/1 in an INTEGER. */
    public String bool() {
        return this == SQLITE ? "INTEGER" : "TINYINT(1)";
    }

    /**
     * Whether a foreign key can be added to a table that already exists. SQLite cannot — it has no
     * {@code ALTER TABLE ADD CONSTRAINT} — so the table has to be rebuilt around the new
     * constraint instead.
     */
    public boolean canAddForeignKeyInPlace() {
        return this == MYSQL;
    }

    /**
     * Statement that turns foreign key enforcement off or on for the current session.
     *
     * <p>Needed while retrofitting a constraint onto a populated table: the referenced table is
     * empty at that moment, so every existing row would be rejected. Rows that violate the new
     * constraint are deliberately kept rather than deleted — these are ownership and ledger
     * neighbours, and silently dropping one to satisfy a schema change would destroy the record of
     * who paid for what.</p>
     */
    public String foreignKeyEnforcement(boolean enabled) {
        return this == SQLITE
                ? "PRAGMA foreign_keys = " + (enabled ? "ON" : "OFF")
                : "SET FOREIGN_KEY_CHECKS = " + (enabled ? "1" : "0");
    }
}
