package dev.visherryz.plugins.vsrbank.service;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.database.provider.DatabaseProvider;
import dev.visherryz.plugins.vsrbank.event.BankLevelUpEvent;
import dev.visherryz.plugins.vsrbank.event.BankPostTransactionEvent;
import dev.visherryz.plugins.vsrbank.event.BankPreTransactionEvent;
import dev.visherryz.plugins.vsrbank.model.*;
import dev.visherryz.plugins.vsrbank.redis.RedisLockService;
import dev.visherryz.plugins.vsrbank.redis.RedisPubSubService;
import lombok.RequiredArgsConstructor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Core Bank Service - handles all banking business logic
 * Refactored: ใช้ helper methods ลด boilerplate ของ log/event/publish pipeline
 */
@RequiredArgsConstructor
public class BankService {

    private final VsrBank plugin;
    private final TierRequirementService tierRequirementService;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // ==================== Accessors ====================

    private DatabaseProvider db() {
        return plugin.getDatabaseManager().getProvider();
    }

    private Economy economy() {
        return plugin.getVaultHook().getEconomy();
    }

    private BankConfig config() {
        return plugin.getConfigManager().getConfig();
    }

    private String serverId() {
        return config().getServerId();
    }

    // ==================== Transaction Pipeline Helpers ====================

    /**
     * บันทึก log + publish Redis + fire post-event ในขั้นตอนเดียว
     * ใช้แทน boilerplate ที่ซ้ำกันทุก operation
     */
    private void commitTransaction(UUID uuid, String playerName,
                                   TransactionLog.TransactionType type,
                                   double amount, double prevBalance, double newBalance,
                                   String reason) {
        commitTransaction(uuid, playerName, type, amount, prevBalance, newBalance, reason, null, null);
    }

    private void commitTransaction(UUID uuid, String playerName,
                                   TransactionLog.TransactionType type,
                                   double amount, double prevBalance, double newBalance,
                                   String reason, UUID targetUuid, String targetName) {
        // 1. Log
        TransactionLog.TransactionLogBuilder logBuilder = TransactionLog.builder()
                .playerUuid(uuid)
                .playerName(playerName)
                .type(type)
                .amount(amount)
                .balanceBefore(prevBalance)
                .balanceAfter(newBalance)
                .serverId(serverId())
                .reason(reason)
                .timestamp(Instant.now());

        if (targetUuid != null) {
            logBuilder.targetUuid(targetUuid).targetName(targetName);
        }

        db().insertLog(logBuilder.build());

        // 2. Redis publish
        publishBalanceUpdate(uuid, newBalance);

        // 3. Post-event
        firePostTransactionEvent(uuid, playerName, type, amount, prevBalance, newBalance);
    }

    /**
     * บันทึก admin action: log + discord + redis + post-event
     */
    private void commitAdminTransaction(UUID uuid, String playerName,
                                        TransactionLog.TransactionType type,
                                        double amount, double prevBalance, double newBalance,
                                        String adminName) {
        TransactionLog log = TransactionLog.adminAction(
                uuid, playerName, type, amount, prevBalance, newBalance, adminName, serverId(), null);
        db().insertLog(log);

        plugin.getDiscordWebhook().sendAdminAction(adminName, type.name().replace("ADMIN_", ""), playerName, amount);
        publishBalanceUpdate(uuid, newBalance);
        firePostTransactionEvent(uuid, playerName, type, amount, prevBalance, newBalance);
    }

    /**
     * Quick fail helper — ลดการเขียน CompletableFuture.completedFuture ซ้ำๆ
     */
    private CompletableFuture<TransactionResponse> fail(BankResult result) {
        return CompletableFuture.completedFuture(TransactionResponse.failure(result));
    }

    private CompletableFuture<TransactionResponse> fail(BankResult result, double currentBalance) {
        return CompletableFuture.completedFuture(TransactionResponse.failure(result, currentBalance));
    }

    private CompletableFuture<TransactionResponse> succeed(double prev, double now, double processed) {
        return CompletableFuture.completedFuture(TransactionResponse.success(prev, now, processed));
    }

    // ==================== Event Helpers ====================

    private boolean isTransactionCancelled(UUID uuid, String playerName,
                                           TransactionLog.TransactionType type,
                                           double amount, double currentBalance) {
        BankPreTransactionEvent event = new BankPreTransactionEvent(
                uuid, playerName, type, amount, currentBalance);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    private void firePostTransactionEvent(UUID uuid, String playerName,
                                          TransactionLog.TransactionType type,
                                          double amount, double previousBalance, double newBalance) {
        Bukkit.getPluginManager().callEvent(
                new BankPostTransactionEvent(uuid, playerName, type, amount, previousBalance, newBalance));
    }

    private void fireLevelUpEvent(UUID uuid, String playerName,
                                  int previousTier, int newTier,
                                  String newTierName, double upgradeCost) {
        Bukkit.getPluginManager().callEvent(
                new BankLevelUpEvent(uuid, playerName, previousTier, newTier, newTierName, upgradeCost));
    }

    private void publishBalanceUpdate(UUID uuid, double newBalance) {
        RedisPubSubService pubSub = plugin.getRedisPubSubService();
        if (pubSub != null) {
            pubSub.publishBalanceUpdate(uuid, newBalance);
        }
    }

    // ==================== Cooldown ====================

    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }

    public double getRemainingCooldown(UUID uuid) {
        Long last = cooldowns.get(uuid);
        if (last == null) return 0;

        long cooldownMs = config().getTransaction().getCooldownMs();
        long remaining = cooldownMs - (System.currentTimeMillis() - last);
        return Math.max(0, remaining / 1000.0);
    }

    private boolean checkAndSetCooldown(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.hasPermission("vsrbank.bypass.cooldown")) {
            return true;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = config().getTransaction().getCooldownMs();

        boolean[] allowed = {false};
        cooldowns.compute(uuid, (key, last) -> {
            if (last == null || (now - last) >= cooldownMs) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        return allowed[0];
    }

    // ==================== Account Management ====================

    public CompletableFuture<BankAccount> getOrCreateAccount(UUID uuid, String playerName) {
        return db().getAccount(uuid).thenCompose(opt -> {
            if (opt.isPresent()) {
                BankAccount account = opt.get();
                if (!account.getPlayerName().equals(playerName)) {
                    db().updatePlayerName(uuid, playerName);
                    account.setPlayerName(playerName);
                }
                return CompletableFuture.completedFuture(account);
            } else {
                return db().createAccount(BankAccount.createNew(uuid, playerName));
            }
        });
    }

    public CompletableFuture<Boolean> hasAccount(UUID uuid) {
        return db().accountExists(uuid);
    }

    public CompletableFuture<Optional<BankAccount>> getAccount(UUID uuid) {
        return db().getAccount(uuid);
    }

    public CompletableFuture<Optional<BankAccount>> getAccountByName(String playerName) {
        return db().getAccountByName(playerName);
    }

    public CompletableFuture<Double> getBalance(UUID uuid) {
        return db().getBalance(uuid);
    }

    // ==================== Deposit ====================

    public CompletableFuture<TransactionResponse> deposit(Player player, double amount, String reason) {
        UUID uuid = player.getUniqueId();
        BankConfig.TransactionSettings tx = config().getTransaction();

        // Pre-validation
        if (amount <= 0) return fail(BankResult.INVALID_AMOUNT);
        if (amount < tx.getMinDepositAmount()) return fail(BankResult.BELOW_MINIMUM);
        if (!checkAndSetCooldown(uuid)) return fail(BankResult.COOLDOWN_ACTIVE);

        Economy eco = economy();
        if (eco == null) return fail(BankResult.VAULT_NOT_AVAILABLE);
        if (!eco.has(player, amount)) return fail(BankResult.INSUFFICIENT_FUNDS);

        return executeWithLock(uuid, () ->
                getAccount(uuid).thenCompose(opt -> {
                    if (opt.isEmpty()) return fail(BankResult.ACCOUNT_NOT_FOUND);

                    BankAccount account = opt.get();
                    double prevBalance = account.getBalance();

                    if (isTransactionCancelled(uuid, player.getName(),
                            TransactionLog.TransactionType.DEPOSIT, amount, prevBalance)) {
                        return fail(BankResult.TRANSACTION_LOCKED);
                    }

                    // Clamp to max balance
                    BankConfig.TierSettings tier = config().getTier(account.getTier());
                    double maxBalance = tier.getMaxBalance();
                    double depositAmount = amount;

                    if (maxBalance >= 0 && !player.hasPermission("vsrbank.bypass.maxbalance")) {
                        double remaining = maxBalance - prevBalance;
                        if (remaining <= 0) return fail(BankResult.MAX_BALANCE_REACHED);
                        if (amount > remaining) depositAmount = remaining;
                    }

                    final double finalAmount = depositAmount;

                    // Vault withdraw → Bank deposit
                    return CompletableFuture.supplyAsync(() ->
                            eco.withdrawPlayer(player, finalAmount).transactionSuccess()
                    ).thenCompose(vaultOk -> {
                        if (!vaultOk) return fail(BankResult.VAULT_TRANSACTION_FAILED);

                        return db().updateBalanceAtomic(uuid, finalAmount).thenApply(newBalance -> {
                            if (newBalance < 0) {
                                eco.depositPlayer(player, finalAmount); // rollback vault
                                return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                            }

                            commitTransaction(uuid, player.getName(),
                                    TransactionLog.TransactionType.DEPOSIT,
                                    finalAmount, prevBalance, newBalance,
                                    reason != null ? reason : "Deposit");

                            return TransactionResponse.success(prevBalance, newBalance, finalAmount);
                        });
                    });
                })
        ).thenApply(response -> {
            if (!response.isSuccess()) clearCooldown(uuid);
            return response;
        });
    }

    // ==================== Withdraw ====================

    public CompletableFuture<TransactionResponse> withdraw(Player player, double amount, String reason) {
        UUID uuid = player.getUniqueId();
        BankConfig.TransactionSettings tx = config().getTransaction();

        // Pre-validation
        if (amount <= 0) return fail(BankResult.INVALID_AMOUNT);
        if (amount < tx.getMinWithdrawAmount()) return fail(BankResult.BELOW_MINIMUM);
        if (!checkAndSetCooldown(uuid)) return fail(BankResult.COOLDOWN_ACTIVE);

        Economy eco = economy();
        if (eco == null) {
            clearCooldown(uuid);
            return fail(BankResult.VAULT_NOT_AVAILABLE);
        }

        return executeWithLock(uuid, () ->
                getAccount(uuid).thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        clearCooldown(uuid);
                        return fail(BankResult.ACCOUNT_NOT_FOUND);
                    }

                    BankAccount account = opt.get();
                    double prevBalance = account.getBalance();

                    if (isTransactionCancelled(uuid, player.getName(),
                            TransactionLog.TransactionType.WITHDRAW, amount, prevBalance)) {
                        clearCooldown(uuid);
                        return fail(BankResult.TRANSACTION_LOCKED);
                    }

                    if (!account.hasBalance(amount)) {
                        clearCooldown(uuid);
                        return fail(BankResult.INSUFFICIENT_BANK_BALANCE);
                    }

                    // Bank debit → Vault deposit
                    return db().updateBalanceAtomic(uuid, -amount).thenCompose(newBalance -> {
                        if (newBalance < 0) {
                            plugin.getLogger().severe(String.format(
                                    "CRITICAL: Withdraw balance went negative! UUID=%s, Prev=%.2f, Amount=%.2f, New=%.2f",
                                    uuid, prevBalance, amount, newBalance));
                            db().setBalance(uuid, prevBalance);
                            clearCooldown(uuid);
                            return fail(BankResult.INSUFFICIENT_BANK_BALANCE);
                        }

                        return CompletableFuture.supplyAsync(() ->
                                eco.depositPlayer(player, amount).transactionSuccess()
                        ).thenCompose(vaultOk -> {
                            if (!vaultOk) {
                                return db().updateBalanceAtomic(uuid, amount).thenApply(rollback -> {
                                    plugin.getLogger().warning("Withdraw Vault deposit failed for " +
                                            player.getName() + ", rolled back to " + rollback);
                                    clearCooldown(uuid);
                                    return TransactionResponse.failure(BankResult.VAULT_TRANSACTION_FAILED);
                                });
                            }

                            commitTransaction(uuid, player.getName(),
                                    TransactionLog.TransactionType.WITHDRAW,
                                    amount, prevBalance, newBalance,
                                    reason != null ? reason : "Withdrawal");

                            return succeed(prevBalance, newBalance, amount);
                        });
                    });
                })
        ).thenApply(response -> {
            if (!response.isSuccess()) clearCooldown(uuid);
            return response;
        });
    }

    // ==================== Transfer ====================

    public CompletableFuture<TransactionResponse> transfer(Player sender, String recipientName, double amount) {
        UUID senderUuid = sender.getUniqueId();
        BankConfig.TransactionSettings tx = config().getTransaction();

        // Pre-validation
        if (amount <= 0) return fail(BankResult.INVALID_AMOUNT);
        if (amount < tx.getMinTransferAmount()) return fail(BankResult.BELOW_MINIMUM);
        if (tx.getMaxTransferAmount() > 0 && amount > tx.getMaxTransferAmount()) return fail(BankResult.ABOVE_MAXIMUM);
        if (!checkAndSetCooldown(senderUuid)) return fail(BankResult.COOLDOWN_ACTIVE);

        double fee = amount * tx.getTransferFeePercent();
        double totalDeducted = amount + fee;

        return getAccountByName(recipientName).thenCompose(optRecipient -> {
            if (optRecipient.isEmpty()) return fail(BankResult.RECIPIENT_NOT_FOUND);

            BankAccount recipientPreCheck = optRecipient.get();
            UUID recipientUuid = recipientPreCheck.getUuid();

            if (senderUuid.equals(recipientUuid)) return fail(BankResult.CANNOT_TRANSFER_SELF);

            Player recipientPlayer = Bukkit.getPlayer(recipientUuid);
            if (!tx.isAllowOfflineTransfer() && recipientPlayer == null) {
                return fail(BankResult.RECIPIENT_OFFLINE);
            }

            return executeWithDualLock(senderUuid, recipientUuid, () ->
                    getAccount(senderUuid).thenCompose(optSender -> {
                        if (optSender.isEmpty()) return fail(BankResult.ACCOUNT_NOT_FOUND);

                        return getAccount(recipientUuid).thenCompose(optRecipientFresh -> {
                            if (optRecipientFresh.isEmpty()) return fail(BankResult.RECIPIENT_NOT_FOUND);

                            BankAccount senderAcc = optSender.get();
                            BankAccount recipientAcc = optRecipientFresh.get();
                            double senderPrev = senderAcc.getBalance();
                            double recipientPrev = recipientAcc.getBalance();

                            if (isTransactionCancelled(senderUuid, sender.getName(),
                                    TransactionLog.TransactionType.TRANSFER_OUT, totalDeducted, senderPrev)) {
                                return fail(BankResult.TRANSACTION_LOCKED);
                            }

                            if (!senderAcc.hasBalance(totalDeducted)) {
                                return fail(BankResult.INSUFFICIENT_BANK_BALANCE);
                            }

                            // Check recipient max balance
                            BankConfig.TierSettings recipientTier = config().getTier(recipientAcc.getTier());
                            double recipientMax = recipientTier.getMaxBalance();
                            if (recipientMax >= 0 && !recipientAcc.canDeposit(amount, recipientMax)) {
                                return fail(BankResult.RECIPIENT_MAX_BALANCE);
                            }

                            // Step 1: Deduct sender
                            return db().updateBalanceAtomic(senderUuid, -totalDeducted).thenCompose(senderNew -> {
                                if (senderNew < 0) {
                                    plugin.getLogger().severe(String.format(
                                            "CRITICAL: Transfer sender negative! Sender=%s, Amount=%.2f, New=%.2f",
                                            senderUuid, totalDeducted, senderNew));
                                    db().setBalance(senderUuid, senderPrev);
                                    return fail(BankResult.INSUFFICIENT_BANK_BALANCE);
                                }

                                // Step 2: Credit recipient
                                return db().updateBalanceAtomic(recipientUuid, amount).thenCompose(recipientNew -> {
                                    if (recipientNew < 0) {
                                        return db().updateBalanceAtomic(senderUuid, totalDeducted).thenApply(rollback -> {
                                            plugin.getLogger().warning("Transfer failed for recipient " +
                                                    recipientName + ", rolled back sender to " + rollback);
                                            return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                                        });
                                    }

                                    // Commit both sides
                                    commitTransaction(senderUuid, sender.getName(),
                                            TransactionLog.TransactionType.TRANSFER_OUT,
                                            -totalDeducted, senderPrev, senderNew,
                                            "Transfer to " + recipientName,
                                            recipientUuid, recipientName);

                                    commitTransaction(recipientUuid, recipientName,
                                            TransactionLog.TransactionType.TRANSFER_IN,
                                            amount, recipientPrev, recipientNew,
                                            "Transfer from " + sender.getName(),
                                            senderUuid, sender.getName());

                                    if (recipientPlayer != null) {
                                        plugin.getMessageUtil().sendTransferReceived(
                                                recipientPlayer, sender.getName(), amount);
                                    }

                                    return succeed(senderPrev, senderNew, totalDeducted);
                                });
                            });
                        });
                    })
            );
        }).thenApply(response -> {
            if (!response.isSuccess()) clearCooldown(senderUuid);
            return response;
        });
    }

    // ==================== Upgrade Tier ====================

    public CompletableFuture<TransactionResponse> upgradeTier(Player player) {
        UUID uuid = player.getUniqueId();

        return executeWithLock(uuid, () ->
                getAccount(uuid).thenCompose(opt -> {
                    if (opt.isEmpty()) return fail(BankResult.ACCOUNT_NOT_FOUND);

                    BankAccount account = opt.get();
                    int currentTier = account.getTier();
                    int nextTier = currentTier + 1;

                    if (nextTier > config().getMaxTier()) return fail(BankResult.MAX_TIER_REACHED);

                    BankConfig.TierSettings nextSettings = config().getTier(nextTier);
                    double cost = nextSettings.getUpgradeCost();
                    int xpCost = nextSettings.getUpgradeXpCost();
                    double prevBalance = account.getBalance();

                    if (isTransactionCancelled(uuid, player.getName(),
                            TransactionLog.TransactionType.UPGRADE, cost, prevBalance)) {
                        return fail(BankResult.TRANSACTION_LOCKED);
                    }

                    // PlaceholderAPI requirements
                    TierRequirementService.RequirementCheckResult reqResult =
                            tierRequirementService.checkRequirements(player, nextSettings);
                    if (reqResult.hasFailed()) {
                        return CompletableFuture.completedFuture(
                                TransactionResponse.failureWithRequirements(
                                        BankResult.REQUIREMENTS_NOT_MET, reqResult.getFailedRequirements()));
                    }

                    if (!account.hasBalance(cost)) return fail(BankResult.INSUFFICIENT_MONEY_FOR_UPGRADE, prevBalance);
                    if (player.getTotalExperience() < xpCost) return fail(BankResult.INSUFFICIENT_XP_FOR_UPGRADE);

                    // Deduct money
                    return db().updateBalanceAtomic(uuid, -cost).thenCompose(newBalance -> {
                        if (newBalance < 0) {
                            plugin.getLogger().severe(String.format(
                                    "CRITICAL: Upgrade balance negative! UUID=%s, Prev=%.2f, Cost=%.2f, New=%.2f",
                                    uuid, prevBalance, cost, newBalance));
                            db().setBalance(uuid, prevBalance);
                            return fail(BankResult.INSUFFICIENT_MONEY_FOR_UPGRADE, prevBalance);
                        }

                        // Deduct XP on main thread
                        return supplyAsyncMain(() -> {
                            player.giveExp(-xpCost);
                            return true;
                        }).thenCompose(xpOk ->
                                db().updateTier(uuid, nextTier).thenCompose(success -> {
                                    if (!success) {
                                        // Rollback money + XP
                                        return db().updateBalanceAtomic(uuid, cost).thenCompose(rollback ->
                                                supplyAsyncMain(() -> {
                                                    player.giveExp(xpCost);
                                                    plugin.getLogger().warning("Tier update failed for " +
                                                            player.getName() + " - rolled back");
                                                    return TransactionResponse.failure(BankResult.DATABASE_ERROR);
                                                })
                                        );
                                    }

                                    commitTransaction(uuid, player.getName(),
                                            TransactionLog.TransactionType.UPGRADE,
                                            cost, prevBalance, newBalance,
                                            "Upgraded to " + nextSettings.getName());

                                    fireLevelUpEvent(uuid, player.getName(),
                                            currentTier, nextTier, nextSettings.getName(), cost);

                                    return succeed(prevBalance, newBalance, cost);
                                })
                        );
                    });
                })
        );
    }

    // ==================== Admin Operations ====================

    public CompletableFuture<TransactionResponse> adminGive(UUID targetUuid, String targetName,
                                                            double amount, String adminName) {
        return executeWithLock(targetUuid, () ->
                getAccount(targetUuid).thenCompose(opt -> {
                    if (opt.isEmpty()) return fail(BankResult.ACCOUNT_NOT_FOUND);

                    double prevBalance = opt.get().getBalance();

                    return db().updateBalanceAtomic(targetUuid, amount).thenApply(newBalance -> {
                        if (newBalance < 0) return TransactionResponse.failure(BankResult.DATABASE_ERROR);

                        commitAdminTransaction(targetUuid, targetName,
                                TransactionLog.TransactionType.ADMIN_GIVE,
                                amount, prevBalance, newBalance, adminName);

                        return TransactionResponse.success(prevBalance, newBalance, amount);
                    });
                })
        );
    }

    public CompletableFuture<TransactionResponse> adminTake(UUID targetUuid, String targetName,
                                                            double amount, String adminName) {
        return executeWithLock(targetUuid, () ->
                getAccount(targetUuid).thenCompose(opt -> {
                    if (opt.isEmpty()) return fail(BankResult.ACCOUNT_NOT_FOUND);

                    BankAccount account = opt.get();
                    double prevBalance = account.getBalance();
                    double actualAmount = Math.min(amount, prevBalance);

                    return db().updateBalanceAtomic(targetUuid, -actualAmount).thenApply(newBalance -> {
                        if (newBalance < 0) return TransactionResponse.failure(BankResult.DATABASE_ERROR);

                        commitAdminTransaction(targetUuid, targetName,
                                TransactionLog.TransactionType.ADMIN_TAKE,
                                actualAmount, prevBalance, newBalance, adminName);

                        return TransactionResponse.success(prevBalance, newBalance, actualAmount);
                    });
                })
        );
    }

    public CompletableFuture<TransactionResponse> adminSet(UUID targetUuid, String targetName,
                                                           double amount, String adminName) {
        return executeWithLock(targetUuid, () ->
                getAccount(targetUuid).thenCompose(opt -> {
                    if (opt.isEmpty()) return fail(BankResult.ACCOUNT_NOT_FOUND);

                    double prevBalance = opt.get().getBalance();

                    return db().setBalance(targetUuid, amount).thenApply(success -> {
                        if (!success) return TransactionResponse.failure(BankResult.DATABASE_ERROR);

                        commitAdminTransaction(targetUuid, targetName,
                                TransactionLog.TransactionType.ADMIN_SET,
                                amount, prevBalance, amount, adminName);

                        return TransactionResponse.success(prevBalance, amount, amount);
                    });
                })
        );
    }

    // ==================== History ====================

    public CompletableFuture<List<TransactionLog>> getHistory(UUID uuid, int limit) {
        return db().getTransactionHistory(uuid, limit);
    }

    // ==================== Locking Infrastructure ====================

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> executeWithLock(UUID uuid,
                                                     java.util.function.Supplier<CompletableFuture<T>> operation) {
        RedisLockService lockService = plugin.getRedisLockService();

        if (lockService == null || plugin.getRedisManager() == null || !plugin.getRedisManager().isConnected()) {
            return operation.get();
        }

        return lockService.withLockAsync(uuid, 5, TimeUnit.SECONDS, operation)
                .handle((result, error) -> {
                    if (error != null) {
                        if (error.getCause() instanceof TimeoutException || error.getMessage().contains("lock")) {
                            return (T) TransactionResponse.failure(BankResult.TRANSACTION_LOCKED);
                        }
                        plugin.getLogger().severe("Internal Transaction Error for " + uuid + ": " + error.getMessage());
                        error.printStackTrace();
                        return (T) TransactionResponse.failure(BankResult.DATABASE_ERROR);
                    }
                    return result;
                });
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> executeWithDualLock(UUID uuid1, UUID uuid2,
                                                         java.util.function.Supplier<CompletableFuture<T>> operation) {
        RedisLockService lockService = plugin.getRedisLockService();

        if (lockService == null || plugin.getRedisManager() == null || !plugin.getRedisManager().isConnected()) {
            return operation.get();
        }

        UUID firstLock = uuid1.compareTo(uuid2) < 0 ? uuid1 : uuid2;
        UUID secondLock = uuid1.compareTo(uuid2) < 0 ? uuid2 : uuid1;

        return lockService.withLockAsync(firstLock, 5, TimeUnit.SECONDS, () ->
                lockService.withLockAsync(secondLock, 5, TimeUnit.SECONDS, operation).toCompletableFuture()
        ).exceptionally(error -> (T) TransactionResponse.failure(BankResult.TRANSACTION_LOCKED));
    }

    private <T> CompletableFuture<T> supplyAsyncMain(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> applyInterestSafe(UUID uuid, double interest, double maxBalance) {
        return executeWithLock(uuid, () ->
                getAccount(uuid).thenCompose(opt -> {
                    if (opt.isEmpty()) return CompletableFuture.completedFuture(false);

                    BankAccount account = opt.get();
                    double currentBalance = account.getBalance();

                    double finalInterest = interest;
                    if (maxBalance >= 0) {
                        finalInterest = Math.min(interest, Math.max(0, maxBalance - currentBalance));
                        if (finalInterest <= 0) {
                            return CompletableFuture.completedFuture(false);
                        }
                    }

                    double actualInterest = finalInterest;
                    return db().updateBalanceAtomic(uuid, actualInterest).thenApply(newBalance -> {
                        if (newBalance < 0) return false;

                        commitTransaction(uuid, account.getPlayerName(),
                                TransactionLog.TransactionType.INTEREST,
                                actualInterest, currentBalance, newBalance,
                                "Interest payment");

                        return true;
                    });
                })
        );
    }
}