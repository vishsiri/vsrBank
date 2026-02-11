package dev.visherryz.plugins.vsrbank.gui.handler;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.MessagesConfig;
import dev.visherryz.plugins.vsrbank.gui.ChatInputHandler;
import dev.visherryz.plugins.vsrbank.gui.common.ButtonConfig;
import dev.visherryz.plugins.vsrbank.gui.v2.BankGuiV2;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.gui.v2.HistoryGuiV2;
import dev.visherryz.plugins.vsrbank.gui.v2.TransferGuiV2;
import dev.visherryz.plugins.vsrbank.gui.v2.UpgradeGuiV2;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.TransactionResponse;
import dev.visherryz.plugins.vsrbank.util.TransactionErrorHandler;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.function.Consumer;

/**
 * Handles button actions based on ButtonType
 * Updated to use V2 GUIs and support all button types
 * All messages are now pulled from MessagesConfig
 */
public class ButtonHandler {

    private final VsrBank plugin;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    public ButtonHandler(VsrBank plugin) {
        this.plugin = plugin;
    }

    private MessagesConfig msg() {
        return plugin.getConfigManager().getMessages();
    }

    /**
     * Create action handler for button based on its type
     */
    public Consumer<Player> createAction(ButtonConfig config, BankAccount account) {
        return switch (config.getType()) {
            case CLOSE -> Player::closeInventory;
            case BACK -> player -> openGui(player, config.getTargetGui());
            case OPEN_GUI -> player -> handleOpenGui(player, config);
            case DEPOSIT -> player -> handleDeposit(player, config.getAmount());
            case WITHDRAW -> player -> handleWithdraw(player, config.getAmount(), account);
            case DEPOSIT_ALL -> this::handleDepositAll;
            case WITHDRAW_ALL -> player -> handleWithdrawAll(player, account);
            case DEPOSIT_HALF -> this::handleDepositHalf;
            case WITHDRAW_HALF -> player -> handleWithdrawHalf(player, account);
            case CUSTOM_AMOUNT -> player -> handleCustomAmount(player, config.getAction());
            case TRANSFER_CUSTOM -> this::handleTransferCustom;
            case UPGRADE_TIER -> player -> handleUpgradeTier(player, account);
            case CANCEL -> player -> handleCancel(player, config.getTargetGui());
            case CUSTOM_COMMAND -> player -> handleCustomCommand(player, config);
            default -> player -> {};
        };
    }

    // ===== NAVIGATION HANDLERS =====

    private void handleOpenGui(Player player, ButtonConfig config) {
        if (checkPermission(player, config)) {
            openGui(player, config.getTargetGui());
        } else {
            playErrorSound(player);
            plugin.getMessageUtil().send(player, msg().getNoPermission());
        }
    }

    private void handleCancel(Player player, String targetGui) {
        if (targetGui != null && !targetGui.isEmpty()) {
            openGui(player, targetGui);
        } else {
            player.closeInventory();
        }
    }

    // ===== TRANSACTION HANDLERS =====

    private void handleDeposit(Player player, double amount) {
        if (amount > 0) {
            processDeposit(player, amount);
        }
    }

    private void handleWithdraw(Player player, double amount, BankAccount account) {
        if (amount > 0) {
            processWithdraw(player, amount);
        }
    }

    private void handleDepositAll(Player player) {
        double wallet = plugin.getVaultHook().getEconomy().getBalance(player);
        if (wallet > 0) {
            processDeposit(player, wallet);
        } else {
            playErrorSound(player);
            plugin.getMessageUtil().send(player, msg().getWalletEmpty());
        }
    }

    private void handleWithdrawAll(Player player, BankAccount account) {
        if (account != null && account.getBalance() > 0) {
            processWithdraw(player, account.getBalance());
        } else {
            playErrorSound(player);
            plugin.getMessageUtil().send(player, msg().getBankEmpty());
        }
    }

    private void handleDepositHalf(Player player) {
        double wallet = plugin.getVaultHook().getEconomy().getBalance(player);
        if (wallet <= 0) {
            playErrorSound(player);
            plugin.getMessageUtil().send(player, msg().getWalletEmpty());
            return;
        }

        double halfAmount = Math.floor(wallet / 2.0);
        double minDeposit = plugin.getConfigManager().getConfig().getTransaction().getMinDepositAmount();

        if (halfAmount < minDeposit) {
            playErrorSound(player);
            plugin.getMessageUtil().send(player,
                    msg().getDepositMinimum().replace("{min}", formatMoney(minDeposit)));
            return;
        }

        processDeposit(player, halfAmount);
    }

    private void handleWithdrawHalf(Player player, BankAccount account) {
        if (account == null || account.getBalance() <= 0) {
            playErrorSound(player);
            plugin.getMessageUtil().send(player, msg().getBankEmpty());
            return;
        }

        double halfAmount = Math.floor(account.getBalance() / 2.0);
        double minWithdraw = plugin.getConfigManager().getConfig().getTransaction().getMinWithdrawAmount();

        if (halfAmount < minWithdraw) {
            playErrorSound(player);
            plugin.getMessageUtil().send(player,
                    msg().getWithdrawMinimum().replace("{min}", formatMoney(minWithdraw)));
            return;
        }

        processWithdraw(player, halfAmount);
    }

    private void handleCustomAmount(Player player, String action) {
        player.closeInventory();
        new ChatInputHandler(plugin).requestInput(player, action);
    }

    // ===== TRANSFER HANDLERS =====

    private void handleTransferCustom(Player player) {
        player.closeInventory();
        new ChatInputHandler(plugin).requestTransferPlayer(player);
    }

    // ===== UPGRADE HANDLERS =====

    private void handleUpgradeTier(Player player, BankAccount account) {
        if (account == null) return;

        int nextTier = account.getTier() + 1;
        int maxTier = plugin.getConfigManager().getConfig().getMaxTier();

        if (nextTier <= maxTier) {
            new UpgradeGuiV2(plugin, account).open(player);
        } else {
            playErrorSound(player);
            plugin.getMessageUtil().send(player, msg().getUpgradeMaxTier());
        }
    }

    // ===== CUSTOM COMMAND HANDLER =====

    private void handleCustomCommand(Player player, ButtonConfig config) {
        if (config.getCommand().isEmpty()) return;

        if (checkPermission(player, config)) {
            player.performCommand(config.getCommand());
        } else {
            playErrorSound(player);
            plugin.getMessageUtil().send(player, msg().getNoPermission());
        }
    }

    // ===== CORE TRANSACTION PROCESSING =====

    private void processDeposit(Player player, double amount) {
        plugin.getBankService().deposit(player, amount, "GUI deposit")
                .thenAccept(response -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (response.isSuccess()) {
                        plugin.getMessageUtil().sendDepositSuccess(player, amount, response.getNewBalance());
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        new BankGuiV2(plugin).open(player);
                    } else {
                        handleTransactionError(player, response);
                    }
                }));
    }

    private void processWithdraw(Player player, double amount) {
        plugin.getBankService().withdraw(player, amount, "GUI withdraw")
                .thenAccept(response -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (response.isSuccess()) {
                        plugin.getMessageUtil().sendWithdrawSuccess(player, amount, response.getNewBalance());
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                        new BankGuiV2(plugin).open(player);
                    } else {
                        handleTransactionError(player, response);
                    }
                }));
    }

    private void handleTransactionError(Player player, TransactionResponse response) {
        playErrorSound(player);
        new TransactionErrorHandler(plugin).handle(player, response);
    }

    // ===== GUI NAVIGATION =====

    private void openGui(Player player, String guiName) {
        if (guiName == null || guiName.isEmpty()) {
            player.closeInventory();
            return;
        }

        switch (guiName.toLowerCase()) {
            case "bank", "main" -> new BankGuiV2(plugin).open(player);
            case "transfer" -> new TransferGuiV2(plugin).open(player);
            case "upgrade" -> openUpgradeGui(player);
            case "history" -> new HistoryGuiV2(plugin).open(player);
            default -> player.closeInventory();
        }
    }

    private void openUpgradeGui(Player player) {
        plugin.getBankService().getAccount(player.getUniqueId())
                .thenAccept(optAccount -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (optAccount.isPresent()) {
                        new UpgradeGuiV2(plugin, optAccount.get()).open(player);
                    } else {
                        player.closeInventory();
                    }
                }));
    }

    // ===== CONDITION EVALUATION =====

    public boolean shouldDisplay(ButtonConfig config, BankAccount account) {
        if (config.getShowWhen().isEmpty()) {
            return true;
        }
        return evaluateCondition(config.getShowWhen(), account);
    }

    private boolean evaluateCondition(String condition, BankAccount account) {
        if (condition == null || condition.isEmpty() || account == null) {
            return true;
        }

        condition = condition.toLowerCase().trim();
        int currentTier = account.getTier();
        int maxTier = plugin.getConfigManager().getConfig().getMaxTier();
        double balance = account.getBalance();

        // Tier comparisons
        if (condition.contains("tier")) {
            return evaluateTierCondition(condition, currentTier, maxTier);
        }

        // Balance comparisons
        if (condition.contains("balance")) {
            return evaluateBalanceCondition(condition, balance);
        }

        return true;
    }

    private boolean evaluateTierCondition(String condition, int currentTier, int maxTier) {
        // Max tier comparisons
        if (condition.contains("< max_tier")) return currentTier < maxTier;
        if (condition.contains("<= max_tier")) return currentTier <= maxTier;
        if (condition.contains(">= max_tier")) return currentTier >= maxTier;
        if (condition.contains("> max_tier")) return currentTier > maxTier;
        if (condition.contains("== max_tier")) return currentTier == maxTier;

        // Numeric comparisons
        try {
            if (condition.contains(">=")) {
                int value = Integer.parseInt(condition.split(">=")[1].trim());
                return currentTier >= value;
            }
            if (condition.contains("<=")) {
                int value = Integer.parseInt(condition.split("<=")[1].trim());
                return currentTier <= value;
            }
            if (condition.contains(">")) {
                int value = Integer.parseInt(condition.split(">")[1].trim());
                return currentTier > value;
            }
            if (condition.contains("<")) {
                int value = Integer.parseInt(condition.split("<")[1].trim());
                return currentTier < value;
            }
            if (condition.contains("==")) {
                int value = Integer.parseInt(condition.split("==")[1].trim());
                return currentTier == value;
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}

        return true;
    }

    private boolean evaluateBalanceCondition(String condition, double balance) {
        try {
            if (condition.contains(">=")) {
                double value = Double.parseDouble(condition.split(">=")[1].trim());
                return balance >= value;
            }
            if (condition.contains("<=")) {
                double value = Double.parseDouble(condition.split("<=")[1].trim());
                return balance <= value;
            }
            if (condition.contains(">")) {
                double value = Double.parseDouble(condition.split(">")[1].trim());
                return balance > value;
            }
            if (condition.contains("<")) {
                double value = Double.parseDouble(condition.split("<")[1].trim());
                return balance < value;
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}

        return true;
    }

    // ===== UTILITY METHODS =====

    private boolean checkPermission(Player player, ButtonConfig config) {
        if (config.getRequirePermission().isEmpty()) {
            return true;
        }
        return player.hasPermission(config.getRequirePermission());
    }

    private String formatMoney(double amount) {
        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
        return symbol + currencyFormat.format(amount);
    }

    private void playErrorSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
}