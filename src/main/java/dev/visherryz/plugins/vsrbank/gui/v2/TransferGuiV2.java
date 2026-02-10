package dev.visherryz.plugins.vsrbank.gui.v2;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.MessagesConfig;
import dev.visherryz.plugins.vsrbank.config.gui.TransferGuiConfig;
import dev.visherryz.plugins.vsrbank.gui.ChatInputHandler;
import dev.visherryz.plugins.vsrbank.gui.handler.ButtonBuilder;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Map;

/**
 * Transfer GUI V2 - Using new config system
 */
public class TransferGuiV2 {

    private final VsrBank plugin;
    private final ButtonBuilder buttonBuilder;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    public TransferGuiV2(VsrBank plugin) {
        this.plugin = plugin;
        this.buttonBuilder = new ButtonBuilder(plugin);
    }

    private MessagesConfig msg() {
        return plugin.getConfigManager().getMessages();
    }

    public void open(Player player) {
        plugin.getBankService().getAccount(player.getUniqueId())
                .thenAccept(optAccount -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> {
                            if (optAccount.isPresent()) {
                                openGui(player, optAccount.get());
                            } else {
                                player.closeInventory();
                                plugin.getMessageUtil().send(player, msg().getNoAccount());
                            }
                        }));
    }

    private void openGui(Player player, BankAccount account) {
        TransferGuiConfig config = plugin.getConfigManager().getTransferGui();

        Gui gui = Gui.gui()
                .title(plugin.getMessageUtil().parse(config.getTitle()))
                .rows(config.getRows())
                .disableAllInteractions()
                .create();

        // Filler
        gui.getFiller().fill(buttonBuilder.buildFiller(config.getFiller()));

        // Balance info
        setupBalanceInfo(player, account, gui, config);

        // Online players
        setupOnlinePlayers(player, gui, config);

        // Custom buttons
        setupButtons(player, account, gui, config);

        gui.open(player);
    }

    private void setupBalanceInfo(Player player, BankAccount account, Gui gui, TransferGuiConfig config) {
        // Only show balance info if slot is configured
        if (config.getBalanceInfo().getSlot() == -1) {
            return;
        }

        Map<String, String> placeholders = buttonBuilder.placeholders()
                .add("balance", formatMoney(account.getBalance()))
                .build();

        GuiItem infoItem = buttonBuilder.buildButton(config.getBalanceInfo(), account, placeholders);
        gui.setItem(config.getBalanceInfo().getSlot(), infoItem);
    }

    private void setupOnlinePlayers(Player player, Gui gui, TransferGuiConfig config) {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        var playerSlots = config.getPlayerSlots();
        int slotIndex = 0;

        for (Player target : onlinePlayers) {
            // Skip self
            if (target.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            // Check if we have slots left
            if (slotIndex >= playerSlots.size()) {
                break;
            }

            // Create player head
            ItemStack playerHead = buttonBuilder.buildPlayerHead(
                    target,
                    config.getPlayerHeadDisplay().getNameFormat(),
                    config.getPlayerHeadDisplay().getLore(),
                    Map.of("player", target.getName()),
                    config.getPlayerHeadDisplay().getCustomModelData()
            );

            GuiItem headItem = new GuiItem(playerHead, event -> {
                Player clicker = (Player) event.getWhoClicked();
                if (plugin.canClick(clicker)) {
                    playClickSound(clicker);
                    gui.close(clicker);
                    new ChatInputHandler(plugin).requestTransferAmount(
                            clicker,
                            target.getUniqueId(),
                            target.getName()
                    );
                }
            });

            gui.setItem(playerSlots.get(slotIndex), headItem);
            slotIndex++;
        }
    }

    private void setupButtons(Player player, BankAccount account, Gui gui, TransferGuiConfig config) {
        for (var btnConfig : config.getButtons()) {
            // Skip if slot is -1 (hidden button)
            if (btnConfig.getSlot() == -1) {
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

    private void playClickSound(Player player) {
        if (plugin.getConfigManager().getCommonGui().isEnableSounds()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}