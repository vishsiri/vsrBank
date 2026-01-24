package dev.visherryz.plugins.vsrbank.api;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.BankResult;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for external plugins to interact with VsrBank
 *
 * Usage:
 * <pre>
 * BankAPI api = VsrBank.getAPI();
 * api.getBalance(playerUuid).thenAccept(balance -> {
 *     // Do something with balance
 * });
 * </pre>
 *
 * All methods are ASYNC and return CompletableFuture
 */
public class BankAPI {

    private final VsrBank plugin;

    public BankAPI(VsrBank plugin) {
        this.plugin = plugin;
    }

    /**
     * Get player's bank balance
     * @param uuid Player UUID
     * @return CompletableFuture with balance, -1 if account not found
     */
    public CompletableFuture<Double> getBalance(UUID uuid) {
        return plugin.getDatabaseManager().getProvider().getBalance(uuid);
    }

    /**
     * Check if player has a bank account
     * @param uuid Player UUID
     * @return CompletableFuture<Boolean>
     */
    public CompletableFuture<Boolean> hasAccount(UUID uuid) {
        return plugin.getDatabaseManager().getProvider().accountExists(uuid);
    }

    /**
     * Get player's bank account
     * @param uuid Player UUID
     * @return CompletableFuture with Optional<BankAccount>
     */
    public CompletableFuture<Optional<BankAccount>> getAccount(UUID uuid) {
        return plugin.getDatabaseManager().getProvider().getAccount(uuid);
    }

    /**
     * Deposit money to player's bank (does NOT deduct from Vault)
     * Use this for custom integrations where you handle the source yourself
     *
     * @param uuid Player UUID
     * @param amount Amount to deposit
     * @param reason Reason for transaction (for logging)
     * @return CompletableFuture<BankResult>
     */
    public CompletableFuture<BankResult> deposit(UUID uuid, double amount, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(BankResult.INVALID_AMOUNT);
        }

        return plugin.getDatabaseManager().getProvider().updateBalanceAtomic(uuid, amount)
                .thenApply(newBalance -> {
                    if (newBalance < 0) {
                        return BankResult.DATABASE_ERROR;
                    }

                    // Publish to Redis for cross-server sync
                    plugin.getRedisPubSubService().publishBalanceUpdate(uuid, newBalance);

                    return BankResult.SUCCESS;
                });
    }

    /**
     * Withdraw money from player's bank (does NOT deposit to Vault)
     * Use this for custom integrations where you handle the destination yourself
     *
     * @param uuid Player UUID
     * @param amount Amount to withdraw
     * @param reason Reason for transaction (for logging)
     * @return CompletableFuture<BankResult>
     */
    public CompletableFuture<BankResult> withdraw(UUID uuid, double amount, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(BankResult.INVALID_AMOUNT);
        }

        return plugin.getDatabaseManager().getProvider().getBalance(uuid)
                .thenCompose(currentBalance -> {
                    if (currentBalance < 0) {
                        return CompletableFuture.completedFuture(BankResult.ACCOUNT_NOT_FOUND);
                    }
                    if (currentBalance < amount) {
                        return CompletableFuture.completedFuture(BankResult.INSUFFICIENT_FUNDS);
                    }

                    return plugin.getDatabaseManager().getProvider().updateBalanceAtomic(uuid, -amount)
                            .thenApply(newBalance -> {
                                if (newBalance < 0) {
                                    return BankResult.DATABASE_ERROR;
                                }

                                plugin.getRedisPubSubService().publishBalanceUpdate(uuid, newBalance);
                                return BankResult.SUCCESS;
                            });
                });
    }

    /**
     * Transfer money between two bank accounts
     *
     * @param fromUuid Sender UUID
     * @param toUuid Receiver UUID
     * @param amount Amount to transfer
     * @return CompletableFuture<BankResult>
     */
    public CompletableFuture<BankResult> transfer(UUID fromUuid, UUID toUuid, double amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(BankResult.INVALID_AMOUNT);
        }
        if (fromUuid.equals(toUuid)) {
            return CompletableFuture.completedFuture(BankResult.SELF_TRANSFER);
        }

        return plugin.getDatabaseManager().getProvider().transferAtomic(fromUuid, toUuid, amount)
                .thenApply(success -> {
                    if (success) {
                        // Get new balances for Redis sync
                        plugin.getDatabaseManager().getProvider().getBalance(fromUuid)
                                .thenAccept(bal -> plugin.getRedisPubSubService().publishBalanceUpdate(fromUuid, bal));
                        plugin.getDatabaseManager().getProvider().getBalance(toUuid)
                                .thenAccept(bal -> plugin.getRedisPubSubService().publishBalanceUpdate(toUuid, bal));
                        return BankResult.SUCCESS;
                    }
                    return BankResult.INSUFFICIENT_FUNDS;
                });
    }

    /**
     * Set player's bank balance directly
     *
     * @param uuid Player UUID
     * @param amount New balance
     * @return CompletableFuture<BankResult>
     */
    public CompletableFuture<BankResult> setBalance(UUID uuid, double amount) {
        if (amount < 0) {
            return CompletableFuture.completedFuture(BankResult.INVALID_AMOUNT);
        }

        return plugin.getDatabaseManager().getProvider().setBalance(uuid, amount)
                .thenApply(success -> {
                    if (success) {
                        plugin.getRedisPubSubService().publishBalanceUpdate(uuid, amount);
                        return BankResult.SUCCESS;
                    }
                    return BankResult.ACCOUNT_NOT_FOUND;
                });
    }

    /**
     * Get player's bank tier
     * @param uuid Player UUID
     * @return CompletableFuture with tier level (1-5), -1 if not found
     */
    public CompletableFuture<Integer> getTier(UUID uuid) {
        return plugin.getDatabaseManager().getProvider().getAccount(uuid)
                .thenApply(opt -> opt.map(BankAccount::getTier).orElse(-1));
    }

    /**
     * Get max balance for a tier
     * @param tier Tier level
     * @return Max balance (-1 for unlimited)
     */
    public double getMaxBalanceForTier(int tier) {
        return plugin.getConfigManager().getConfig().getTier(tier).getMaxBalance();
    }

    /**
     * Get interest multiplier for a tier
     * @param tier Tier level
     * @return Interest multiplier
     */
    public double getInterestMultiplierForTier(int tier) {
        return plugin.getConfigManager().getConfig().getTier(tier).getInterestMultiplier();
    }

    /**
     * Format currency amount
     * @param amount Amount to format
     * @return Formatted string (e.g., "$1,234.56")
     */
    public String formatCurrency(double amount) {
        return plugin.getMessageUtil().formatCurrency(amount);
    }
}
