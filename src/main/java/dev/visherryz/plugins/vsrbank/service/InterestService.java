package dev.visherryz.plugins.vsrbank.service;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.TransactionLog;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service for calculating and applying interest to bank accounts
 */
@RequiredArgsConstructor
public class InterestService {

    private final VsrBank plugin;

    /**
     * Calculate and apply interest to all eligible accounts
     * Called periodically by the scheduler
     */
    public CompletableFuture<Integer> processInterest() {
        BankConfig.InterestSettings settings = plugin.getConfigManager().getConfig().getInterest();

        if (!settings.isEnabled()) {
            return CompletableFuture.completedFuture(0);
        }

        return plugin.getDatabaseManager().getProvider()
                .getAccountsEligibleForInterest(settings.getMinBalanceForInterest())
                .thenApply(accounts -> {
                    int processed = 0;

                    for (BankAccount account : accounts) {
                        try {
                            double interest = calculateInterest(account, settings);

                            if (interest > 0) {
                                applyInterest(account, interest);
                                processed++;
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Failed to process interest for " + account.getPlayerName(), e);
                        }
                    }

                    plugin.getLogger().info("Processed interest for " + processed + " accounts");
                    return processed;
                });
    }

    /**
     * Calculate interest for a single account
     */
    private double calculateInterest(BankAccount account, BankConfig.InterestSettings settings) {
        double balance = account.getBalance();

        // Determine rate based on online status
        Player player = Bukkit.getPlayer(account.getUuid());
        boolean isOnline = player != null && player.isOnline();

        double baseRate = isOnline ? settings.getBaseRate() : settings.getOfflineRate();

        // Apply tier multiplier
        BankConfig.TierSettings tier = plugin.getConfigManager().getConfig().getTier(account.getTier());
        double multiplier = tier.getInterestMultiplier();

        double interest = balance * baseRate * multiplier;

        // Cap at maximum
        interest = Math.min(interest, settings.getMaxInterestPerCycle());

        return interest;
    }

    /**
     * Apply interest to an account
     * FIXED: Now respects max balance limit (standard banking behavior)
     */
    private void applyInterest(BankAccount account, double interest) {
        BankConfig.TierSettings tier = plugin.getConfigManager().getConfig().getTier(account.getTier());
        double maxBalance = tier.getMaxBalance();

        plugin.getBankService().applyInterestSafe(account.getUuid(), interest, maxBalance)
                .thenAccept(success -> {
                    if (success) {
                        Player player = Bukkit.getPlayer(account.getUuid());
                        if (player != null && player.isOnline()) {
                            plugin.getMessageUtil().sendInterestReceived(player, interest);
                        }
                    }
                });
    }

    /**
     * Get next interest time for a player
     */
    public Instant getNextInterestTime(BankAccount account) {
        int intervalMinutes = plugin.getConfigManager().getConfig().getInterest().getIntervalMinutes();
        return account.getLastInterestTime().plusSeconds(intervalMinutes * 60L);
    }
}