package dev.visherryz.plugins.vsrbank.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.*;
import org.redisson.codec.JsonJacksonCodec;

/**
 * Redis Data Structures Service
 * Handles collections, atomic operations, and rate limiting
 */
@RequiredArgsConstructor
public class RedisDataService {

    private final RedisManager redisManager;

    // ==================== Atomic Operations ====================

    /**
     * Get atomic double (perfect for balance!)
     */
    public RAtomicDouble getAtomicDouble(String key) {
        return redisManager.getRedisson().getAtomicDouble(key);
    }

    /**
     * Get atomic long
     */
    public RAtomicLong getAtomicLong(String key) {
        return redisManager.getRedisson().getAtomicLong(key);
    }

    // ==================== Collections ====================

    /**
     * Get distributed map (auto-serialization!)
     */
    public <K, V> RMap<K, V> getMap(String name) {
        return redisManager.getRedisson().getMap(name, new JsonJacksonCodec());
    }

    /**
     * Get distributed set
     */
    public <V> RSet<V> getSet(String name) {
        return redisManager.getRedisson().getSet(name, new JsonJacksonCodec());
    }

    /**
     * Get distributed list
     */
    public <V> RList<V> getList(String name) {
        return redisManager.getRedisson().getList(name, new JsonJacksonCodec());
    }

    /**
     * Get distributed queue
     */
    public <V> RQueue<V> getQueue(String name) {
        return redisManager.getRedisson().getQueue(name, new JsonJacksonCodec());
    }

    // ==================== Rate Limiting ====================

    /**
     * Get rate limiter
     */
    public RRateLimiter getRateLimiter(String name) {
        return redisManager.getRedisson().getRateLimiter(name);
    }

    /**
     * Create rate limiter with specific rate
     */
    public RRateLimiter createRateLimiter(String name, long rate, long rateInterval,
                                          RateIntervalUnit unit) {
        RRateLimiter limiter = getRateLimiter(name);
        limiter.trySetRate(RateType.OVERALL, rate, rateInterval, unit);
        return limiter;
    }

    // ==================== Cache Operations ====================

    /**
     * Get bucket (simple key-value)
     */
    public <V> RBucket<V> getBucket(String key) {
        return redisManager.getRedisson().getBucket(key, new JsonJacksonCodec());
    }

    /**
     * Set value with TTL
     */
    public <V> void set(String key, V value, long ttl, java.util.concurrent.TimeUnit unit) {
        RBucket<V> bucket = getBucket(key);
        bucket.set(value, ttl, unit);
    }

    /**
     * Get value
     */
    public <V> V get(String key) {
        RBucket<V> bucket = getBucket(key);
        return bucket.get();
    }

    /**
     * Delete key
     */
    public boolean delete(String key) {
        return redisManager.getRedisson().getBucket(key).delete();
    }

    // ==================== Bloom Filter ====================

    /**
     * Get bloom filter (for probabilistic set membership)
     */
    public <V> RBloomFilter<V> getBloomFilter(String name) {
        return redisManager.getRedisson().getBloomFilter(name, new JsonJacksonCodec());
    }

    // ==================== Geospatial ====================

    /**
     * Get geo data structure
     */
    public <V> RGeo<V> getGeo(String name) {
        return redisManager.getRedisson().getGeo(name, new JsonJacksonCodec());
    }
}