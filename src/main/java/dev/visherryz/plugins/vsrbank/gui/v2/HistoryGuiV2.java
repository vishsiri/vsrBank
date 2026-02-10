package dev.visherryz.plugins.vsrbank.gui.v2;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.gui.HistoryGuiConfig;
import dev.visherryz.plugins.vsrbank.gui.handler.ButtonBuilder;
import dev.visherryz.plugins.vsrbank.model.TransactionLog;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * History GUI V2 - Using new config system
 */
public class HistoryGuiV2 {

    private final VsrBank plugin;
    private final ButtonBuilder buttonBuilder;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    public HistoryGuiV2(VsrBank plugin) {
        this.plugin = plugin;
        this.buttonBuilder = new ButtonBuilder(plugin);
    }

    public void open(Player player) {
        HistoryGuiConfig config = plugin.getConfigManager().getHistoryGui();
        int limit = config.getMaxEntries();

        plugin.getBankService().getHistory(player.getUniqueId(), limit)
                .thenAccept(logs -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> openGui(player, logs)));
    }

    private void openGui(Player player, List<TransactionLog> logs) {
        HistoryGuiConfig config = plugin.getConfigManager().getHistoryGui();

        PaginatedGui gui = Gui.paginated()
                .title(plugin.getMessageUtil().parse(config.getTitle()))
                .rows(config.getRows())
                .pageSize(config.getPageSize())
                .disableAllInteractions()
                .create();

        // Filler
        GuiItem filler = getFiller(config);

        if (config.getFillerSettings().isFillBottom()) {
            gui.getFiller().fillBottom(filler);
        }

        var fillerArea = config.getFillerSettings().getMiddleArea();
        gui.getFiller().fillBetweenPoints(
                fillerArea.getStartRow(), fillerArea.getStartCol(),
                fillerArea.getEndRow(), fillerArea.getEndCol(),
                filler
        );

        // Transaction logs
        if (logs.isEmpty()) {
            // Only show empty item if slot is configured
            if (config.getEmptyItem().getSlot() != -1) {
                gui.setItem(config.getEmptyItem().getSlot(),
                        buttonBuilder.buildButton(config.getEmptyItem(), null));
            }
        } else {
            for (TransactionLog log : logs) {
                gui.addItem(buildLogItem(log, config.getLogFormat()));
            }
        }

        // Navigation buttons
        setupButtons(player, gui, config);

        gui.open(player);
    }

    private GuiItem buildLogItem(TransactionLog log, HistoryGuiConfig.TransactionLogFormat format) {
        Material material;
        try {
            material = Material.valueOf(log.getType().getMaterial());
        } catch (IllegalArgumentException e) {
            material = Material.PAPER;
        }

        String typeKey = log.getType().name();
        String color = format.getTypeColors().getOrDefault(typeKey, "<yellow>");
        String amountPrefix = format.getAmountPrefixes().getOrDefault(typeKey, "");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("color", color);
        placeholders.put("amount_prefix", amountPrefix);
        placeholders.put("amount", formatMoney(log.getAmount()));
        placeholders.put("balance", formatMoney(log.getBalanceAfter()));
        placeholders.put("date", log.getFormattedDate());
        placeholders.put("target", log.getTargetName() != null ? log.getTargetName() : "");

        // Target line (only if target exists)
        String targetLine = "";
        if (log.getTargetName() != null) {
            targetLine = replacePlaceholders(format.getTargetLineFormat(), placeholders);
        }
        placeholders.put("target_line", targetLine);

        var builder = dev.triumphteam.gui.builder.item.PaperItemBuilder.from(material)
                .name(plugin.getMessageUtil().parse(
                        color + log.getType().getIcon() + " " + log.getType().getDisplayName()
                ));

        for (String line : format.getLore()) {
            String processed = replacePlaceholders(line, placeholders);
            // Skip empty lines and %target_line% if no target
            if (!processed.isEmpty() && !processed.equals("%target_line%")) {
                builder.lore(plugin.getMessageUtil().parse(processed));
            }
        }

        return builder.asGuiItem();
    }

    private void setupButtons(Player player, PaginatedGui gui, HistoryGuiConfig config) {
        for (var btnConfig : config.getButtons()) {
            // Skip if slot is -1 (hidden button)
            if (btnConfig.getSlot() == -1) {
                continue;
            }

            GuiItem item = buttonBuilder.buildButton(btnConfig, null);

            // Override action for pagination buttons
            switch (btnConfig.getType()) {
                case PREVIOUS_PAGE -> {
                    item.setAction(event -> {
                        if (plugin.canClick(player)) {
                            playClickSound(player);
                            gui.previous();
                        }
                    });
                }
                case NEXT_PAGE -> {
                    item.setAction(event -> {
                        if (plugin.canClick(player)) {
                            playClickSound(player);
                            gui.next();
                        }
                    });
                }
            }

            gui.setItem(btnConfig.getSlot(), item);
        }
    }

    private GuiItem getFiller(HistoryGuiConfig config) {
        var customFiller = config.getFillerSettings().getCustomFiller();

        if (customFiller != null && customFiller.getMaterial() != null &&
                !customFiller.getMaterial().name().equals("STONE")) {
            return buttonBuilder.buildFiller(customFiller);
        }

        return buttonBuilder.buildFiller(
                plugin.getConfigManager().getCommonGui().getDefaultFiller()
        );
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
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}