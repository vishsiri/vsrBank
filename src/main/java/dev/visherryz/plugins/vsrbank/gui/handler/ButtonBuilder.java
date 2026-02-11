package dev.visherryz.plugins.vsrbank.gui.handler;

import dev.triumphteam.gui.builder.item.PaperItemBuilder;
import dev.triumphteam.gui.guis.GuiItem;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.MessagesConfig;
import dev.visherryz.plugins.vsrbank.gui.common.ButtonConfig;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.TransactionResponse;
import dev.visherryz.plugins.vsrbank.util.TransactionErrorHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ButtonBuilder {

    private final VsrBank plugin;
    private final ButtonHandler buttonHandler;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    public ButtonBuilder(VsrBank plugin) {
        this.plugin = plugin;
        this.buttonHandler = new ButtonHandler(plugin);
    }

    private MessagesConfig msg() {
        return plugin.getConfigManager().getMessages();
    }

    public GuiItem buildButton(ButtonConfig config, BankAccount account, Map<String, String> placeholders) {
        var builder = PaperItemBuilder.from(config.getMaterial());

        String name = replacePlaceholders(config.getName(), placeholders);
        if (!name.isEmpty()) {
            builder.name(plugin.getMessageUtil().parse(name));
        }

        if (config.getLore() != null && !config.getLore().isEmpty()) {
            List<Component> lore = config.getLore().stream()
                    .map(line -> replacePlaceholders(line, placeholders))
                    .filter(line -> !line.equals("%target_line%") || placeholders.containsKey("target"))
                    .map(plugin.getMessageUtil()::parse)
                    .collect(Collectors.toList());
            builder.lore(lore);
        }

        if (config.hasCustomModelData()) {
            builder.model(config.getCustomModelData());
        }

        GuiItem item = builder.asGuiItem();

        if (config.getType() != ButtonConfig.ButtonType.INFO &&
                config.getType() != ButtonConfig.ButtonType.FILLER) {
            Consumer<Player> action = buttonHandler.createAction(config, account);
            item.setAction(event -> {
                Player player = (Player) event.getWhoClicked();
                if (plugin.canClick(player)) {
                    playClickSound(player);
                    action.accept(player);
                }
            });
        }

        return item;
    }

    public GuiItem buildButton(ButtonConfig config, BankAccount account) {
        return buildButton(config, account, new HashMap<>());
    }

    public GuiItem buildFiller(ButtonConfig config) {
        if (config == null || config.getMaterial() == null ||
                config.getMaterial().name().equals("STONE")) {
            config = plugin.getConfigManager().getCommonGui().getDefaultFiller();
        }

        if (config.getMaterial() == Material.AIR) {
            return new GuiItem(new org.bukkit.inventory.ItemStack(Material.AIR));
        }

        return PaperItemBuilder.from(config.getMaterial())
                .name(Component.empty())
                .asGuiItem();
    }

    public ItemStack buildPlayerHead(Player player, String nameFormat, List<String> lore,
                                     Map<String, String> placeholders, Integer customModelData) {
        org.bukkit.inventory.ItemStack playerSkull = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) playerSkull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            playerSkull.setItemMeta(meta);
        }

        var builder = PaperItemBuilder.from(playerSkull);

        String name = replacePlaceholders(nameFormat, placeholders);
        if (!name.isEmpty()) {
            builder.name(plugin.getMessageUtil().parse(name));
        }

        if (lore != null && !lore.isEmpty()) {
            List<Component> loreParsed = lore.stream()
                    .map(line -> replacePlaceholders(line, placeholders))
                    .map(plugin.getMessageUtil()::parse)
                    .collect(Collectors.toList());
            builder.lore(loreParsed);
        }

        if (customModelData != null && customModelData > 0) {
            builder.model(customModelData);
        }

        return builder.build();
    }

    public ItemStack buildPlayerHead(Player player, String nameFormat, List<String> lore,
                                     Map<String, String> placeholders) {
        return buildPlayerHead(player, nameFormat, lore, placeholders, null);
    }

    public GuiItem createPresetButton(ButtonConfig template, double amount, BankAccount account) {
        Map<String, String> placeholders = placeholders()
                .add("amount", formatMoney(amount))
                .build();

        var builder = PaperItemBuilder.from(template.getMaterial());

        String name = template.getName().isEmpty() ?
                formatMoney(amount) :
                replacePlaceholders(template.getName(), placeholders);
        builder.name(plugin.getMessageUtil().parse(name));

        if (template.getLore() != null && !template.getLore().isEmpty()) {
            List<Component> lore = template.getLore().stream()
                    .map(line -> replacePlaceholders(line, placeholders))
                    .map(plugin.getMessageUtil()::parse)
                    .collect(Collectors.toList());
            builder.lore(lore);
        }

        if (template.hasCustomModelData()) {
            builder.model(template.getCustomModelData());
        }

        GuiItem item = builder.asGuiItem();

        Consumer<Player> action = player -> {
            switch (template.getType()) {
                case DEPOSIT -> {
                    plugin.getBankService().deposit(player, amount, "Deposit")
                            .thenAccept(response -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (response.isSuccess()) {
                                    plugin.getMessageUtil().sendDepositSuccess(player, amount, response.getNewBalance());
                                    new dev.visherryz.plugins.vsrbank.gui.v2.BankGuiV2(plugin).open(player);
                                } else {
                                    handleTransactionError(player, response);
                                }
                            }));
                }
                case WITHDRAW -> {
                    plugin.getBankService().withdraw(player, amount, "Withdraw")
                            .thenAccept(response -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (response.isSuccess()) {
                                    // BUG FIX: was sendDepositSuccess, now correctly sendWithdrawSuccess
                                    plugin.getMessageUtil().sendWithdrawSuccess(player, amount, response.getNewBalance());
                                    new dev.visherryz.plugins.vsrbank.gui.v2.BankGuiV2(plugin).open(player);
                                } else {
                                    handleTransactionError(player, response);
                                }
                            }));
                }
            }
        };

        item.setAction(event -> {
            Player player = (Player) event.getWhoClicked();
            if (plugin.canClick(player)) {
                playClickSound(player);
                action.accept(player);
            }
        });

        return item;
    }

    public PlaceholderBuilder placeholders() {
        return new PlaceholderBuilder();
    }

    public static class PlaceholderBuilder {
        private final Map<String, String> map = new HashMap<>();

        public PlaceholderBuilder add(String key, String value) {
            map.put(key, value);
            return this;
        }

        public Map<String, String> build() {
            return map;
        }
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null) {
            return text != null ? text : "";
        }

        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return result;
    }

    private String formatMoney(double amount) {
        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
        return symbol + currencyFormat.format(amount);
    }

    private void handleTransactionError(Player player, TransactionResponse response) {
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        new TransactionErrorHandler(plugin).handle(player, response);
    }

    private void playClickSound(Player player) {
        if (plugin.getConfigManager().getCommonGui().isEnableSounds()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}