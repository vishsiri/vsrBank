package dev.visherryz.plugins.vsrbank.gui.v2;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.config.MessagesConfig;
import dev.visherryz.plugins.vsrbank.config.gui.UpgradeGuiConfig;
import dev.visherryz.plugins.vsrbank.gui.handler.ButtonBuilder;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Upgrade GUI V2 - Using new config system
 */
public class UpgradeGuiV2 {

    private final VsrBank plugin;
    private final ButtonBuilder buttonBuilder;
    private final BankAccount account;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    public UpgradeGuiV2(VsrBank plugin, BankAccount account) {
        this.plugin = plugin;
        this.buttonBuilder = new ButtonBuilder(plugin);
        this.account = account;
    }

    private MessagesConfig msg() {
        return plugin.getConfigManager().getMessages();
    }

    public void open(Player player) {
        UpgradeGuiConfig config = plugin.getConfigManager().getUpgradeGui();
        BankConfig bankConfig = plugin.getConfigManager().getConfig();

        int nextTier = account.getTier() + 1;

        // Check if can upgrade
        if (nextTier > bankConfig.getMaxTier()) {
            new BankGuiV2(plugin).open(player);
            return;
        }

        BankConfig.TierSettings nextTierSettings = bankConfig.getTier(nextTier);

        Gui gui = Gui.gui()
                .title(plugin.getMessageUtil().parse(
                        config.getTitle().replace("%tier%", String.valueOf(nextTier))))
                .rows(config.getRows())
                .disableAllInteractions()
                .create();

        // Filler
        gui.getFiller().fill(buttonBuilder.buildFiller(config.getFiller()));

        // Info item - only show if slot is configured
        if (config.getInfoDisplay().getSlot() != -1) {
            gui.setItem(config.getInfoDisplay().getSlot(),
                    buildInfoItem(nextTierSettings, config.getInfoDisplay()));
        }

        // Setup buttons
        setupButtons(player, gui, config, nextTierSettings);

        gui.open(player);
    }

    private GuiItem buildInfoItem(BankConfig.TierSettings tierSettings,
                                  UpgradeGuiConfig.UpgradeInfoDisplay display) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tier_name", tierSettings.getName());
        placeholders.put("cost", formatMoney(tierSettings.getUpgradeCost()));
        placeholders.put("xp", String.valueOf(tierSettings.getUpgradeXpCost()));
        placeholders.put("interest", String.format("%.2fx", tierSettings.getInterestMultiplier()));
        placeholders.put("max_balance", tierSettings.getMaxBalance() < 0
                ? "Unlimited"
                : formatMoney(tierSettings.getMaxBalance()));

        var builder = dev.triumphteam.gui.builder.item.PaperItemBuilder.from(display.getMaterial())
                .name(plugin.getMessageUtil().parse(
                        replacePlaceholders(display.getNameFormat(), placeholders)));

        for (String line : display.getLore()) {
            String processed = replacePlaceholders(line, placeholders);
            if (!processed.isEmpty()) {
                builder.lore(plugin.getMessageUtil().parse(processed));
            }
        }

        if (display.getCustomModelData() != null && display.getCustomModelData() > 0) {
            builder.model(display.getCustomModelData());
        }

        return builder.asGuiItem();
    }

    private void setupButtons(Player player, Gui gui, UpgradeGuiConfig config,
                              BankConfig.TierSettings nextTierSettings) {
        for (var btnConfig : config.getButtons()) {
            // Skip if slot is -1 (hidden button)
            if (btnConfig.getSlot() == -1) {
                continue;
            }

            GuiItem item;

            // Handle confirm button specially
            if (btnConfig.getType() == dev.visherryz.plugins.vsrbank.gui.common.ButtonConfig.ButtonType.CONFIRM) {
                item = buttonBuilder.buildButton(btnConfig, account);
                // Override action
                item.setAction(event -> {
                    Player p = (Player) event.getWhoClicked();
                    if (plugin.canClick(p)) {
                        playClickSound(p);
                        gui.close(p);
                        processUpgrade(p, nextTierSettings);
                    }
                });
            } else {
                item = buttonBuilder.buildButton(btnConfig, account);
            }

            gui.setItem(btnConfig.getSlot(), item);
        }
    }

    private void processUpgrade(Player player, BankConfig.TierSettings nextTierSettings) {
        plugin.getMessageUtil().sendRaw(player, msg().getTransactionLocked());

        plugin.getBankService().upgradeTier(player).thenAccept(response ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (response.isSuccess()) {
                        plugin.getMessageUtil().sendUpgradeSuccess(player, nextTierSettings.getName());
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        new BankGuiV2(plugin).open(player);
                    } else {
                        handleUpgradeError(player, response);
                        new BankGuiV2(plugin).open(player);
                    }
                })
        );
    }

    private void handleUpgradeError(Player player,
                                    dev.visherryz.plugins.vsrbank.model.TransactionResponse response) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        switch (response.getResult()) {
            case INSUFFICIENT_MONEY_FOR_UPGRADE ->
                    plugin.getMessageUtil().send(player, msg().getUpgradeNotEnoughMoney()
                            .replace("{cost}", formatMoney(
                                    plugin.getConfigManager().getConfig()
                                            .getTier(account.getTier() + 1).getUpgradeCost())));

            case INSUFFICIENT_XP_FOR_UPGRADE ->
                    plugin.getMessageUtil().send(player, msg().getUpgradeNotEnoughXp()
                            .replace("{xp}", String.valueOf(
                                    plugin.getConfigManager().getConfig()
                                            .getTier(account.getTier() + 1).getUpgradeXpCost())));

            case MAX_TIER_REACHED ->
                    plugin.getMessageUtil().send(player, msg().getUpgradeMaxTier());

            default ->
                    plugin.getMessageUtil().send(player, msg().getUpgradeFailed());
        }
    }

    private String formatMoney(double amount) {
        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
        return symbol + currencyFormat.format(amount);
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null) {
            return text;
        }

        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return result;
    }

    private void playClickSound(Player player) {
        if (plugin.getConfigManager().getCommonGui().isEnableSounds()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}