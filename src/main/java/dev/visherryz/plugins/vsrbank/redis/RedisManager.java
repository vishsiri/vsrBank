package dev.visherryz.plugins.vsrbank.redis;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import lombok.Getter;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Core Redis Connection Manager
 * Handles connection lifecycle only
 */
public class RedisManager {

    private final VsrBank plugin;
    private final BankConfig.RedisSettings settings;

    @Getter
    private RedissonClient redisson;

    @Getter
    private boolean connected = false;

    public RedisManager(VsrBank plugin) {
        this.plugin = plugin;
        this.settings = plugin.getConfigManager().getConfig().getRedis();
    }

    /**
     * Initialize Redisson connection
     */
    public CompletableFuture<Void> initialize() {
        if (!settings.isEnabled()) {
            plugin.getLogger().info("Redis is disabled in config");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                Config config = createRedissonConfig();
                redisson = Redisson.create(config);

                // Test connection
                redisson.getKeys().count();

                connected = true;
                plugin.getLogger().info("✅ Redisson connected successfully");

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to connect to Redis", e);
                connected = false;
            }
        });
    }

    /**
     * Create Redisson configuration
     */
    private Config createRedissonConfig() {
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec());

        if (settings.isClusterMode()) {
            configureCluster(config);
        } else {
            configureStandalone(config);
        }

        // Thread pool settings
        config.setThreads(16);
        config.setNettyThreads(32);

        return config;
    }

    /**
     * Configure cluster mode
     */
    private void configureCluster(Config config) {
        List<String> nodeAddresses = settings.getClusterNodes().stream()
                .map(node -> "redis://" + node)
                .toList();

        if (nodeAddresses.isEmpty()) {
            throw new IllegalStateException("Cluster mode enabled but no nodes configured!");
        }

        ClusterServersConfig clusterConfig = config.useClusterServers()
                .addNodeAddress(nodeAddresses.toArray(new String[0]))
                .setScanInterval(2000)
                .setConnectTimeout(settings.getTimeout())
                .setTimeout(settings.getTimeout())
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setMasterConnectionPoolSize(settings.getConnectionPoolSize())
                .setSlaveConnectionPoolSize(settings.getConnectionPoolSize())
                .setMasterConnectionMinimumIdleSize(settings.getConnectionMinimumIdleSize())
                .setSlaveConnectionMinimumIdleSize(settings.getConnectionMinimumIdleSize());

        if (settings.getPassword() != null && !settings.getPassword().isEmpty()) {
            clusterConfig.setPassword(settings.getPassword());
        }

        plugin.getLogger().info("Redis Cluster configured with " + nodeAddresses.size() + " nodes");
    }

    /**
     * Configure standalone mode
     */
    private void configureStandalone(Config config) {
        String address = "redis://" + settings.getHost() + ":" + settings.getPort();

        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(settings.getDatabase())
                .setConnectTimeout(settings.getTimeout())
                .setTimeout(settings.getTimeout())
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setConnectionPoolSize(settings.getConnectionPoolSize())
                .setConnectionMinimumIdleSize(settings.getConnectionMinimumIdleSize())
                .setPingConnectionInterval(30000)
                .setKeepAlive(true);

        if (settings.getPassword() != null && !settings.getPassword().isEmpty()) {
            serverConfig.setPassword(settings.getPassword());
        }

        plugin.getLogger().info("Redis Standalone configured: " + address);
    }

    /**
     * Shutdown Redisson
     */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            try {
                connected = false;

                if (redisson != null && !redisson.isShutdown()) {
                    redisson.shutdown(5, 15, TimeUnit.SECONDS);
                }

                plugin.getLogger().info("✅ Redisson connection closed");

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error during Redisson shutdown", e);
            }
        });
    }

    /**
     * Health check
     */
    public boolean healthCheck() {
        if (!connected || redisson == null || redisson.isShutdown()) {
            return false;
        }

        try {
            redisson.getKeys().count();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis health check failed", e);
            return false;
        }
    }

    /**
     * Get connection stats
     */
    public String getConnectionStats() {
        if (!connected || redisson == null) {
            return "Redis: Disconnected";
        }

        StringBuilder stats = new StringBuilder();
        stats.append("Redisson Status:\n");
        stats.append("  Connected: ").append(!redisson.isShutdown()).append("\n");
        stats.append("  Mode: ").append(settings.isClusterMode() ? "Cluster" : "Standalone").append("\n");

        if (settings.isClusterMode()) {
            stats.append("  Cluster Nodes: ").append(settings.getClusterNodes().size()).append("\n");
        }

        return stats.toString();
    }


}