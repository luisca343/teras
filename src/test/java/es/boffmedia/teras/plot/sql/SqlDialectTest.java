package es.boffmedia.teras.plot.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MySQL half of these can't be exercised against a live server in CI, so the generated SQL is
 * asserted directly. Every case here is a difference that would otherwise only surface as a syntax
 * error on someone's production MySQL.
 */
class SqlDialectTest {

    @Test
    void dialectComesFromTheJdbcUrl() {
        assertEquals(SqlDialect.SQLITE, SqlDialect.fromDsn("jdbc:sqlite:/srv/teras.db"));
        assertEquals(SqlDialect.MYSQL, SqlDialect.fromDsn("jdbc:mysql://10.0.0.5:3306/teras"));
        assertEquals(SqlDialect.MYSQL, SqlDialect.fromDsn("jdbc:mariadb://host/teras"));
        assertEquals(SqlDialect.MYSQL, SqlDialect.fromDsn(null));
    }

    @Test
    void autoIncrementSpellingDiffers() {
        assertEquals("INTEGER PRIMARY KEY AUTOINCREMENT", SqlDialect.SQLITE.autoIncrementPrimaryKey());
        assertEquals("BIGINT AUTO_INCREMENT PRIMARY KEY", SqlDialect.MYSQL.autoIncrementPrimaryKey());
    }

    /** MySQL cannot index a bare TEXT, and every string column here is part of a key. */
    @Test
    void mysqlStringColumnsCarryALength() {
        assertEquals("TEXT", SqlDialect.SQLITE.text(128));
        assertEquals("VARCHAR(128)", SqlDialect.MYSQL.text(128));
    }

    @Test
    void sqliteUpsertNamesTheConflictAndReadsExcluded() {
        String sql = SqlDialect.SQLITE.upsert(new String[]{"region_name"}, "dimension", "updated_at");
        assertEquals("ON CONFLICT(region_name) DO UPDATE SET "
                + "dimension = excluded.dimension, updated_at = excluded.updated_at", sql);
    }

    @Test
    void mysqlUpsertInfersTheKeyAndReadsValues() {
        String sql = SqlDialect.MYSQL.upsert(new String[]{"region_name"}, "dimension", "updated_at");
        assertEquals("ON DUPLICATE KEY UPDATE "
                + "dimension = VALUES(dimension), updated_at = VALUES(updated_at)", sql);
    }

    @Test
    void compositeKeyUpsertNamesEveryColumnOnSqlite() {
        String sql = SqlDialect.SQLITE.upsert(
                new String[]{"region_name", "player_uuid"}, "added_at", "added_by");
        assertTrue(sql.startsWith("ON CONFLICT(region_name, player_uuid) DO UPDATE SET"), sql);
    }

    /** The pending-ledger index is partial on SQLite and cannot be on MySQL. */
    @Test
    void onlySqliteGetsAPartialPendingIndex() {
        assertEquals("CREATE INDEX idx_pending ON tx(backend_ref) WHERE backend_ref IS NULL",
                SqlDialect.SQLITE.pendingIndex("idx_pending", "tx", "backend_ref"));
        assertEquals("CREATE INDEX idx_pending ON tx(backend_ref)",
                SqlDialect.MYSQL.pendingIndex("idx_pending", "tx", "backend_ref"));
    }

    @Test
    void onlySqliteSupportsCreateIndexIfNotExists() {
        assertTrue(SqlDialect.SQLITE.supportsCreateIndexIfNotExists());
        assertFalse(SqlDialect.MYSQL.supportsCreateIndexIfNotExists());
    }
}
