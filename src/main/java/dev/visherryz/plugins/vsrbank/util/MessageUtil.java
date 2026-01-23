package dev.visherryz.plugins.vsrbank.util;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.MessagesConfig;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;

public class MessageUtil {

    private final VsrBank plugin;
    private final MiniMessage miniMessage;
    private final DecimalFormat currencyFormat;

    public MessageUtil(VsrBank plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.currencyFormat = new DecimalFormat("#,##0.00");
    }

    public void shutdown() {
    }

    private MessagesConfig getMessages() {
        return plugin.getConfigManager().getMessages();
    }

    public String formatCurrency(double amount) {
        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
        String format = plugin.getConfigManager().getConfig().getEconomy().getCurrencyFormat();
        String formatted = currencyFormat.format(amount);

        if ("SYMBOL_FIRST".equals(format)) {
            return symbol + formatted;
        } else {
            return formatted + symbol;
        }
    }

    private Audience getAudience(CommandSender sender) {
        return (Audience) sender;
    }

    /**
     * Helper to convert legacy {} placeholders to MiniMessage <> tags
     */
    private String convertPlaceholders(String message) {
        if (message == null) return "";
        // แปลง { เป็น < และ } เป็น > เพื่อให้ Config เก่าทำงานได้
        return message.replace("{", "<").replace("}", ">");
    }

    public void send(CommandSender sender, String message, TagResolver... resolvers) {
        if (message == null || message.isEmpty()) return;

        // แปลง Prefix และ Message ให้รองรับ {}
        String prefix = convertPlaceholders(getMessages().getPrefix());
        String msgContent = convertPlaceholders(message);

        String fullMessage = prefix + msgContent;

        // ส่งเข้า MiniMessage (ซึ่งตอนนี้เป็น <player> แล้ว จึงทำงานได้)
        Component component = miniMessage.deserialize(fullMessage, resolvers);
        getAudience(sender).sendMessage(component);
    }

    public void sendRaw(CommandSender sender, String message, TagResolver... resolvers) {
        if (message == null || message.isEmpty()) return;

        String msgContent = convertPlaceholders(message);
        Component component = miniMessage.deserialize(msgContent, resolvers);
        getAudience(sender).sendMessage(component);
    }

    public Component parse(String message, TagResolver... resolvers) {
        if (message == null) return Component.empty();
        String msgContent = convertPlaceholders(message);
        return miniMessage.deserialize(msgContent, resolvers);
    }

    // ==================== Specific Messages ====================
    // ส่วนด้านล่างนี้ไม่ต้องแก้ Logic อะไร เพราะเราแก้ที่ send() แล้วครับ

    public void sendNoPermission(CommandSender sender) {
        send(sender, getMessages().getNoPermission());
    }

    public void sendPlayerOnly(CommandSender sender) {
        send(sender, getMessages().getPlayerOnly());
    }

    public void sendPlayerNotFound(CommandSender sender, String playerName) {
        send(sender, getMessages().getPlayerNotFound(),
                Placeholder.unparsed("player", playerName));
    }

    public void sendInvalidAmount(CommandSender sender) {
        send(sender, getMessages().getInvalidAmount());
    }

    public void sendBalance(Player player, double balance) {
        send(player, getMessages().getBalanceDisplay(),
                Placeholder.unparsed("balance", formatCurrency(balance)));
    }

    public void sendBalanceOther(CommandSender sender, String playerName, double balance) {
        send(sender, getMessages().getBalanceOther(),
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("balance", formatCurrency(balance)));
    }

    public void sendDepositSuccess(Player player, double amount, double newBalance) {
        send(player, getMessages().getDepositSuccess(),
                Placeholder.unparsed("amount", formatCurrency(amount)),
                Placeholder.unparsed("balance", formatCurrency(newBalance)));
    }

    public void sendDepositFailed(Player player) {
        send(player, getMessages().getDepositFailed());
    }

    public void sendDepositMinimum(Player player, double min) {
        send(player, getMessages().getDepositMinimum(),
                Placeholder.unparsed("min", formatCurrency(min)));
    }

    public void sendMaxBalanceReached(Player player, double max) {
        send(player, getMessages().getMaxBalanceReached(),
                Placeholder.unparsed("max", formatCurrency(max)));
    }

    public void sendWithdrawSuccess(Player player, double amount, double newBalance) {
        send(player, getMessages().getWithdrawSuccess(),
                Placeholder.unparsed("amount", formatCurrency(amount)),
                Placeholder.unparsed("balance", formatCurrency(newBalance)));
    }

    public void sendWithdrawFailed(Player player) {
        send(player, getMessages().getWithdrawFailed());
    }

    public void sendWithdrawMinimum(Player player, double min) {
        send(player, getMessages().getWithdrawMinimum(),
                Placeholder.unparsed("min", formatCurrency(min)));
    }

    public void sendInsufficientFunds(Player player, double balance) {
        send(player, getMessages().getInsufficientFunds(),
                Placeholder.unparsed("balance", formatCurrency(balance)));
    }

    public void sendTransferSuccess(Player player, double amount, String targetName) {
        send(player, getMessages().getTransferSuccess(),
                Placeholder.unparsed("amount", formatCurrency(amount)),
                Placeholder.unparsed("player", targetName));
    }

    public void sendTransferReceived(Player player, String senderName, double amount) {
        send(player, getMessages().getTransferReceived(),
                Placeholder.unparsed("amount", formatCurrency(amount)),
                Placeholder.unparsed("player", senderName));
    }

    public void sendTransferFailed(Player player) {
        send(player, getMessages().getTransferFailed());
    }

    public void sendTransferToSelf(Player player) {
        send(player, getMessages().getTransferToSelf());
    }

    public void sendTransferMinimum(Player player, double min) {
        send(player, getMessages().getTransferMinimum(),
                Placeholder.unparsed("min", formatCurrency(min)));
    }

    public void sendTransferMaximum(Player player, double max) {
        send(player, getMessages().getTransferMaximum(),
                Placeholder.unparsed("max", formatCurrency(max)));
    }

    public void sendTransferFee(Player player, double fee) {
        send(player, getMessages().getTransferFee(),
                Placeholder.unparsed("fee", formatCurrency(fee)));
    }

    public void sendInterestReceived(Player player, double amount) {
        send(player, getMessages().getInterestReceived(),
                Placeholder.unparsed("amount", formatCurrency(amount)));
    }

    public void sendCurrentTier(Player player, String tierName, int level) {
        send(player, getMessages().getCurrentTier(),
                Placeholder.unparsed("tier", tierName),
                Placeholder.unparsed("level", String.valueOf(level)));
    }

    public void sendTierBenefits(Player player, double maxBalance, double rate) {
        String maxBalanceStr = maxBalance < 0 ? "Unlimited" : formatCurrency(maxBalance);
        send(player, getMessages().getTierBenefits(),
                Placeholder.unparsed("maxBalance", maxBalanceStr),
                Placeholder.unparsed("rate", String.format("%.2f", rate)));
    }

    public void sendUpgradeSuccess(Player player, String tierName) {
        send(player, getMessages().getUpgradeSuccess(),
                Placeholder.unparsed("tier", tierName));
    }

    public void sendUpgradeFailed(Player player) {
        send(player, getMessages().getUpgradeFailed());
    }

    public void sendUpgradeMaxTier(Player player) {
        send(player, getMessages().getUpgradeMaxTier());
    }

    public void sendUpgradeRequirements(Player player, double cost, int xp) {
        send(player, getMessages().getUpgradeRequirements(),
                Placeholder.unparsed("cost", formatCurrency(cost)),
                Placeholder.unparsed("xp", String.valueOf(xp)));
    }

    public void sendTransactionLocked(Player player) {
        send(player, getMessages().getTransactionLocked());
    }

    public void sendCooldownActive(Player player, double seconds) {
        send(player, getMessages().getCooldownActive(),
                Placeholder.unparsed("seconds", String.format("%.1f", seconds)));
    }

    public void sendAdminGive(CommandSender sender, String playerName, double amount) {
        send(sender, getMessages().getAdminGive(),
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("amount", formatCurrency(amount)));
    }

    public void sendAdminTake(CommandSender sender, String playerName, double amount) {
        send(sender, getMessages().getAdminTake(),
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("amount", formatCurrency(amount)));
    }

    public void sendAdminSet(CommandSender sender, String playerName, double amount) {
        send(sender, getMessages().getAdminSet(),
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("amount", formatCurrency(amount)));
    }

    public void sendDatabaseError(CommandSender sender) {
        send(sender, getMessages().getDatabaseError());
    }

    public void sendReloadSuccess(CommandSender sender) {
        send(sender, getMessages().getReloadSuccess());
    }

    public void sendNoAccount(Player player) {
        send(player, getMessages().getNoAccount());
    }

    public void sendTargetNoAccount(CommandSender sender, String playerName) {
        send(sender, getMessages().getTargetNoAccount(),
                Placeholder.unparsed("player", playerName));
    }

    public void sendHelp(CommandSender sender) {
        MessagesConfig messages = getMessages();
        sendRaw(sender, messages.getHelpHeader());
        sendRaw(sender, messages.getHelpBalance());
        sendRaw(sender, messages.getHelpDeposit());
        sendRaw(sender, messages.getHelpWithdraw());
        sendRaw(sender, messages.getHelpTransfer());
        sendRaw(sender, messages.getHelpUpgrade());
        sendRaw(sender, messages.getHelpHistory());
    }
}