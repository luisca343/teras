package es.boffmedia.teras.plot;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.plot.model.PlotOwnership;
import es.boffmedia.teras.plot.model.PlotTransaction;
import es.boffmedia.teras.plot.sql.SqlDialect;
import es.boffmedia.teras.plot.sql.TerasDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plot ownership and the money ledger: the query surface over {@link TerasDatabase}'s {@code plot},
 * {@code plot_member} and {@code plot_transaction} tables. A purchase is several writes that must
 * land together or not at all, which is what this being a database rather than a json store buys.
 *
 * <p>Connections, dialect and schema all belong to {@link TerasDatabase}; this class only issues
 * statements. Reads on the block-event hot path never come here — they go to {@link PlotStore}'s
 * in-memory snapshot, which is what lets the store be remote at all.</p>
 */
public final class PlotDatabase implements AutoCloseable {

    private final TerasDatabase database;

    public PlotDatabase(TerasDatabase database) {
        this.database = database;
    }

    private Connection connect() throws SQLException {
        return database.connect();
    }

    private SqlDialect dialect() {
        return database.dialect();
    }

    private String plotTable() {
        return database.table("plot");
    }

    private String memberTable() {
        return database.table("plot_member");
    }

    private String transactionTable() {
        return database.table("plot_transaction");
    }

    @Override
    public void close() {
        // The connection lifecycle belongs to TerasDatabase; nothing is held open here.
    }

    // ---- Reads ----

    /** Every plot with its members, for {@link PlotStore} to publish as the read-side snapshot. */
    public synchronized Map<String, PlotOwnership> loadAll() throws SQLException {
        try (Connection connection = connect()) {
            Map<String, Set<UUID>> members = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT region_name, player_uuid FROM " + memberTable())) {
                while (rows.next()) {
                    UUID player = parseUuid(rows.getString(2));
                    if (player == null) continue;
                    members.computeIfAbsent(rows.getString(1), k -> new HashSet<>()).add(player);
                }
            }
            Map<String, PlotOwnership> plots = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT region_name, dimension, owner_uuid, "
                         + "owned_since, expires_at FROM " + plotTable())) {
                while (rows.next()) {
                    String region = rows.getString(1);
                    String dimension = rows.getString(2);
                    UUID owner = parseUuid(rows.getString(3));
                    long ownedSince = rows.getLong(4);
                    // wasNull() reports on the most recent read, so each nullable column must be
                    // checked right after its own get — not folded into the constructor call.
                    long expiresValue = rows.getLong(5);
                    Long expiresAt = rows.wasNull() ? null : expiresValue;
                    plots.put(region, new PlotOwnership(region, dimension, owner, ownedSince,
                            expiresAt, members.getOrDefault(region, Set.of())));
                }
            }
            return plots;
        }
    }

    /**
     * The newest {@code updated_at} across all plots, or 0 when there are none. Cheap enough to
     * poll, and the hook for noticing that the SmartRotom backend changed ownership in the shared
     * database — nothing calls it yet (PLOTS.md §6/§7).
     */
    public synchronized long latestRevision() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT MAX(updated_at) FROM " + plotTable())) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }

    /** The ledger for one plot, newest first. */
    public synchronized List<PlotTransaction> transactionsFor(String regionName) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, region_name, kind, buyer_uuid, seller_uuid, price, backend_ref, "
                             + "created_at FROM " + transactionTable()
                             + " WHERE region_name = ? ORDER BY id DESC")) {
            statement.setString(1, regionName);
            return readTransactions(statement);
        }
    }

    /** Rows the backend has not acknowledged — what a reconciliation sweep retries. */
    public synchronized List<PlotTransaction> pendingTransactions() throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, region_name, kind, buyer_uuid, seller_uuid, price, backend_ref, "
                             + "created_at FROM " + transactionTable()
                             + " WHERE backend_ref IS NULL ORDER BY id")) {
            return readTransactions(statement);
        }
    }

    // ---- Writes ----

    /**
     * Lists {@code region} for sale, creating the row that makes it a plot at all. Keeps any
     * existing owner and members — an admin re-pricing a sold plot is not a repossession.
     */
    public synchronized void register(String regionName, String dimension) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + plotTable() + " (region_name, dimension, updated_at) "
                             + "VALUES (?, ?, ?) "
                             + dialect().upsert(new String[]{"region_name"}, "dimension", "updated_at"))) {
            statement.setString(1, regionName);
            statement.setString(2, dimension);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * Sets (or with a null {@code owner}, clears) the plot's owner and records why, as one
     * transaction — the ownership row and its ledger entry must never disagree.
     *
     * <p>Clearing an owner drops the member list with it: members were the previous owner's
     * guests, and carrying them into the next sale would silently hand build rights to strangers.</p>
     */
    public synchronized void setOwner(String regionName, String dimension, UUID owner, Long expiresAt,
                                      PlotTransaction.Kind kind, UUID seller, long price, long now)
            throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO " + plotTable() + " (region_name, dimension, owner_uuid, "
                                + "owned_since, expires_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) "
                                + dialect().upsert(new String[]{"region_name"},
                                        "dimension", "owner_uuid", "owned_since", "expires_at",
                                        "updated_at"))) {
                    statement.setString(1, regionName);
                    statement.setString(2, dimension);
                    setUuid(statement, 3, owner);
                    if (owner == null) {
                        statement.setNull(4, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(4, now);
                    }
                    if (expiresAt == null) {
                        statement.setNull(5, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(5, expiresAt);
                    }
                    statement.setLong(6, now);
                    statement.executeUpdate();
                }
                if (owner == null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM " + memberTable() + " WHERE region_name = ?")) {
                        statement.setString(1, regionName);
                        statement.executeUpdate();
                    }
                }
                insertTransaction(connection, regionName, kind, owner, seller, price, now);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Adds a member, or refreshes who invited them. No-op semantics on re-add. */
    public synchronized void addMember(String regionName, UUID player, String addedBy, long now)
            throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + memberTable()
                             + " (region_name, player_uuid, added_at, added_by) VALUES (?, ?, ?, ?) "
                             + dialect().upsert(new String[]{"region_name", "player_uuid"},
                                     "added_at", "added_by"))) {
            statement.setString(1, regionName);
            setUuid(statement, 2, player);
            statement.setLong(3, now);
            statement.setString(4, addedBy);
            statement.executeUpdate();
        }
    }

    /** Removes a member, returning whether they were one. */
    public synchronized boolean removeMember(String regionName, UUID player) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + memberTable()
                             + " WHERE region_name = ? AND player_uuid = ?")) {
            statement.setString(1, regionName);
            setUuid(statement, 2, player);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Stops {@code region} being a plot at all, dropping its ownership and members. The ledger is
     * deliberately left intact — it is append-only, and the history of a delisted plot is exactly
     * what a reconciliation against starbank needs.
     */
    public synchronized boolean unregister(String regionName) throws SQLException {
        try (Connection connection = connect()) {
            // The CASCADE covers this on both engines, but only while foreign keys are enforced;
            // an explicit delete means a member row can never outlive its plot and resurrect
            // build rights on the next listing.
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + memberTable() + " WHERE region_name = ?")) {
                statement.setString(1, regionName);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + plotTable() + " WHERE region_name = ?")) {
                statement.setString(1, regionName);
                return statement.executeUpdate() > 0;
            }
        }
    }

    /** Stamps a ledger row as confirmed by the backend. The only UPDATE this table ever takes. */
    public synchronized void confirmTransaction(long id, String backendRef) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + transactionTable() + " SET backend_ref = ? WHERE id = ?")) {
            statement.setString(1, backendRef);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void insertTransaction(Connection connection, String regionName, PlotTransaction.Kind kind,
                                   UUID buyer, UUID seller, long price, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + transactionTable() + " (region_name, kind, buyer_uuid, seller_uuid, "
                        + "price, backend_ref, created_at) VALUES (?, ?, ?, ?, ?, NULL, ?)")) {
            statement.setString(1, regionName);
            statement.setString(2, kind.key());
            setUuid(statement, 3, buyer);
            setUuid(statement, 4, seller);
            statement.setLong(5, price);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
    }

    private static List<PlotTransaction> readTransactions(PreparedStatement statement)
            throws SQLException {
        List<PlotTransaction> transactions = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                transactions.add(new PlotTransaction(
                        rows.getLong(1),
                        rows.getString(2),
                        PlotTransaction.Kind.fromKey(rows.getString(3)),
                        parseUuid(rows.getString(4)),
                        parseUuid(rows.getString(5)),
                        rows.getLong(6),
                        rows.getString(7),
                        rows.getLong(8)));
            }
        }
        return transactions;
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }

    /** Lenient on purpose: one hand-mangled uuid should not take the whole catalog down. */
    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            Teras.LOGGER.warn("PlotDatabase: ignoring malformed uuid '{}'", value);
            return null;
        }
    }
}
