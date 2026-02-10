package dev.visherryz.plugins.vsrbank.command;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.gui.v2.BankGuiV2;
import dev.visherryz.plugins.vsrbank.gui.ChatInputHandler;
import dev.visherryz.plugins.vsrbank.gui.v2.HistoryGuiV2;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.BankResult;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.*;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command({"bank", "b", "banking"})
public class BankCommand {

    private final VsrBank plugin;

    public BankCommand(VsrBank plugin) {
        this.plugin = plugin;
    }

    @Command({"bank", "b", "banking"})
    @CommandPermission("vsrbank.use")
    public void openBank(Player player) {
        new BankGuiV2(plugin).open(player);
    }

    @Subcommand("help")
    @CommandPermission("vsrbank.use")
    public void help(CommandSender sender) {
        plugin.getMessageUtil().sendHelp(sender);
    }

    @Subcommand("balance")
    @CommandPermission("vsrbank.use")
    public void balance(Player player) {
        plugin.getBankService().getAccount(player.getUniqueId()).thenAccept(opt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt.isPresent()) {
                    plugin.getMessageUtil().sendBalance(player, opt.get().getBalance());
                } else {
                    plugin.getMessageUtil().sendNoAccount(player);
                }
            });
        }).exceptionally(ex -> {
            ex.printStackTrace();
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageUtil().sendDatabaseError(player));
            return null;
        });;
    }

    @Subcommand("deposit")
    @CommandPermission("vsrbank.deposit")
    public void deposit(Player player,
                        @Optional Double amount) {
        if (amount == null) {
            new ChatInputHandler(plugin).requestInput(player, "deposit");
            return;
        }
        if (amount <= 0) { plugin.getMessageUtil().sendInvalidAmount(player); return; }

        plugin.getBankService().deposit(player, amount, null).thenAccept(r -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (r.isSuccess()) plugin.getMessageUtil().sendDepositSuccess(player, amount, r.getNewBalance());
                else handleError(player, r.getResult());
            });
        }).exceptionally(ex -> {
            ex.printStackTrace();
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageUtil().sendDatabaseError(player));
            return null;
        });
    }

    @Subcommand("withdraw")
    @CommandPermission("vsrbank.withdraw")
    public void withdraw(Player player,
                         @Optional Double amount) {
        if (amount == null) {
            new ChatInputHandler(plugin).requestInput(player, "withdraw");
            return;
        }
        if (amount <= 0) { plugin.getMessageUtil().sendInvalidAmount(player); return; }

        plugin.getBankService().withdraw(player, amount, null).thenAccept(r -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (r.isSuccess()) plugin.getMessageUtil().sendWithdrawSuccess(player, amount, r.getNewBalance());
                else handleError(player, r.getResult());
            });
        }).exceptionally(ex -> {
            ex.printStackTrace();
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageUtil().sendDatabaseError(player));
            return null;
        });;
    }

    @Subcommand("transfer")
    @CommandPermission("vsrbank.transfer")
    public void transfer(Player player,
                         OfflinePlayer target,
                         double amount) {
        if (amount <= 0) { plugin.getMessageUtil().sendInvalidAmount(player); return; }

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getMessageUtil().sendPlayerNotFound(player, target.getName());
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.getMessageUtil().sendTransferToSelf(player);
            return;
        }

        plugin.getBankService().transfer(player, target.getUniqueId(), target.getName(), amount, null)
                .thenAccept(r -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (r.isSuccess()) {
                        plugin.getMessageUtil().sendTransferSuccess(player, amount, target.getName());
                        if (r.getFee() > 0) plugin.getMessageUtil().sendTransferFee(player, r.getFee());
                    } else handleError(player, r.getResult());
                })).exceptionally(ex -> {
                    ex.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getMessageUtil().sendDatabaseError(player));
                    return null;
                });;
    }

    @Subcommand("upgrade")
    @CommandPermission("vsrbank.upgrade")
    public void upgrade(Player player) {
        new BankGuiV2(plugin).open(player);
    }

    @Subcommand("history")
    @CommandPermission("vsrbank.history")
    public void history(Player player) {
        new HistoryGuiV2(plugin).open(player);
    }

    // ==================== Admin Commands ====================

    @Subcommand("admin give")
    @CommandPermission("vsrbank.admin.give")
    public void adminGive(CommandSender sender,
                          OfflinePlayer target,
                          double amount) {
        if (amount <= 0) { plugin.getMessageUtil().sendInvalidAmount(sender); return; }

        processAdminAction(sender, target, amount, "give");
    }

    @Subcommand("admin take")
    @CommandPermission("vsrbank.admin.take")
    public void adminTake(CommandSender sender,
                          OfflinePlayer target,
                          double amount) {
        if (amount <= 0) { plugin.getMessageUtil().sendInvalidAmount(sender); return; }

        processAdminAction(sender, target, amount, "take");
    }

    @Subcommand("admin set")
    @CommandPermission("vsrbank.admin.set")
    public void adminSet(CommandSender sender,
                         OfflinePlayer target,
                         double amount) {
        if (amount < 0) { plugin.getMessageUtil().sendInvalidAmount(sender); return; }

        processAdminAction(sender, target, amount, "set");
    }

    @Subcommand("admin view")
    @CommandPermission("vsrbank.admin.view")
    public void adminView(CommandSender sender,
                          OfflinePlayer target) {
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getMessageUtil().sendPlayerNotFound(sender, target.getName());
            return;
        }

        plugin.getBankService().getAccount(target.getUniqueId()).thenAccept(opt -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (opt.isPresent()) {
                BankAccount acc = opt.get();
                plugin.getMessageUtil().sendBalanceOther(sender, target.getName(), acc.getBalance());
            } else plugin.getMessageUtil().sendTargetNoAccount(sender, target.getName());
        }));
    }

    @Subcommand("admin reload")
    @CommandPermission("vsrbank.admin.reload")
    public void adminReload(CommandSender sender) {
        plugin.closeOpenMenus();
        plugin.getConfigManager().reloadConfigs();
        plugin.getMessageUtil().sendReloadSuccess(sender);
    }

    // --- Helpers ---

    private void processAdminAction(CommandSender sender, OfflinePlayer target, double amount, String action) {
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getMessageUtil().sendPlayerNotFound(sender, target.getName());
            return;
        }

        String admin = sender instanceof Player p ? p.getName() : "Console";
        var future = switch (action) {
            case "give" -> plugin.getBankService().adminGive(target.getUniqueId(), target.getName(), amount, admin);
            case "take" -> plugin.getBankService().adminTake(target.getUniqueId(), target.getName(), amount, admin);
            case "set" -> plugin.getBankService().adminSet(target.getUniqueId(), target.getName(), amount, admin);
            default -> throw new IllegalStateException("Unexpected value: " + action);
        };

        future.thenAccept(r -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (r.isSuccess()) {
                switch (action) {
                    case "give" -> plugin.getMessageUtil().sendAdminGive(sender, target.getName(), amount);
                    case "take" -> plugin.getMessageUtil().sendAdminTake(sender, target.getName(), r.getProcessedAmount()); // ใช้ processed amount เผื่อหักไม่หมด
                    case "set" -> plugin.getMessageUtil().sendAdminSet(sender, target.getName(), amount);
                }
            } else {
                plugin.getMessageUtil().sendDatabaseError(sender);
            }
        }));
    }

    private void handleError(Player player, BankResult result) {
        switch (result) {
            case INSUFFICIENT_FUNDS -> plugin.getMessageUtil().sendInsufficientFunds(player, 0);
            case MAX_BALANCE_REACHED -> plugin.getMessageUtil().sendMaxBalanceReached(player, 0);
            case COOLDOWN_ACTIVE -> plugin.getMessageUtil().sendCooldownActive(player, plugin.getBankService().getRemainingCooldown(player.getUniqueId()));
            case TRANSACTION_LOCKED -> plugin.getMessageUtil().sendTransactionLocked(player);
            default -> plugin.getMessageUtil().sendDatabaseError(player);
        }
    }
}