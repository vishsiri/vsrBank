package dev.visherryz.plugins.vsrbank.database.provider;

import com.zaxxer.hikari.HikariConfig;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;

public class MySQLProvider extends AbstractSQLProvider {

    public MySQLProvider(VsrBank plugin, BankConfig.DatabaseSettings settings) {
        super(plugin, settings);
    }

    @Override
    protected HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();

        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&autoReconnect=true",
                settings.getHost(),
                settings.getPort(),
                settings.getDatabase()
        );

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(settings.getUsername());
        config.setPassword(settings.getPassword());

        config.setMaximumPoolSize(settings.getMaxPoolSize());
        config.setMinimumIdle(settings.getMinIdle());
        config.setConnectionTimeout(settings.getConnectionTimeout());
        config.setIdleTimeout(settings.getIdleTimeout());
        config.setMaxLifetime(settings.getMaxLifetime());
        config.setPoolName("VsrBank-MySQL-Pool");

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        return config;
    }

    @Override
    protected String getCreateAccountsTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                uuid VARCHAR(36) PRIMARY KEY,
                player_name VARCHAR(16) NOT NULL,
                balance DOUBLE NOT NULL DEFAULT 0,
                tier INT NOT NULL DEFAULT 1,
                total_interest_earned DOUBLE NOT NULL DEFAULT 0,
                last_interest_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                last_online TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_player_name (player_name),
                INDEX idx_balance (balance)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """.formatted(accountsTable);
    }

    @Override
    protected String getCreateLogsTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16) NOT NULL,
                type VARCHAR(32) NOT NULL,
                amount DOUBLE NOT NULL,
                balance_before DOUBLE NOT NULL,
                balance_after DOUBLE NOT NULL,
                target_uuid VARCHAR(36),
                target_name VARCHAR(16),
                server_id VARCHAR(64),
                reason TEXT,
                admin_action BOOLEAN NOT NULL DEFAULT FALSE,
                admin_name VARCHAR(16),
                timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_type (type),
                INDEX idx_timestamp (timestamp),
                INDEX idx_admin_action (admin_action)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """.formatted(logsTable);
    }

    @Override
    public String getDatabaseType() {
        return "MySQL";
    }
}