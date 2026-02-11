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
import java.util.List;
import java.util.Map;

/**
 * Bank GUI V2 — supports multi-slot buttons via getEffectiveSlots()
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

        // 1. เช็คว่ามีช่องที่ต้องวางไอเทมไหม (รองรับทั้ง slot และ slots)
        List<Integer> effectiveSlots = section.getEffectiveSlots();
        if (effectiveSlots.isEmpty()) {
            return;
        }

        // 2. เตรียมข้อมูล Placeholders
        var tier = plugin.getConfigManager().getConfig().getTier(account.getTier());
        var maxBalance = tier.getMaxBalance();

        Map<String, String> placeholders = buttonBuilder.placeholders()
                .add("player", player.getName())
                .add("balance", formatMoney(account.getBalance()))
                .add("tier", tier.getName())
                .add("max_balance", maxBalance < 0 ? "Unlimited" : formatMoney(maxBalance))
                .add("interest", String.format("%.2fx", tier.getInterestMultiplier()))
                .build();

        // 3. สร้างไอเทม Head
        var head = buttonBuilder.buildPlayerHead(
                player,
                section.getNameFormat(),
                section.getLore(),
                placeholders,
                section.getCustomModelData()
        );

        // 4. วนลูปวางไอเทมลงในทุกช่องที่กำหนดไว้
        for (int s : effectiveSlots) {
            gui.setItem(s, new GuiItem(head));
        }
    }

    private void setupTransactionSection(Player player, BankAccount account, Gui gui,
                                         BankGuiConfig.TransactionSection section, boolean isDeposit) {
        // Label — multi-slot
        if (section.getLabel() != null && section.getLabel().isVisible()) {
            GuiItem label = buttonBuilder.buildButton(section.getLabel(), account);
            for (int s : section.getLabel().getEffectiveSlots()) {
                gui.setItem(s, label);
            }
        }

        // Preset buttons
        var slots = section.getSlots();
        var amounts = section.getAmounts();
        var template = section.getPresetTemplate();

        for (int i = 0; i < Math.min(slots.size(), amounts.size()); i++) {
            GuiItem item = buttonBuilder.createPresetButton(template, amounts.get(i), account);
            gui.setItem(slots.get(i), item);
        }

        // Custom amount — multi-slot
        if (section.getCustomButton() != null && section.getCustomButton().isVisible()) {
            GuiItem custom = buttonBuilder.buildButton(section.getCustomButton(), account);
            for (int s : section.getCustomButton().getEffectiveSlots()) {
                gui.setItem(s, custom);
            }
        }

        // All in/out — multi-slot
        if (section.getAllButton() != null && section.getAllButton().isVisible()) {
            double allAmount = isDeposit ?
                    plugin.getVaultHook().getEconomy().getBalance(player) :
                    account.getBalance();

            Map<String, String> placeholders = Map.of(
                    isDeposit ? "wallet" : "balance",
                    formatMoney(allAmount)
            );

            GuiItem all = buttonBuilder.buildButton(section.getAllButton(), account, placeholders);
            for (int s : section.getAllButton().getEffectiveSlots()) {
                gui.setItem(s, all);
            }
        }

        // Half button — multi-slot
        if (section.getHalfButton() != null && section.getHalfButton().isVisible()) {
            double sourceAmount = isDeposit ?
                    plugin.getVaultHook().getEconomy().getBalance(player) :
                    account.getBalance();

            double halfAmount = Math.floor(sourceAmount / 2.0);

            Map<String, String> placeholders = Map.of(
                    isDeposit ? "wallet" : "balance", formatMoney(sourceAmount),
                    "half_amount", formatMoney(halfAmount)
            );

            GuiItem half = buttonBuilder.buildButton(section.getHalfButton(), account, placeholders);
            for (int s : section.getHalfButton().getEffectiveSlots()) {
                gui.setItem(s, half);
            }
        }
    }

    private void setupButtons(Player player, BankAccount account, Gui gui, BankGuiConfig config) {
        for (ButtonConfig btnConfig : config.getButtons()) {
            // Skip hidden buttons
            if (btnConfig.isHidden()) {
                continue;
            }

            // Check condition
            if (!buttonHandler.shouldDisplay(btnConfig, account)) {
                continue;
            }

            GuiItem item = buttonBuilder.buildButton(btnConfig, account);

            // Place on all effective slots
            for (int s : btnConfig.getEffectiveSlots()) {
                gui.setItem(s, item);
            }
        }
    }

    private String formatMoney(double amount) {
        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
        return symbol + currencyFormat.format(amount);
    }
}