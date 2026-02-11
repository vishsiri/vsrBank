package dev.visherryz.plugins.vsrbank.database.provider;

import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.TransactionLog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for database providers
 * Implements Strategy Pattern to allow different database backends
 */
public interface DatabaseProvider {

    /**
     * Initialize the database connection and create tables
     */
    CompletableFuture<Void> initialize();

    /**
     * Shutdown the database connection
     */
    CompletableFuture<Void> shutdown();

    /**
     * Check if database is connected and healthy
     */
    boolean isConnected();

    // ==================== Account Operations ====================

    /**
     * Create a new bank account
     */
    CompletableFuture<BankAccount> createAccount(BankAccount account);

    /**
     * Get account by UUID
     */
    CompletableFuture<Optional<BankAccount>> getAccount(UUID uuid);

    /**
     * Get account by player name (case-insensitive)
     */
    CompletableFuture<Optional<BankAccount>> getAccountByName(String playerName);

    /**
     * Check if account exists
     */
    CompletableFuture<Boolean> accountExists(UUID uuid);

    /**
     * CRITICAL: Update account balance using ATOMIC SQL operation
     * This prevents race conditions by using relative updates
     *
     * @param uuid Player UUID
     * @param delta Amount to add (negative for subtract)
     * @return New balance after update, or -1 if failed
     */
    CompletableFuture<Double> updateBalanceAtomic(UUID uuid, double delta);

    /**
     * Set account balance directly (for admin operations)
     */
    CompletableFuture<Boolean> setBalance(UUID uuid, double newBalance);

    /**
     * Update account tier
     */
    CompletableFuture<Boolean> updateTier(UUID uuid, int newTier);

    /**
     * Update last online timestamp
     */
    CompletableFuture<Boolean> updateLastOnline(UUID uuid);

    /**
     * Update player name (when player changes name)
     */
    CompletableFuture<Boolean> updatePlayerName(UUID uuid, String newName);

    /**
     * Get current balance (read-only)
     */
    CompletableFuture<Double> getBalance(UUID uuid);

    /**
     * CRITICAL: Transfer money between accounts atomically using DB transaction
     */
    CompletableFuture<Boolean> transferAtomic(UUID fromUuid, UUID toUuid, double amount);

    // ==================== Log Operations ====================

    /**
     * Insert a transaction log entry
     */
    CompletableFuture<Long> insertLog(TransactionLog log);

    /**
     * Get transaction history for a player
     */
    CompletableFuture<List<TransactionLog>> getTransactionHistory(UUID uuid, int limit);

    /**
     * Get all logs (for admin)
     */
    CompletableFuture<List<TransactionLog>> getAllLogs(int limit, int offset);

    // ==================== Interest Operations ====================

    /**
     * Get all accounts eligible for interest
     */
    CompletableFuture<List<BankAccount>> getAccountsEligibleForInterest(double minBalance);

    /**
     * Apply interest to account and update timestamp
     */
    CompletableFuture<Boolean> applyInterest(UUID uuid, double interestAmount);

    // ==================== Utility ====================

    /**
     * Get top balances for leaderboard
     */
    CompletableFuture<List<BankAccount>> getTopBalances(int limit);

    /**
     * Get database type name
     */
    String getDatabaseType();

    // ==================== Cache (Cross-Server Support) ====================

    /**
     * Invalidate any local cache for a player's account data.
     * Called by RedisPubSubService when another server updates a player's balance.
     *
     * Default implementation is no-op (suitable for providers that read directly from DB
     * without caching, like the current AbstractSQLProvider).
     *
     * Override this if your provider implements a local cache layer.
     *
     * @param uuid The player UUID whose cached data should be invalidated
     */
    default void invalidateCache(UUID uuid) {
        // No-op — AbstractSQLProvider reads directly from DB every time,
        // so there's nothing to invalidate.
        // Override this if you add a caching layer in the future.
    }
}