package dev.visherryz.plugins.vsrbank.hook;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * PlaceholderAPI expansion for VsrBank
 */
public class PlaceholderExpansionHook extends PlaceholderExpansion {

    private final VsrBank plugin;
    private final DecimalFormat currencyFormat;
    private final DecimalFormat simpleFormat;

    public PlaceholderExpansionHook(VsrBank plugin) {
        this.plugin = plugin;
        this.currencyFormat = new DecimalFormat("#,##0.00");
        this.simpleFormat = new DecimalFormat("#,##0");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vsrbank";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Visherryz";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        // Get account data (cached or async with timeout)
        BankAccount account = getAccountSync(player);

        if (account == null) {
            return getDefaultValue(params);
        }

        BankConfig config = plugin.getConfigManager().getConfig();
        String symbol = config.getEconomy().getCurrencySymbol();

        return switch (params.toLowerCase()) {
            case "balance" -> String.valueOf(account.getBalance());
            case "balance_formatted" -> symbol + currencyFormat.format(account.getBalance());
            case "balance_short" -> symbol + formatShort(account.getBalance());
            case "balance_raw" -> currencyFormat.format(account.getBalance());

            case "level", "tier" -> String.valueOf(account.getTier());
            case "tier_name" -> config.getTier(account.getTier()).getName();
            case "tier_max_balance" -> {
                double max = config.getTier(account.getTier()).getMaxBalance();
                yield max < 0 ? "Unlimited" : symbol + currencyFormat.format(max);
            }
            case "tier_interest_rate" -> String.format("%.2fx", config.getTier(account.getTier()).getInterestMultiplier());

            case "interest_total" -> symbol + currencyFormat.format(account.getTotalInterestEarned());
            case "interest_next" -> formatTimeUntilInterest(account);

            case "max_balance" -> {
                double max = config.getTier(account.getTier()).getMaxBalance();
                yield max < 0 ? "∞" : symbol + currencyFormat.format(max);
            }

            default -> null;
        };
    }

    /**
     * Get account synchronously with timeout
     */
    private BankAccount getAccountSync(OfflinePlayer player) {
        try {
            CompletableFuture<java.util.Optional<BankAccount>> future =
                    plugin.getDatabaseManager().getProvider().getAccount(player.getUniqueId());

            return future.get(100, TimeUnit.MILLISECONDS).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get default value for placeholder when account not found
     */
    private String getDefaultValue(String params) {
        return switch (params.toLowerCase()) {
            case "balance", "balance_raw" -> "0";
            case "balance_formatted", "balance_short" ->
                    plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol() + "0";
            case "level", "tier" -> "0";
            case "tier_name" -> "None";
            case "tier_max_balance", "max_balance" -> "N/A";
            case "tier_interest_rate" -> "0x";
            case "interest_total" ->
                    plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol() + "0";
            case "interest_next" -> "N/A";
            default -> "";
        };
    }

    /**
     * Format balance in short form (e.g., 1.5K, 2.3M)
     */
    private String formatShort(double amount) {
        if (amount < 1000) {
            return simpleFormat.format(amount);
        } else if (amount < 1000000) {
            return String.format("%.1fK", amount / 1000);
        } else if (amount < 1000000000) {
            return String.format("%.1fM", amount / 1000000);
        } else {
            return String.format("%.1fB", amount / 1000000000);
        }
    }

    /**
     * Format time until next interest
     */
    private String formatTimeUntilInterest(BankAccount account) {
        int intervalMinutes = plugin.getConfigManager().getConfig().getInterest().getIntervalMinutes();
        Instant nextInterest = account.getLastInterestTime().plusSeconds(intervalMinutes * 60L);
        Duration duration = Duration.between(Instant.now(), nextInterest);

        if (duration.isNegative()) {
            return "Soon";
        }

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm", minutes);
        } else {
            return "< 1m";
        }
    }
}