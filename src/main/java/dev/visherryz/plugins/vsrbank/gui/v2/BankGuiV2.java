package dev.visherryz.plugins.vsrbank.gui.v2;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.gui.common.ButtonConfig;
import dev.visherryz.plugins.vsrbank.config.gui.BankGuiConfig;
import dev.visherryz.plugins.vsrbank.gui.handler.ButtonBuilder;
import dev.visherryz.plugins.vsrbank.gui.handler.ButtonHandler;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.Map;

/**
 * New BankGui implementation using type-based config system
 */
public class BankGuiV2 {

    private final VsrBank plugin;
    private final ButtonBuilder buttonBuilder;
    private final ButtonHandler buttonHandler;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    public BankGuiV2(VsrBank plugin) {
        this.plugin = plugin;
        this.buttonBuilder = new ButtonBuilder(plugin);
        this.buttonHandler = new ButtonHandler(plugin);
    }

    public void open(Player player) {
        plugin.getBankService().getOrCreateAccount(player.getUniqueId(), player.getName())
                .thenAccept(account -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> openGui(player, account)));
    }

    private void openGui(Player player, BankAccount account) {
        // เปลี่ยนจาก guiConfigManager เป็น configManager
        BankGuiConfig config = plugin.getConfigManager().getBankGui();

        Gui gui = Gui.gui()
                .title(plugin.getMessageUtil().parse(config.getTitle()))
                .rows(config.getRows())
                .disableAllInteractions()
                .create();

        // Fill with filler
        gui.getFiller().fill(buttonBuilder.buildFiller(config.getFiller()));

        // Player head
        setupPlayerHead(player, account, gui, config);

        // Withdraw section
        setupTransactionSection(player, account, gui, config.getWithdrawSection(), false);

        // Deposit section
        setupTransactionSection(player, account, gui, config.getDepositSection(), true);

        // Utility buttons
        setupButtons(player, account, gui, config);

        gui.open(player);
    }

    private void setupPlayerHead(Player player, BankAccount account, Gui gui, BankGuiConfig config) {
        var section = config.getPlayerHead();

        if (section.getSlot() == null || section.getSlot() == -1) {
            return;
        }

        var tier = plugin.getConfigManager().getConfig().getTier(account.getTier());

        Map<String, String> placeholders = buttonBuilder.placeholders()
                .add("player", player.getName())
                .add("balance", formatMoney(account.getBalance()))
                .add("tier", tier.getName())
                .add("interest", String.format("%.2fx", tier.getInterestMultiplier()))
                .build();

        var head = buttonBuilder.buildPlayerHead(
                player,
                section.getNameFormat(),
                section.getLore(),
                placeholders,
                section.getCustomModelData()
        );

        gui.setItem(section.getSlot(), new GuiItem(head));
    }

    private void setupTransactionSection(Player player, BankAccount account, Gui gui,
                                         BankGuiConfig.TransactionSection section, boolean isDeposit) {
        // Label - check slot before adding
        if (section.getLabel().getSlot() != -1) {
            GuiItem label = buttonBuilder.buildButton(section.getLabel(), account);
            gui.setItem(section.getLabel().getSlot(), label);
        }

        // Preset buttons
        var slots = section.getSlots();
        var amounts = section.getAmounts();
        var template = section.getPresetTemplate();

        for (int i = 0; i < Math.min(slots.size(), amounts.size()); i++) {
            GuiItem item = buttonBuilder.createPresetButton(template, amounts.get(i), account);
            gui.setItem(slots.get(i), item);
        }

        // Custom amount - check slot before adding
        if (section.getCustomButton().getSlot() != -1) {
            GuiItem custom = buttonBuilder.buildButton(section.getCustomButton(), account);
            gui.setItem(section.getCustomButton().getSlot(), custom);
        }

        // All in/out - check slot before adding
        if (section.getAllButton().getSlot() != -1) {
            double allAmount = isDeposit ?
                    plugin.getVaultHook().getEconomy().getBalance(player) :
                    account.getBalance();

            Map<String, String> placeholders = Map.of(
                    isDeposit ? "wallet" : "balance",
                    formatMoney(allAmount)
            );

            GuiItem all = buttonBuilder.buildButton(section.getAllButton(), account, placeholders);
            gui.setItem(section.getAllButton().getSlot(), all);
        }

        // Half button - check slot before adding
        if (section.getHalfButton() != null && section.getHalfButton().getSlot() != -1) {
            double sourceAmount = isDeposit ?
                    plugin.getVaultHook().getEconomy().getBalance(player) :
                    account.getBalance();

            double halfAmount = Math.floor(sourceAmount / 2.0);

            Map<String, String> placeholders = Map.of(
                    isDeposit ? "wallet" : "balance", formatMoney(sourceAmount),
                    "half_amount", formatMoney(halfAmount)
            );

            GuiItem half = buttonBuilder.buildButton(section.getHalfButton(), account, placeholders);
            gui.setItem(section.getHalfButton().getSlot(), half);
        }
    }

    private void setupButtons(Player player, BankAccount account, Gui gui, BankGuiConfig config) {
        for (ButtonConfig btnConfig : config.getButtons()) {
            // Skip if slot is -1 (hidden button)
            if (btnConfig.getSlot() == -1) {
                continue;
            }

            // Check condition
            if (!buttonHandler.shouldDisplay(btnConfig, account)) {
                continue;
            }

            GuiItem item = buttonBuilder.buildButton(btnConfig, account);
            gui.setItem(btnConfig.getSlot(), item);
        }
    }

    private String formatMoney(double amount) {
        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
        return symbol + currencyFormat.format(amount);
    }
}