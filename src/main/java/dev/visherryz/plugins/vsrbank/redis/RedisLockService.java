package dev.visherryz.plugins.vsrbank.redis;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Redis Distributed Lock Service
 * Handles all lock operations
 */
@RequiredArgsConstructor
public class RedisLockService {

    private final VsrBank plugin;
    private final RedisManager redisManager;

    private static final String LOCK_PREFIX = "vsrbank:lock:";

    /**
     * Get a distributed lock
     */
    public RLock getLock(UUID playerUuid) {
        if (!redisManager.isConnected()) {
            throw new IllegalStateException("Redis not connected");
        }
        return redisManager.getRedisson().getLock(LOCK_PREFIX + playerUuid.toString());
    }

    /**
     * Get a fair lock (FIFO ordering)
     */
    public RLock getFairLock(UUID playerUuid) {
        if (!redisManager.isConnected()) {
            throw new IllegalStateException("Redis not connected");
        }
        return redisManager.getRedisson().getFairLock(LOCK_PREFIX + playerUuid.toString());
    }

    /**
     * Get a read-write lock
     */
    public RReadWriteLock getReadWriteLock(UUID playerUuid) {
        if (!redisManager.isConnected()) {
            throw new IllegalStateException("Redis not connected");
        }
        return redisManager.getRedisson().getReadWriteLock(LOCK_PREFIX + playerUuid.toString());
    }

    /**
     * Execute operation with automatic lock management
     */
    public <T> CompletableFuture<T> withLock(UUID playerUuid, Supplier<T> operation) {
        BankConfig.RedisSettings settings = plugin.getConfigManager().getConfig().getRedis();

        return CompletableFuture.supplyAsync(() -> {
            RLock lock = getLock(playerUuid);
            try {
                if (lock.tryLock(settings.getLockTimeout(), TimeUnit.MILLISECONDS)) {
                    try {
                        return operation.get();
                    } finally {
                        unlockSafely(lock, playerUuid);
                    }
                } else {
                    throw new RuntimeException("Failed to acquire lock for " + playerUuid);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Lock acquisition interrupted", e);
            }
        });
    }

    /**
     * Execute operation with automatic lock and timeout (async)
     */
    public <T> CompletableFuture<T> withLockAsync(UUID playerUuid, long timeout, TimeUnit unit,
                                                  Supplier<CompletableFuture<T>> operation) {
        RLock lock = getLock(playerUuid);

        return lock.tryLockAsync(timeout, unit)
                .toCompletableFuture()
                .thenCompose(acquired -> {
                    if (!acquired) {
                        CompletableFuture<T> failed = new CompletableFuture<>();
                        failed.completeExceptionally(
                                new RuntimeException("Failed to acquire lock for " + playerUuid)
                        );
                        return failed;
                    }

                    return operation.get()
                            .whenComplete((result, error) -> {
                                unlockSafely(lock, playerUuid);
                            });
                });
    }

    /**
     * Safely unlock with logging
     */
    private void unlockSafely(RLock lock, UUID playerUuid) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                plugin.getLogger().fine("Lock released for player " + playerUuid);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to unlock for player " + playerUuid, e);
        }
    }
}