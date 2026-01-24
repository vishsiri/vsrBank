package dev.visherryz.plugins.vsrbank.database.provider;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.TransactionLog;
import lombok.Getter;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Abstract base class for SQL database providers
 * Contains shared HikariCP logic and ATOMIC SQL operations
 */
public abstract class AbstractSQLProvider implements DatabaseProvider {

    protected final VsrBank plugin;
    protected final BankConfig.DatabaseSettings settings;

    @Getter
    protected HikariDataSource dataSource;

    protected final ExecutorService executor;
    protected final String tablePrefix;

    // Table names
    protected final String accountsTable;
    protected final String logsTable;

    public AbstractSQLProvider(VsrBank plugin, BankConfig.DatabaseSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, settings.getMaxPoolSize() / 2),
                r -> {
                    Thread t = new Thread(r, "VsrBank-DB-Worker");
                    t.setDaemon(true);
                    return t;
                }
        );
        this.tablePrefix = settings.getTablePrefix();
        this.accountsTable = tablePrefix + "accounts";
        this.logsTable = tablePrefix + "logs";
    }

    /**
     * Get HikariCP configuration - implemented by subclasses
     */
    protected abstract HikariConfig createHikariConfig();

    /**
     * Get SQL for creating accounts table
     */
    protected abstract String getCreateAccountsTableSQL();

    /**
     * Get SQL for creating logs table
     */
    protected abstract String getCreateLogsTableSQL();

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                HikariConfig config = createHikariConfig();
                this.dataSource = new HikariDataSource(config);

                // Create tables
                createTables();

                plugin.getLogger().info("Database connected: " + getDatabaseType());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
                throw new RuntimeException("Database initialization failed", e);
            }
        }, executor);
    }

    protected void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(getCreateAccountsTableSQL());
            stmt.execute(getCreateLogsTableSQL());

            // Create indexes
            createIndexes(stmt);
        }
    }

    protected void createIndexes(Statement stmt) {
        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_acc_uuid ON " + accountsTable + " (uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_acc_name ON " + accountsTable + " (player_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_player ON " + logsTable + " (player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_time ON " + logsTable + " (timestamp DESC)");
        } catch (SQLException e) {
            plugin.getLogger().fine("Index creation note: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Close HikariCP datasource
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                }

                // Shutdown executor with timeout
                executor.shutdown();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Database executor didn't terminate in 30s, forcing shutdown");

                    // Force shutdown and get pending tasks
                    List<Runnable> pending = executor.shutdownNow();
                    if (!pending.isEmpty()) {
                        plugin.getLogger().warning("Forced shutdown, " + pending.size() + " tasks cancelled");
                    }

                    // Final wait
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        plugin.getLogger().severe("Database executor didn't terminate after force shutdown!");
                    }
                }

                plugin.getLogger().info("Database connection closed successfully");

            } catch (InterruptedException e) {
                plugin.getLogger().warning("Interrupted during database shutdown");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    // ==================== Account Operations ====================

    @Override
    public CompletableFuture<BankAccount> createAccount(BankAccount account) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO %s (uuid, player_name, balance, tier, total_interest_earned,
                    last_interest_time, last_online, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(accountsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                Instant now = Instant.now();
                stmt.setString(1, account.getUuid().toString());
                stmt.setString(2, account.getPlayerName());
                stmt.setDouble(3, account.getBalance());
                stmt.setInt(4, account.getTier());
                stmt.setDouble(5, account.getTotalInterestEarned());
                stmt.setTimestamp(6, Timestamp.from(now));
                stmt.setTimestamp(7, Timestamp.from(now));
                stmt.setTimestamp(8, Timestamp.from(now));
                stmt.setTimestamp(9, Timestamp.from(now));

                stmt.executeUpdate();

                account.setLastInterestTime(now);
                account.setLastOnline(now);
                account.setCreatedAt(now);
                account.setUpdatedAt(now);

                return account;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create account", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<BankAccount>> getAccount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + accountsTable + " WHERE uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapResultSetToAccount(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get account", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<BankAccount>> getAccountByName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + accountsTable + " WHERE LOWER(player_name) = LOWER(?)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerName);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapResultSetToAccount(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get account by name", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> accountExists(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + accountsTable + " WHERE uuid = ? LIMIT 1";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                return stmt.executeQuery().next();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check account exists", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * CRITICAL: Atomic balance update using relative SQL
     * Prevents race conditions - NO "get -> calculate -> set" pattern!
     */
    @Override
    public CompletableFuture<Double> updateBalanceAtomic(UUID uuid, double delta) {
        return CompletableFuture.supplyAsync(() -> {
            // ATOMIC UPDATE - Key to preventing exploits!
            // Uses: balance = balance + ? instead of SET balance = <calculated_value>
            String updateSql = """
                UPDATE %s\s
                SET balance = balance + ?, updated_at = ?
                WHERE uuid = ?
               \s""".formatted(accountsTable);

            String selectSql = "SELECT balance FROM " + accountsTable + " WHERE uuid = ?";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Perform atomic update
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setDouble(1, delta);
                        updateStmt.setTimestamp(2, Timestamp.from(Instant.now()));
                        updateStmt.setString(3, uuid.toString());

                        int updated = updateStmt.executeUpdate();
                        if (updated == 0) {
                            conn.rollback();
                            return -1.0;
                        }
                    }

                    // Get new balance
                    double newBalance;
                    try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                        selectStmt.setString(1, uuid.toString());
                        ResultSet rs = selectStmt.executeQuery();

                        if (rs.next()) {
                            newBalance = rs.getDouble("balance");
                        } else {
                            conn.rollback();
                            return -1.0;
                        }
                    }

                    conn.commit();
                    return newBalance;

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update balance atomically", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> setBalance(UUID uuid, double newBalance) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE %s SET balance = ?, updated_at = ? WHERE uuid = ?
                """.formatted(accountsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setDouble(1, newBalance);
                stmt.setTimestamp(2, Timestamp.from(Instant.now()));
                stmt.setString(3, uuid.toString());

                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to set balance", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updateTier(UUID uuid, int newTier) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE %s SET tier = ?, updated_at = ? WHERE uuid = ?
                """.formatted(accountsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, newTier);
                stmt.setTimestamp(2, Timestamp.from(Instant.now()));
                stmt.setString(3, uuid.toString());

                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update tier", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updateLastOnline(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE %s SET last_online = ?, updated_at = ? WHERE uuid = ?
                """.formatted(accountsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                Timestamp now = Timestamp.from(Instant.now());
                stmt.setTimestamp(1, now);
                stmt.setTimestamp(2, now);
                stmt.setString(3, uuid.toString());

                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update last online", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updatePlayerName(UUID uuid, String newName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE %s SET player_name = ?, updated_at = ? WHERE uuid = ?
                """.formatted(accountsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, newName);
                stmt.setTimestamp(2, Timestamp.from(Instant.now()));
                stmt.setString(3, uuid.toString());

                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update player name", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Double> getBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT balance FROM " + accountsTable + " WHERE uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getDouble("balance");
                }
                return -1.0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get balance", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * CRITICAL: Atomic transfer using database transaction
     * Ensures ACID compliance - both operations succeed or both fail
     */
    @Override
    public CompletableFuture<Boolean> transferAtomic(UUID fromUuid, UUID toUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            String deductSql = """
                UPDATE %s SET balance = balance - ?, updated_at = ?
                WHERE uuid = ? AND balance >= ?
                """.formatted(accountsTable);

            String addSql = """
                UPDATE %s SET balance = balance + ?, updated_at = ?
                WHERE uuid = ?
                """.formatted(accountsTable);

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    Timestamp now = Timestamp.from(Instant.now());

                    // Step 1: Deduct from sender (with balance check in SQL)
                    try (PreparedStatement deductStmt = conn.prepareStatement(deductSql)) {
                        deductStmt.setDouble(1, amount);
                        deductStmt.setTimestamp(2, now);
                        deductStmt.setString(3, fromUuid.toString());
                        deductStmt.setDouble(4, amount); // Balance check

                        int updated = deductStmt.executeUpdate();
                        if (updated == 0) {
                            // Either account doesn't exist or insufficient balance
                            conn.rollback();
                            return false;
                        }
                    }

                    // Step 2: Add to receiver
                    try (PreparedStatement addStmt = conn.prepareStatement(addSql)) {
                        addStmt.setDouble(1, amount);
                        addStmt.setTimestamp(2, now);
                        addStmt.setString(3, toUuid.toString());

                        int updated = addStmt.executeUpdate();
                        if (updated == 0) {
                            // Receiver doesn't exist - ROLLBACK!
                            conn.rollback();
                            return false;
                        }
                    }

                    // Both succeeded - commit
                    conn.commit();
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to transfer atomically", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    // ==================== Log Operations ====================

    @Override
    public CompletableFuture<Long> insertLog(TransactionLog log) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO %s (player_uuid, player_name, type, amount, balance_before,\s
                    balance_after, target_uuid, target_name, server_id, reason,\s
                    admin_action, admin_name, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               \s""".formatted(logsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, log.getPlayerUuid().toString());
                stmt.setString(2, log.getPlayerName());
                stmt.setString(3, log.getType().name());
                stmt.setDouble(4, log.getAmount());
                stmt.setDouble(5, log.getBalanceBefore());
                stmt.setDouble(6, log.getBalanceAfter());
                stmt.setString(7, log.getTargetUuid() != null ? log.getTargetUuid().toString() : null);
                stmt.setString(8, log.getTargetName());
                stmt.setString(9, log.getServerId());
                stmt.setString(10, log.getReason());
                stmt.setBoolean(11, log.isAdminAction());
                stmt.setString(12, log.getAdminName());
                stmt.setTimestamp(13, Timestamp.from(log.getTimestamp() != null ? log.getTimestamp() : Instant.now()));

                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return -1L;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to insert log", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TransactionLog>> getTransactionHistory(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM %s\s
                WHERE player_uuid = ?\s
                ORDER BY timestamp DESC\s
                LIMIT ?
               \s""".formatted(logsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                stmt.setInt(2, limit);

                ResultSet rs = stmt.executeQuery();
                List<TransactionLog> logs = new ArrayList<>();

                while (rs.next()) {
                    logs.add(mapResultSetToLog(rs));
                }

                return logs;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get transaction history", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TransactionLog>> getAllLogs(int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM %s\s
                ORDER BY timestamp DESC\s
                LIMIT ? OFFSET ?
               \s""".formatted(logsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                ResultSet rs = stmt.executeQuery();
                List<TransactionLog> logs = new ArrayList<>();

                while (rs.next()) {
                    logs.add(mapResultSetToLog(rs));
                }

                return logs;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get all logs", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    // ==================== Interest Operations ====================

    @Override
    public CompletableFuture<List<BankAccount>> getAccountsEligibleForInterest(double minBalance) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM %s\s
                WHERE balance >= ?
               \s""".formatted(accountsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setDouble(1, minBalance);

                ResultSet rs = stmt.executeQuery();
                List<BankAccount> accounts = new ArrayList<>();

                while (rs.next()) {
                    accounts.add(mapResultSetToAccount(rs));
                }

                return accounts;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get accounts for interest", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> applyInterest(UUID uuid, double interestAmount) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE %s\s
                SET balance = balance + ?,
                    total_interest_earned = total_interest_earned + ?,
                    last_interest_time = ?,
                    updated_at = ?
                WHERE uuid = ?
               \s""".formatted(accountsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                Timestamp now = Timestamp.from(Instant.now());
                stmt.setDouble(1, interestAmount);
                stmt.setDouble(2, interestAmount);
                stmt.setTimestamp(3, now);
                stmt.setTimestamp(4, now);
                stmt.setString(5, uuid.toString());

                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to apply interest", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    // ==================== Utility ====================

    @Override
    public CompletableFuture<List<BankAccount>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM %s\s
                ORDER BY balance DESC\s
                LIMIT ?
               \s""".formatted(accountsTable);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);

                ResultSet rs = stmt.executeQuery();
                List<BankAccount> accounts = new ArrayList<>();

                while (rs.next()) {
                    accounts.add(mapResultSetToAccount(rs));
                }

                return accounts;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get top balances", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    // ==================== Mapping Helpers ====================

    protected BankAccount mapResultSetToAccount(ResultSet rs) throws SQLException {
        return BankAccount.builder()
                .uuid(UUID.fromString(rs.getString("uuid")))
                .playerName(rs.getString("player_name"))
                .balance(rs.getDouble("balance"))
                .tier(rs.getInt("tier"))
                .totalInterestEarned(rs.getDouble("total_interest_earned"))
                .lastInterestTime(rs.getTimestamp("last_interest_time").toInstant())
                .lastOnline(rs.getTimestamp("last_online").toInstant())
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }

    protected TransactionLog mapResultSetToLog(ResultSet rs) throws SQLException {
        String targetUuidStr = rs.getString("target_uuid");
        return TransactionLog.builder()
                .id(rs.getLong("id"))
                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                .playerName(rs.getString("player_name"))
                .type(TransactionLog.TransactionType.valueOf(rs.getString("type")))
                .amount(rs.getDouble("amount"))
                .balanceBefore(rs.getDouble("balance_before"))
                .balanceAfter(rs.getDouble("balance_after"))
                .targetUuid(targetUuidStr != null ? UUID.fromString(targetUuidStr) : null)
                .targetName(rs.getString("target_name"))
                .serverId(rs.getString("server_id"))
                .reason(rs.getString("reason"))
                .adminAction(rs.getBoolean("admin_action"))
                .adminName(rs.getString("admin_name"))
                .timestamp(rs.getTimestamp("timestamp").toInstant())
                .build();
    }
}
