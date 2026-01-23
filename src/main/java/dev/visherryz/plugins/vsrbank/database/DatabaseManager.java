package dev.visherryz.plugins.vsrbank.database;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.database.provider.DatabaseProvider;
import dev.visherryz.plugins.vsrbank.database.provider.MySQLProvider;
import dev.visherryz.plugins.vsrbank.database.provider.SQLiteProvider;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

/**
 * Factory and manager for database providers
 */
public class DatabaseManager {

    private final VsrBank plugin;

    @Getter
    private DatabaseProvider provider;

    public DatabaseManager(VsrBank plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize database based on config
     */
    public CompletableFuture<Void> initialize() {
        BankConfig.DatabaseSettings settings = plugin.getConfigManager().getConfig().getDatabase();
        String type = settings.getType().toUpperCase();

        // Create appropriate provider based on config
        provider = switch (type) {
            case "MYSQL" -> new MySQLProvider(plugin, settings, false);
            case "MARIADB" -> new MySQLProvider(plugin, settings, true);
            case "SQLITE" -> new SQLiteProvider(plugin, settings);
            default -> {
                plugin.getLogger().warning("Unknown database type: " + type + ", defaulting to SQLite");
                yield new SQLiteProvider(plugin, settings);
            }
        };

        return provider.initialize();
    }

    /**
     * Shutdown database connection
     */
    public CompletableFuture<Void> shutdown() {
        if (provider != null) {
            return provider.shutdown();
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Check if database is connected
     */
    public boolean isConnected() {
        return provider != null && provider.isConnected();
    }
}