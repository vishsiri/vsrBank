package dev.visherryz.plugins.vsrbank.database.provider;

import com.zaxxer.hikari.HikariConfig;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;

import java.io.File;

/**
 * SQLite database provider
 */
public class SQLiteProvider extends AbstractSQLProvider {

    public SQLiteProvider(VsrBank plugin, BankConfig.DatabaseSettings settings) {
        super(plugin, settings);
    }

    @Override
    protected HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();

        // SQLite file path
        File databaseFile = new File(plugin.getDataFolder(), "bank.db");
        String jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite specific settings - single writer
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(settings.getConnectionTimeout());

        // Pool name
        config.setPoolName("VsrBank-SQLite-Pool");

        // SQLite optimizations
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "10000");
        config.addDataSourceProperty("foreign_keys", "ON");

        return config;
    }

    @Override
    protected String getCreateAccountsTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                uuid TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                balance REAL NOT NULL DEFAULT 0,
                tier INTEGER NOT NULL DEFAULT 1,
                total_interest_earned REAL NOT NULL DEFAULT 0,
                last_interest_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                last_online TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.formatted(accountsTable);
    }

    @Override
    protected String getCreateLogsTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                balance_before REAL NOT NULL,
                balance_after REAL NOT NULL,
                target_uuid TEXT,
                target_name TEXT,
                server_id TEXT,
                reason TEXT,
                admin_action INTEGER NOT NULL DEFAULT 0,
                admin_name TEXT,
                timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.formatted(logsTable);
    }

    @Override
    public String getDatabaseType() {
        return "SQLite";
    }
}