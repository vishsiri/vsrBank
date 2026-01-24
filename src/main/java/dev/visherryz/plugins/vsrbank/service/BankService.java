package dev.visherryz.plugins.vsrbank.service;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.database.provider.DatabaseProvider;
import dev.visherryz.plugins.vsrbank.event.BankLevelUpEvent;
import dev.visherryz.plugins.vsrbank.event.BankPostTransactionEvent;
import dev.visherryz.plugins.vsrbank.event.BankPreTransactionEvent;
import dev.visherryz.plugins.vsrbank.model.*;
import dev.visherryz.plugins.vsrbank.redis.RedisLockService;
import dev.visherryz.plugins.vsrbank.redis.RedisManager;
import dev.visherryz.plugins.vsrbank.redis.RedisPubSubService;
import lombok.RequiredArgsConstructor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Core Bank Service - handles all banking business logic
 * Implements transaction safety with distributed locking
 */
@RequiredArgsConstructor
public class BankService {

    private final VsrBank plugin;

    // Cooldown tracking (per-player)
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private DatabaseProvider getDatabase() {
        return plugin.getDatabaseManager().getProvider();
    }

    private RedisPubSubService getRedisPubSub() {
        return plugin.getRedisPubSubService();
    }

    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }

    private Economy getEconomy() {
        return plugin.getVaultHook().getEconomy();
    }

    private String getServerId() {
        return plugin.getConfigManager().getConfig().getServerId();
    }

    // ==================== Event Helpers ====================

    /**
     * Fire pre-transaction event and check if cancelled
     * @return true if event was cancelled (transaction should stop)
     */
    private boolean isTransactionCancelled(UUID uuid, String playerName,
                                           TransactionLog.TransactionType type,
                                           double amount, double currentBalance) {
        BankPreTransactionEvent event = new BankPreTransactionEvent(
                uuid, playerName, type, amount, currentBalance);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    /**
     * Fire post-transaction event (async, non-blocking)
     */
    private void firePostTransactionEvent(UUID uuid, String playerName,
                                          TransactionLog.TransactionType type,
                                          double amount, double previousBalance, double newBalance) {
        BankPostTransactionEvent event = new BankPostTransactionEvent(
                uuid, playerName, type, amount, previousBalance, newBalance);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Fire level up event
     */
    private void fireLevelUpEvent(UUID uuid, String playerName,
                                  int previousTier, int newTier,
                                  String newTierName, double upgradeCost) {
        BankLevelUpEvent event = new BankLevelUpEvent(
                uuid, playerName, previousTier, newTier, newTierName, upgradeCost);
        Bukkit.getPluginManager().callEvent(event);
    }

    // ==================== Account Management ====================

    public CompletableFuture<BankAccount> getOrCreateAccount(UUID uuid, String playerName) {
        return getDatabase().getAccount(uuid).thenCompose(optAccount -> {
            if (optAccount.isPresent()) {
                BankAccount account = optAccount.get();
                if (!account.getPlayerName().equals(playerName)) {
                    getDatabase().updatePlayerName(uuid, playerName);
                    account.setPlayerName(playerName);
                }
                return CompletableFuture.completedFuture(account);
            } else {
                BankAccount newAccount = BankAccount.createNew(uuid, playerName);
                return getDatabase().createAccount(newAccount);
            }
        });
    }

    public CompletableFuture<Boolean> hasAccount(UUID uuid) {
        return getDatabase().accountExists(uuid);
    }

    public CompletableFuture<Optional<BankAccount>> getAccount(UUID uuid) {
        return getDatabase().getAccount(uuid);
    }

    public CompletableFuture<Optional<BankAccount>> getAccountByName(String playerName) {
        return getDatabase().getAccountByName(playerName);
    }

    public CompletableFuture<Double> getBalance(UUID uuid) {
        return getDatabase().getBalance(uuid);
    }

    // ==================== Deposit ====================

    public CompletableFuture<TransactionResponse> deposit(Player player, double amount, String reason) {
        UUID uuid = player.getUniqueId();
        BankConfig config = plugin.getConfigManager().getConfig();
        BankConfig.TransactionSettings txSettings = config.getTransaction();

        if (amount <= 0) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.INVALID_AMOUNT));
        }

        if (amount < txSettings.getMinDepositAmount()) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.BELOW_MINIMUM));
        }

        if (!checkCooldown(uuid)) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.COOLDOWN_ACTIVE));
        }

        Economy economy = getEconomy();
        if (economy == null) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.VAULT_NOT_AVAILABLE));
        }

        if (!economy.has(player, amount)) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.INSUFFICIENT_FUNDS));
        }

        return executeWithLock(uuid, () -> {
            return getAccount(uuid).thenCompose(optAccount -> {
                if (optAccount.isEmpty()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.ACCOUNT_NOT_FOUND));
                }

                BankAccount account = optAccount.get();
                double previousBalance = account.getBalance();

                // Fire Pre-Transaction Event
                if (isTransactionCancelled(uuid, player.getName(),
                        TransactionLog.TransactionType.DEPOSIT, amount, previousBalance)) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.TRANSACTION_LOCKED)); // Event cancelled
                }

                // Check max balance
                BankConfig.TierSettings tier = config.getTier(account.getTier());
                double maxBalance = tier.getMaxBalance();
                if (!account.canDeposit(amount, maxBalance) &&
                        !player.hasPermission("vsrbank.bypass.maxbalance")) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.MAX_BALANCE_REACHED, previousBalance));
                }

                // Withdraw from Vault
                if (!economy.withdrawPlayer(player, amount).transactionSuccess()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.INSUFFICIENT_FUNDS));
                }

                // Deposit to bank (ATOMIC)
                return getDatabase().updateBalanceAtomic(uuid, amount).thenApply(newBalance -> {
                    if (newBalance < 0) {
                        economy.depositPlayer(player, amount); // Rollback
                        return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                    }

                    // Log transaction
                    TransactionLog log = TransactionLog.deposit(
                            uuid, player.getName(), amount, previousBalance, newBalance, getServerId());
                    log.setReason(reason);
                    getDatabase().insertLog(log);

                    // Publish to Redis
                    getRedisPubSub().publishBalanceUpdate(uuid, newBalance);

                    // Fire Post-Transaction Event
                    firePostTransactionEvent(uuid, player.getName(),
                            TransactionLog.TransactionType.DEPOSIT, amount, previousBalance, newBalance);

                    setCooldown(uuid);
                    return TransactionResponse.success(previousBalance, newBalance, amount);
                });
            });
        });
    }

    // ==================== Withdraw ====================

    public CompletableFuture<TransactionResponse> withdraw(Player player, double amount, String reason) {
        UUID uuid = player.getUniqueId();
        BankConfig config = plugin.getConfigManager().getConfig();
        BankConfig.TransactionSettings txSettings = config.getTransaction();

        if (amount <= 0) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.INVALID_AMOUNT));
        }

        if (amount < txSettings.getMinWithdrawAmount()) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.BELOW_MINIMUM));
        }

        if (!checkCooldown(uuid)) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.COOLDOWN_ACTIVE));
        }

        Economy economy = getEconomy();
        if (economy == null) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.VAULT_NOT_AVAILABLE));
        }

        return executeWithLock(uuid, () -> {
            return getAccount(uuid).thenCompose(optAccount -> {
                if (optAccount.isEmpty()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.ACCOUNT_NOT_FOUND));
                }

                BankAccount account = optAccount.get();
                double previousBalance = account.getBalance();

                // Fire Pre-Transaction Event
                if (isTransactionCancelled(uuid, player.getName(),
                        TransactionLog.TransactionType.WITHDRAW, amount, previousBalance)) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.TRANSACTION_LOCKED));
                }

                if (!account.hasBalance(amount)) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.INSUFFICIENT_FUNDS, previousBalance));
                }

                // Withdraw from bank (ATOMIC)
                return getDatabase().updateBalanceAtomic(uuid, -amount).thenApply(newBalance -> {
                    if (newBalance < 0) {
                        return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                    }

                    // Deposit to Vault
                    economy.depositPlayer(player, amount);

                    // Log
                    TransactionLog log = TransactionLog.withdraw(
                            uuid, player.getName(), amount, previousBalance, newBalance, getServerId());
                    log.setReason(reason);
                    getDatabase().insertLog(log);

                    // Publish to Redis
                    getRedisPubSub().publishBalanceUpdate(uuid, newBalance);

                    // Fire Post-Transaction Event
                    firePostTransactionEvent(uuid, player.getName(),
                            TransactionLog.TransactionType.WITHDRAW, amount, previousBalance, newBalance);

                    setCooldown(uuid);
                    return TransactionResponse.success(previousBalance, newBalance, amount);
                });
            });
        });
    }

    // ==================== Transfer ====================

    public CompletableFuture<TransactionResponse> transfer(Player sender, UUID receiverUuid,
                                                           String receiverName, double amount, String reason) {
        UUID senderUuid = sender.getUniqueId();
        BankConfig config = plugin.getConfigManager().getConfig();
        BankConfig.TransactionSettings txSettings = config.getTransaction();

        if (amount <= 0) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.INVALID_AMOUNT));
        }

        if (senderUuid.equals(receiverUuid)) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.SELF_TRANSFER));
        }

        if (amount < txSettings.getMinTransferAmount()) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.BELOW_MINIMUM));
        }

        if (txSettings.getMaxTransferAmount() > 0 && amount > txSettings.getMaxTransferAmount()) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.ABOVE_MAXIMUM));
        }

        OfflinePlayer receiver = Bukkit.getOfflinePlayer(receiverUuid);
        if (!txSettings.isAllowOfflineTransfer() && !receiver.isOnline()) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.OFFLINE_TRANSFER_DISABLED));
        }

        if (!checkCooldown(senderUuid)) {
            return CompletableFuture.completedFuture(TransactionResponse.failure(BankResult.COOLDOWN_ACTIVE));
        }

        double fee = amount * txSettings.getTransferFeePercent();
        double totalDeduct = amount + fee;

        return executeWithLock(senderUuid, () -> {
            return getAccount(senderUuid).thenCompose(senderOpt -> {
                if (senderOpt.isEmpty()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.ACCOUNT_NOT_FOUND));
                }

                return getAccount(receiverUuid).thenCompose(receiverOpt -> {
                    if (receiverOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                TransactionResponse.failure(BankResult.TARGET_NOT_FOUND));
                    }

                    BankAccount senderAccount = senderOpt.get();
                    BankAccount receiverAccount = receiverOpt.get();

                    double senderBefore = senderAccount.getBalance();
                    double receiverBefore = receiverAccount.getBalance();

                    // Fire Pre-Transaction Event for sender
                    if (isTransactionCancelled(senderUuid, sender.getName(),
                            TransactionLog.TransactionType.TRANSFER_OUT, totalDeduct, senderBefore)) {
                        return CompletableFuture.completedFuture(
                                TransactionResponse.failure(BankResult.TRANSACTION_LOCKED));
                    }

                    if (!senderAccount.hasBalance(totalDeduct)) {
                        return CompletableFuture.completedFuture(
                                TransactionResponse.failure(BankResult.INSUFFICIENT_FUNDS, senderBefore));
                    }

                    BankConfig.TierSettings receiverTier = config.getTier(receiverAccount.getTier());
                    if (!receiverAccount.canDeposit(amount, receiverTier.getMaxBalance())) {
                        return CompletableFuture.completedFuture(
                                TransactionResponse.failure(BankResult.MAX_BALANCE_REACHED));
                    }

                    // Execute atomic transfer
                    return getDatabase().transferAtomic(senderUuid, receiverUuid, amount).thenCompose(success -> {
                        if (!success) {
                            return CompletableFuture.completedFuture(
                                    TransactionResponse.failure(BankResult.DATABASE_ERROR));
                        }

                        CompletableFuture<Void> feeFuture;
                        if (fee > 0) {
                            feeFuture = getDatabase().updateBalanceAtomic(senderUuid, -fee).thenAccept(v -> {});
                        } else {
                            feeFuture = CompletableFuture.completedFuture(null);
                        }

                        return feeFuture.thenApply(v -> {
                            double senderAfter = senderBefore - totalDeduct;
                            double receiverAfter = receiverBefore + amount;

                            // Log both
                            TransactionLog[] logs = TransactionLog.transfer(
                                    senderUuid, sender.getName(), senderBefore, senderAfter,
                                    receiverUuid, receiverName, receiverBefore, receiverAfter,
                                    amount, getServerId()
                            );
                            getDatabase().insertLog(logs[0]);
                            getDatabase().insertLog(logs[1]);

                            if (fee > 0) {
                                TransactionLog feeLog = TransactionLog.builder()
                                        .playerUuid(senderUuid)
                                        .playerName(sender.getName())
                                        .type(TransactionLog.TransactionType.FEE)
                                        .amount(fee)
                                        .balanceBefore(senderBefore - amount)
                                        .balanceAfter(senderAfter)
                                        .serverId(getServerId())
                                        .reason("Transfer fee")
                                        .timestamp(Instant.now())
                                        .build();
                                getDatabase().insertLog(feeLog);
                            }

                            // Publish to Redis
                            getRedisPubSub().publishBalanceUpdate(senderUuid, senderAfter);
                            getRedisPubSub().publishBalanceUpdate(receiverUuid, receiverAfter);

                            // Fire Post-Transaction Events
                            firePostTransactionEvent(senderUuid, sender.getName(),
                                    TransactionLog.TransactionType.TRANSFER_OUT, amount, senderBefore, senderAfter);
                            firePostTransactionEvent(receiverUuid, receiverName,
                                    TransactionLog.TransactionType.TRANSFER_IN, amount, receiverBefore, receiverAfter);

                            // Notify receiver
                            Player receiverPlayer = Bukkit.getPlayer(receiverUuid);
                            if (receiverPlayer != null && receiverPlayer.isOnline()) {
                                plugin.getMessageUtil().sendTransferReceived(receiverPlayer, sender.getName(), amount);
                            }

                            setCooldown(senderUuid);
                            return TransactionResponse.successWithFee(senderBefore, senderAfter, amount, fee);
                        });
                    });
                });
            });
        });
    }

    // ==================== Upgrade Tier ====================

    public CompletableFuture<TransactionResponse> upgradeTier(Player player) {
        UUID uuid = player.getUniqueId();
        BankConfig config = plugin.getConfigManager().getConfig();

        return executeWithLock(uuid, () -> {
            return getAccount(uuid).thenCompose(optAccount -> {
                if (optAccount.isEmpty()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.ACCOUNT_NOT_FOUND));
                }

                BankAccount account = optAccount.get();
                int currentTier = account.getTier();
                int nextTier = currentTier + 1;

                if (nextTier > config.getMaxTier()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.MAX_TIER_REACHED));
                }

                BankConfig.TierSettings nextTierSettings = config.getTier(nextTier);
                double upgradeCost = nextTierSettings.getUpgradeCost();
                int upgradeXp = nextTierSettings.getUpgradeXpCost();

                double previousBalance = account.getBalance();

                // Fire Pre-Transaction Event
                if (isTransactionCancelled(uuid, player.getName(),
                        TransactionLog.TransactionType.UPGRADE, upgradeCost, previousBalance)) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.TRANSACTION_LOCKED));
                }

                if (!account.hasBalance(upgradeCost)) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.INSUFFICIENT_MONEY_FOR_UPGRADE, previousBalance));
                }

                if (player.getTotalExperience() < upgradeXp) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.INSUFFICIENT_XP_FOR_UPGRADE));
                }

                // Deduct costs
                return getDatabase().updateBalanceAtomic(uuid, -upgradeCost).thenCompose(newBalance -> {
                    if (newBalance < 0) {
                        return CompletableFuture.completedFuture(
                                TransactionResponse.failure(BankResult.DATABASE_ERROR));
                    }

                    // Deduct XP (on main thread)
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.giveExp(-upgradeXp);
                    });

                    return getDatabase().updateTier(uuid, nextTier).thenApply(success -> {
                        if (!success) {
                            getDatabase().updateBalanceAtomic(uuid, upgradeCost); // Rollback
                            return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                        }

                        // Log
                        TransactionLog log = TransactionLog.builder()
                                .playerUuid(uuid)
                                .playerName(player.getName())
                                .type(TransactionLog.TransactionType.UPGRADE)
                                .amount(upgradeCost)
                                .balanceBefore(previousBalance)
                                .balanceAfter(newBalance)
                                .serverId(getServerId())
                                .reason("Upgraded to " + nextTierSettings.getName())
                                .timestamp(Instant.now())
                                .build();
                        getDatabase().insertLog(log);

                        // Publish to Redis
                        getRedisPubSub().publishBalanceUpdate(uuid, newBalance);

                        // Fire Post-Transaction Event
                        firePostTransactionEvent(uuid, player.getName(),
                                TransactionLog.TransactionType.UPGRADE, upgradeCost, previousBalance, newBalance);

                        // Fire Level Up Event
                        fireLevelUpEvent(uuid, player.getName(),
                                currentTier, nextTier, nextTierSettings.getName(), upgradeCost);

                        return TransactionResponse.success(previousBalance, newBalance, upgradeCost);
                    });
                });
            });
        });
    }

    // ==================== Admin Operations ====================

    public CompletableFuture<TransactionResponse> adminGive(UUID targetUuid, String targetName,
                                                            double amount, String adminName) {
        return executeWithLock(targetUuid, () -> {
            return getAccount(targetUuid).thenCompose(optAccount -> {
                if (optAccount.isEmpty()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.ACCOUNT_NOT_FOUND));
                }

                BankAccount account = optAccount.get();
                double previousBalance = account.getBalance();

                return getDatabase().updateBalanceAtomic(targetUuid, amount).thenApply(newBalance -> {
                    if (newBalance < 0) {
                        return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                    }

                    // Log
                    TransactionLog log = TransactionLog.adminAction(
                            targetUuid, targetName, TransactionLog.TransactionType.ADMIN_GIVE,
                            amount, previousBalance, newBalance, adminName, getServerId(), null);
                    getDatabase().insertLog(log);

                    // Discord
                    plugin.getDiscordWebhook().sendAdminAction(adminName, "GIVE", targetName, amount);

                    // Publish
                    getRedisPubSub().publishBalanceUpdate(targetUuid, newBalance);

                    // Fire Post Event
                    firePostTransactionEvent(targetUuid, targetName,
                            TransactionLog.TransactionType.ADMIN_GIVE, amount, previousBalance, newBalance);

                    return TransactionResponse.success(previousBalance, newBalance, amount);
                });
            });
        });
    }

    public CompletableFuture<TransactionResponse> adminTake(UUID targetUuid, String targetName,
                                                            double amount, String adminName) {
        return executeWithLock(targetUuid, () -> {
            return getAccount(targetUuid).thenCompose(optAccount -> {
                if (optAccount.isEmpty()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.ACCOUNT_NOT_FOUND));
                }

                BankAccount account = optAccount.get();
                double previousBalance = account.getBalance();
                double actualAmount = Math.min(amount, previousBalance);

                return getDatabase().updateBalanceAtomic(targetUuid, -actualAmount).thenApply(newBalance -> {
                    if (newBalance < 0) {
                        return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                    }

                    TransactionLog log = TransactionLog.adminAction(
                            targetUuid, targetName, TransactionLog.TransactionType.ADMIN_TAKE,
                            actualAmount, previousBalance, newBalance, adminName, getServerId(), null);
                    getDatabase().insertLog(log);

                    plugin.getDiscordWebhook().sendAdminAction(adminName, "TAKE", targetName, actualAmount);
                    getRedisPubSub().publishBalanceUpdate(targetUuid, newBalance);

                    firePostTransactionEvent(targetUuid, targetName,
                            TransactionLog.TransactionType.ADMIN_TAKE, actualAmount, previousBalance, newBalance);

                    return TransactionResponse.success(previousBalance, newBalance, actualAmount);
                });
            });
        });
    }

    public CompletableFuture<TransactionResponse> adminSet(UUID targetUuid, String targetName,
                                                           double amount, String adminName) {
        return executeWithLock(targetUuid, () -> {
            return getAccount(targetUuid).thenCompose(optAccount -> {
                if (optAccount.isEmpty()) {
                    return CompletableFuture.completedFuture(
                            TransactionResponse.failure(BankResult.ACCOUNT_NOT_FOUND));
                }

                BankAccount account = optAccount.get();
                double previousBalance = account.getBalance();

                return getDatabase().setBalance(targetUuid, amount).thenApply(success -> {
                    if (!success) {
                        return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                    }

                    TransactionLog log = TransactionLog.adminAction(
                            targetUuid, targetName, TransactionLog.TransactionType.ADMIN_SET,
                            amount, previousBalance, amount, adminName, getServerId(), null);
                    getDatabase().insertLog(log);

                    plugin.getDiscordWebhook().sendAdminAction(adminName, "SET", targetName, amount);
                    getRedisPubSub().publishBalanceUpdate(targetUuid, amount);

                    firePostTransactionEvent(targetUuid, targetName,
                            TransactionLog.TransactionType.ADMIN_SET, amount, previousBalance, amount);

                    return TransactionResponse.success(previousBalance, amount, amount);
                });
            });
        });
    }

    // ==================== History ====================

    public CompletableFuture<List<TransactionLog>> getHistory(UUID uuid, int limit) {
        return getDatabase().getTransactionHistory(uuid, limit);
    }

    // ==================== Utility Methods ====================

    private boolean checkCooldown(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.hasPermission("vsrbank.bypass.cooldown")) {
            return true;
        }

        Long lastTransaction = cooldowns.get(uuid);
        if (lastTransaction == null) {
            return true;
        }

        long cooldownMs = plugin.getConfigManager().getConfig().getTransaction().getCooldownMs();
        return System.currentTimeMillis() - lastTransaction >= cooldownMs;
    }

    private void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    public double getRemainingCooldown(UUID uuid) {
        Long lastTransaction = cooldowns.get(uuid);
        if (lastTransaction == null) {
            return 0;
        }

        long cooldownMs = plugin.getConfigManager().getConfig().getTransaction().getCooldownMs();
        long remaining = cooldownMs - (System.currentTimeMillis() - lastTransaction);
        return Math.max(0, remaining / 1000.0);
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> executeWithLock(UUID uuid, java.util.function.Supplier<CompletableFuture<T>> operation) {
        RedisLockService lockService = plugin.getRedisLockService();

        if (!plugin.getRedisManager().isConnected()) {
            return operation.get();
        }

        return lockService.withLockAsync(uuid, 5, TimeUnit.SECONDS, operation)
                .exceptionally(error -> {
                    // Lock failed
                    return (T) TransactionResponse.failure(BankResult.TRANSACTION_LOCKED);
                });
    }

    private <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long timeout, TimeUnit unit) {
        return future.orTimeout(timeout, unit)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException) {
                        plugin.getLogger().warning("Operation timed out after " + timeout + " " + unit);
                    }
                    throw new CompletionException(ex);
                });
    }
}