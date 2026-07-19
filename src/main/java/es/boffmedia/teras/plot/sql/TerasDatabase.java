package es.boffmedia.teras.plot.sql;

import com.mysql.cj.jdbc.MysqlDataSource;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.TerasConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The one database the mod owns, and the schema it keeps up to date. Regions, plots, plot members
 * and the money ledger all live here; {@code RegionDatabase} and {@code PlotDatabase} are query
 * facades over this, so there is a single place that decides which engine is in use and a single
 * migration ladder.
 *
 * <p>Two backends, chosen by {@link TerasConfig#sql()}: <b>SQLite</b> (default, no setup, what the
 * tests and single-player use) and <b>MySQL</b> (the deployment where the SmartRotom backend shares
 * this database and reads regions and plot ownership directly). {@link SqlDialect} carries the
 * places the two disagree.</p>
 *
 * <p><b>A connection per operation</b>, not one held open: a long-lived MySQL connection dies to
 * {@code wait_timeout} and resurfaces as a confusing failure much later. Writes here are rare and
 * server-thread-only, and every hot-path read is served from an in-memory snapshot instead.</p>
 */
public final class TerasDatabase implements AutoCloseable {

    /** Bump alongside a new case in {@link #applyMigration}; migrations are forward-only. */
    private static final int SCHEMA_VERSION = 2;

    /** Region, dimension and player names; UUIDs are 36. MySQL cannot index a bare TEXT. */
    public static final int NAME_LENGTH = 128;
    public static final int UUID_LENGTH = 36;

    private final DataSource source;
    private final SqlDialect dialect;
    private final String prefix;

    private TerasDatabase(DataSource source, SqlDialect dialect, String prefix) {
        this.source = source;
        this.dialect = dialect;
        this.prefix = prefix;
    }

    /**
     * Opens the configured backend and brings its schema up to date. Throws if that cannot be
     * done: refusing to start beats running on a schema nobody has checked.
     *
     * @param sqliteFile where the local file lives when SQLite is the configured backend
     */
    public static TerasDatabase open(TerasConfig.SqlSettings settings, Path sqliteFile)
            throws SQLException {
        String prefix = settings.tablePrefix() == null || settings.tablePrefix().isBlank()
                ? TerasConfig.SqlSettings.DEFAULT_TABLE_PREFIX
                : settings.tablePrefix();
        TerasDatabase database = settings.use() ? mysql(settings, prefix) : sqlite(sqliteFile, prefix);
        database.migrate();
        return database;
    }

    /** Test and single-player entry point: a local file, default prefix, no configuration. */
    public static TerasDatabase openSqlite(Path file) throws SQLException {
        TerasDatabase database = sqlite(file, TerasConfig.SqlSettings.DEFAULT_TABLE_PREFIX);
        database.migrate();
        return database;
    }

    // ---- The shared instance ----

    private static volatile TerasDatabase shared;

    /**
     * Opens the shared database if it is not already open, and returns it. Both
     * {@code RegionStore} and {@code PlotStore} call this from their own server-start handlers;
     * making it idempotent means neither has to run first, which handler registration order does
     * not guarantee.
     */
    public static synchronized TerasDatabase ensureOpen(Path sqliteFile) throws SQLException {
        if (shared == null) {
            shared = open(TerasConfig.sql(), sqliteFile);
        }
        return shared;
    }

    /** The shared database, or {@code null} when it is not open. */
    public static TerasDatabase shared() {
        return shared;
    }

    /** Closes and forgets the shared database, so the next {@link #ensureOpen} reopens it. */
    public static synchronized void closeShared() {
        if (shared == null) return;
        shared.close();
        shared = null;
    }

    private static TerasDatabase sqlite(Path file, String prefix) throws SQLException {
        try {
            Files.createDirectories(file.getParent());
        } catch (Exception e) {
            throw new SQLException("cannot create database directory " + file.getParent(), e);
        }
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
        // Declared CASCADE and RESTRICT rules do nothing unless this is on; SQLite defaults it off.
        source.setEnforceForeignKeys(true);
        TerasDatabase database = new TerasDatabase(source, SqlDialect.SQLITE, prefix);
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement()) {
            // WAL is the crash-safety story: a torn write rolls back to the last commit instead of
            // corrupting the file. Persisted in the file, so it only has to be set once.
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return database;
    }

    private static TerasDatabase mysql(TerasConfig.SqlSettings settings, String prefix) {
        MysqlDataSource source = new MysqlDataSource();
        source.setUrl(settings.dsn());
        if (settings.username() != null && !settings.username().isEmpty()) {
            source.setUser(settings.username());
        }
        if (settings.password() != null && !settings.password().isEmpty()) {
            source.setPassword(settings.password());
        }
        return new TerasDatabase(source, SqlDialect.MYSQL, prefix);
    }

    public SqlDialect dialect() {
        return dialect;
    }

    /** The prefixed name of one of our tables, e.g. {@code plot} → {@code teras_plot}. */
    public String table(String name) {
        return prefix + name;
    }

    public String prefix() {
        return prefix;
    }

    public Connection connect() throws SQLException {
        return source.getConnection();
    }

    @Override
    public void close() {
        // Nothing to hold open: every operation takes and returns its own connection.
    }

    // ---- Schema ----

    private void migrate() throws SQLException {
        try (Connection connection = connect()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + table("schema_version")
                        + " (version INTEGER NOT NULL)");
            }
            int current = 0;
            try (Statement statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT version FROM " + table("schema_version"))) {
                if (rows.next()) current = rows.getInt(1);
            }
            if (current > SCHEMA_VERSION) {
                throw new SQLException("the Teras database was written by a newer version of the mod "
                        + "(schema " + current + " > " + SCHEMA_VERSION + "); refusing to downgrade it");
            }
            if (current == SCHEMA_VERSION) return;

            // Deliberately not wrapped in one transaction: MySQL commits DDL implicitly, so a
            // rollback would be a lie there. Every step is instead written to be idempotent, so a
            // half-applied migration can simply be re-run.
            for (int version = current + 1; version <= SCHEMA_VERSION; version++) {
                applyMigration(connection, version);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM " + table("schema_version"));
                    statement.execute("INSERT INTO " + table("schema_version")
                            + " (version) VALUES (" + version + ")");
                }
                Teras.LOGGER.info("TerasDatabase: schema migrated to v{} ({})", version, dialect);
            }
        }
    }

    private void applyMigration(Connection connection, int version) throws SQLException {
        switch (version) {
            case 1 -> createPlotTables(connection);
            case 2 -> createRegionTables(connection);
            default -> { }
        }
    }

    private void createPlotTables(Connection connection) throws SQLException {
        String name = dialect.text(NAME_LENGTH);
        String uuid = dialect.text(UUID_LENGTH);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + table("plot") + " ("
                    + "region_name " + name + " PRIMARY KEY, "
                    + "dimension "   + name + " NOT NULL, "
                    + "owner_uuid "  + uuid + ", "
                    + "owned_since BIGINT, "
                    + "expires_at  BIGINT, "
                    // Lets a reader detect changes it did not make. The SmartRotom backend shares
                    // this database and can move ownership behind the mod's back.
                    + "updated_at  BIGINT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS " + table("plot_member") + " ("
                    + "region_name " + name + " NOT NULL, "
                    + "player_uuid " + uuid + " NOT NULL, "
                    + "added_at BIGINT NOT NULL, "
                    + "added_by " + name + " NOT NULL, "
                    + "PRIMARY KEY (region_name, player_uuid), "
                    + "FOREIGN KEY (region_name) REFERENCES " + table("plot") + "(region_name) "
                    + "ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS " + table("plot_transaction") + " ("
                    + "id " + dialect.autoIncrementPrimaryKey() + ", "
                    + "region_name " + name + " NOT NULL, "
                    + "kind " + dialect.text(16) + " NOT NULL CHECK (kind IN "
                    + "('purchase','resale','admin_grant','revoke','expire')), "
                    + "buyer_uuid "  + uuid + ", "
                    + "seller_uuid " + uuid + ", "
                    + "price BIGINT NOT NULL, "
                    + "backend_ref " + name + ", "
                    + "created_at BIGINT NOT NULL)");
        }
        createIndex(connection, "CREATE INDEX " + ifNotExists() + "idx_" + prefix + "member_player ON "
                + table("plot_member") + "(player_uuid)");
        createIndex(connection, "CREATE INDEX " + ifNotExists() + "idx_" + prefix + "tx_region ON "
                + table("plot_transaction") + "(region_name)");
        createIndex(connection, dialect.pendingIndex(ifNotExists() + "idx_" + prefix + "tx_pending",
                table("plot_transaction"), "backend_ref"));
    }

    /**
     * v2 — region geometry moves into the database, and {@code plot} gains the foreign key that
     * was impossible while regions lived in a json file.
     */
    private void createRegionTables(Connection connection) throws SQLException {
        String name = dialect.text(NAME_LENGTH);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + table("region") + " ("
                    + "name " + name + " PRIMARY KEY, "
                    + "dimension " + name + " NOT NULL, "
                    + "shape " + dialect.text(16) + " NOT NULL, "
                    + "priority INT NOT NULL DEFAULT 0, "
                    + "purchasable " + dialect.bool() + " NOT NULL DEFAULT 0, "
                    + "price BIGINT NOT NULL DEFAULT 0, "
                    + "fill_color INT NOT NULL DEFAULT 0, "
                    + "stroke_color INT NOT NULL DEFAULT 0, "
                    + "banner " + name + ", "
                    // Point order is significant (polygon winding), which is why geometry is one
                    // ordered json document rather than a child table where a stray ORDER BY could
                    // silently reshape the region.
                    + "geometry TEXT NOT NULL, "
                    + "flags TEXT, "
                    + "created_by " + name + ", "
                    + "created_at BIGINT NOT NULL DEFAULT 0, "
                    + "updated_at BIGINT NOT NULL DEFAULT 0)");
        }
        createIndex(connection, "CREATE INDEX " + ifNotExists() + "idx_" + prefix + "region_dimension ON "
                + table("region") + "(dimension)");
        addPlotRegionForeignKey(connection);
    }

    /**
     * Retrofits {@code plot.region_name → region.name}. Enforcement is off across this: the region
     * table is empty at this point, so every existing plot would be rejected. Rows that do not
     * match a region are <b>kept</b> and reported, never deleted — they are ownership records with
     * a ledger behind them, and a schema change is not a licence to destroy one.
     */
    private void addPlotRegionForeignKey(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            // Cannot be inside a transaction on SQLite, hence autoCommit above.
            statement.execute(dialect.foreignKeyEnforcement(false));
        }
        try {
            if (dialect.canAddForeignKeyInPlace()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE " + table("plot")
                            + " ADD CONSTRAINT fk_" + prefix + "plot_region"
                            + " FOREIGN KEY (region_name) REFERENCES " + table("region") + "(name)"
                            + " ON DELETE RESTRICT");
                }
            } else {
                rebuildPlotTableWithForeignKey(connection);
            }
        } catch (SQLException e) {
            // An interrupted earlier run may already have added it; a duplicate is not a failure.
            Teras.LOGGER.warn("TerasDatabase: could not add the plot->region foreign key ({}). "
                    + "Integrity will be enforced by the mod instead.", e.getMessage());
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute(dialect.foreignKeyEnforcement(true));
            }
            connection.setAutoCommit(autoCommit);
        }
    }

    /** SQLite has no {@code ADD CONSTRAINT}, so the table is rebuilt around the new one. */
    private void rebuildPlotTableWithForeignKey(Connection connection) throws SQLException {
        String name = dialect.text(NAME_LENGTH);
        String uuid = dialect.text(UUID_LENGTH);
        String temp = table("plot") + "_v2";
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + temp);
            statement.execute("CREATE TABLE " + temp + " ("
                    + "region_name " + name + " PRIMARY KEY, "
                    + "dimension "   + name + " NOT NULL, "
                    + "owner_uuid "  + uuid + ", "
                    + "owned_since BIGINT, "
                    + "expires_at  BIGINT, "
                    + "updated_at  BIGINT NOT NULL DEFAULT 0, "
                    + "FOREIGN KEY (region_name) REFERENCES " + table("region") + "(name) "
                    + "ON DELETE RESTRICT)");
            statement.execute("INSERT INTO " + temp + " (region_name, dimension, owner_uuid, "
                    + "owned_since, expires_at, updated_at) SELECT region_name, dimension, "
                    + "owner_uuid, owned_since, expires_at, updated_at FROM " + table("plot"));
            statement.execute("DROP TABLE " + table("plot"));
            statement.execute("ALTER TABLE " + temp + " RENAME TO " + table("plot"));
        }
    }

    private String ifNotExists() {
        return dialect.supportsCreateIndexIfNotExists() ? "IF NOT EXISTS " : "";
    }

    /** MySQL has no {@code CREATE INDEX IF NOT EXISTS}, so a re-run must tolerate the duplicate. */
    private void createIndex(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            if (dialect.supportsCreateIndexIfNotExists()) throw e;
            Teras.LOGGER.debug("TerasDatabase: index already present ({})", e.getMessage());
        }
    }
}
