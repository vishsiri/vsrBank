package dev.visherryz.plugins.vsrbank.redis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import lombok.Getter;
import redis.clients.jedis.*;
import redis.clients.jedis.params.SetParams;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Manages Redis connection for cross-server synchronization
 * Implements Distributed Locking (Redlock pattern) for transaction safety
 */
public class RedisManager {

    private final VsrBank plugin;
    private final BankConfig.RedisSettings settings;
    private final Gson gson;
    private final ExecutorService executor;

    @Getter
    private JedisPool jedisPool;

    @Getter
    private boolean connected = false;

    private RedisSubscriber subscriber;
    private Thread subscriberThread;

    // Lock prefix for Redis keys
    private static final String LOCK_PREFIX = "vsrbank:lock:";
    private static final String BALANCE_CHANNEL = "vsrbank:balance";

    public RedisManager(VsrBank plugin) {
        this.plugin = plugin;
        this.settings = plugin.getConfigManager().getConfig().getRedis();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "VsrBank-Redis-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialize Redis connection
     */
    public CompletableFuture<Void> initialize() {
        if (!settings.isEnabled()) {
            plugin.getLogger().info("Redis is disabled in config");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                JedisPoolConfig poolConfig = new JedisPoolConfig();
                poolConfig.setMaxTotal(settings.getPoolSize());
                poolConfig.setMaxIdle(settings.getPoolSize());
                poolConfig.setMinIdle(2);
                poolConfig.setTestOnBorrow(true);
                poolConfig.setTestOnReturn(true);
                poolConfig.setTestWhileIdle(true);

                if (settings.getPassword() != null && !settings.getPassword().isEmpty()) {
                    jedisPool = new JedisPool(poolConfig, settings.getHost(), settings.getPort(),
                            2000, settings.getPassword(), settings.getDatabase());
                } else {
                    jedisPool = new JedisPool(poolConfig, settings.getHost(), settings.getPort(),
                            2000, null, settings.getDatabase());
                }

                // Test connection
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.ping();
                }

                connected = true;
                plugin.getLogger().info("Redis connected: " + settings.getHost() + ":" + settings.getPort());

                // Start subscriber for real-time updates
                startSubscriber();

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to connect to Redis", e);
                connected = false;
            }
        }, executor);
    }

    /**
     * Start the pub/sub subscriber
     */
    private void startSubscriber() {
        subscriber = new RedisSubscriber(plugin, gson);
        subscriberThread = new Thread(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(subscriber, settings.getChannel(), BALANCE_CHANNEL);
            } catch (Exception e) {
                if (connected) {
                    plugin.getLogger().log(Level.WARNING, "Redis subscriber disconnected", e);
                }
            }
        }, "VsrBank-Redis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    /**
     * Shutdown Redis connection
     */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            connected = false;

            if (subscriber != null) {
                try {
                    subscriber.unsubscribe();
                } catch (Exception ignored) {}
            }

            if (subscriberThread != null) {
                subscriberThread.interrupt();
            }

            if (jedisPool != null && !jedisPool.isClosed()) {
                jedisPool.close();
            }

            executor.shutdown();
            plugin.getLogger().info("Redis connection closed");
        });
    }

    // ==================== Distributed Locking ====================

    /**
     * Acquire a distributed lock for a player's transaction
     * Prevents race conditions across multiple servers
     *
     * @param playerUuid Player UUID to lock
     * @return Lock token if acquired, null if failed
     */
    public String acquireLock(UUID playerUuid) {
        if (!connected || jedisPool == null) {
            return UUID.randomUUID().toString(); // Fallback: no Redis, no lock needed
        }

        String lockKey = LOCK_PREFIX + playerUuid.toString();
        String lockToken = UUID.randomUUID().toString();
        long lockTimeout = settings.getLockTimeout();
        long retryInterval = settings.getLockRetryInterval();
        long startTime = System.currentTimeMillis();

        try (Jedis jedis = jedisPool.getResource()) {
            while (System.currentTimeMillis() - startTime < lockTimeout) {
                // Try to acquire lock with SET NX EX
                SetParams params = SetParams.setParams().nx().px(lockTimeout);
                String result = jedis.set(lockKey, lockToken, params);

                if ("OK".equals(result)) {
                    return lockToken; // Lock acquired!
                }

                // Wait before retry
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to acquire Redis lock", e);
        }

        return null; // Failed to acquire lock
    }

    /**
     * Release a distributed lock
     *
     * @param playerUuid Player UUID
     * @param lockToken Token received when lock was acquired
     */
    public void releaseLock(UUID playerUuid, String lockToken) {
        if (!connected || jedisPool == null || lockToken == null) {
            return;
        }

        String lockKey = LOCK_PREFIX + playerUuid.toString();

        try (Jedis jedis = jedisPool.getResource()) {
            // Only release if we own the lock (compare token)
            String script = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;
            jedis.eval(script, 1, lockKey, lockToken);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to release Redis lock", e);
        }
    }

    /**
     * Execute an operation with distributed lock
     */
    public <T> CompletableFuture<T> withLock(UUID playerUuid, Supplier<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            String lockToken = acquireLock(playerUuid);
            if (lockToken == null) {
                throw new RuntimeException("Failed to acquire transaction lock");
            }

            try {
                return operation.get();
            } finally {
                releaseLock(playerUuid, lockToken);
            }
        }, executor);
    }

    // ==================== Pub/Sub for Cross-Server Sync ====================

    /**
     * Publish a balance update to all servers
     */
    public void publishBalanceUpdate(UUID playerUuid, double newBalance) {
        if (!connected || jedisPool == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                BalanceUpdateMessage message = new BalanceUpdateMessage(
                        playerUuid,
                        newBalance,
                        plugin.getConfigManager().getConfig().getServerId()
                );
                jedis.publish(BALANCE_CHANNEL, gson.toJson(message));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to publish balance update", e);
            }
        }, executor);
    }

    /**
     * Publish a custom message
     */
    public void publish(String channel, Object message) {
        if (!connected || jedisPool == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channel, gson.toJson(message));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to publish message", e);
            }
        }, executor);
    }

    // ==================== Message Classes ====================

    /**
     * Balance update message for pub/sub
     */
    public record BalanceUpdateMessage(UUID playerUuid, double newBalance, String sourceServer) {}

    /**
     * Instant type adapter for Gson
     */
    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toEpochMilli());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.ofEpochMilli(in.nextLong());
        }
    }
}