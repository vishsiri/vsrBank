package dev.visherryz.plugins.vsrbank.gui;

import dev.triumphteam.gui.builder.item.PaperItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.visherryz.plugins.vsrbank.VsrBank;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.DecimalFormat;
import java.util.Collection;

/**
 * GUI for transferring money to other players
 */
public class TransferGui {

    private final VsrBank plugin;
    private final DecimalFormat currencyFormat;

    public TransferGui(VsrBank plugin) {
        this.plugin = plugin;
        this.currencyFormat = new DecimalFormat("#,##0.00");
    }

    public void open(Player player) {
        Gui gui = Gui.gui()
                .title(plugin.getMessageUtil().parse(plugin.getConfigManager().getConfig().getGui().getTransferTitle()))
                .rows(4)
                .disableAllInteractions() // ปิดการขยับของใน GUI ทั้งหมด
                .disableItemPlace()       // ป้องกันการวางของจากกระเป๋าตัวเองลงไป
                .disableItemSwap()        // ป้องกันการกดเลข 1-9 สลับของ หรือกด F (Offhand)
                .disableItemDrop()        // ป้องกันการกด Q โยนของทิ้ง
                .create();

        // Fill background
        GuiItem filler = PaperItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .asGuiItem();
        gui.getFiller().fill(filler);

        // Online players
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int slot = 10;
        int maxSlots = 16; // Row 2: slots 10-16

        for (Player target : onlinePlayers) {
            if (target.equals(player)) continue;
            if (slot > maxSlots) break;

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                playerHead.setItemMeta(meta);
            }

            ItemStack headItem = PaperItemBuilder.from(playerHead)
                    .name(plugin.getMessageUtil().parse("<yellow>" + target.getName() + "</yellow>"))
                    .lore(
                            plugin.getMessageUtil().parse("<gray>Click to transfer</gray>"),
                            plugin.getMessageUtil().parse("<gray>money to this player</gray>")
                    )
                    .build();

            gui.setItem(slot, new GuiItem(headItem, event -> {
                playClickSound(player);
                gui.close(player);
                requestTransferAmount(player, target.getUniqueId(), target.getName());
            }));
            slot++;
        }

        // Custom player input
        ItemStack customItem = PaperItemBuilder.from(Material.NAME_TAG)
                .name(plugin.getMessageUtil().parse("<gold><bold>Enter Player Name</bold></gold>"))
                .lore(
                        plugin.getMessageUtil().parse("<gray>Click to type a player name</gray>"),
                        plugin.getMessageUtil().parse("<gray>Works for offline players too!</gray>")
                )
                .build();
        gui.setItem(22, new GuiItem(customItem, event -> {
            playClickSound(player);
            gui.close(player);
            new ChatInputHandler(plugin).requestTransferPlayer(player);
        }));

        // Back button
        ItemStack backItem = PaperItemBuilder.from(Material.ARROW)
                .name(plugin.getMessageUtil().parse("<gray>Back</gray>"))
                .build();
        gui.setItem(27, new GuiItem(backItem, event -> {
            playClickSound(player);
            new BankGui(plugin).open(player);
        }));

        // Info
        plugin.getBankService().getAccount(player.getUniqueId()).thenAccept(optAccount -> {
            optAccount.ifPresent(account -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
                    ItemStack infoItem = PaperItemBuilder.from(Material.GOLD_INGOT)
                            .name(plugin.getMessageUtil().parse("<gold>Your Bank Balance</gold>"))
                            .lore(
                                    plugin.getMessageUtil().parse("<white>" + symbol + currencyFormat.format(account.getBalance()) + "</white>")
                            )
                            .build();
                    gui.updateItem(4, new GuiItem(infoItem));
                });
            });
        });

        gui.open(player);
    }

    /**
     * Request transfer amount via chat
     */
    public void requestTransferAmount(Player sender, java.util.UUID targetUuid, String targetName) {
        new ChatInputHandler(plugin).requestTransferAmount(sender, targetUuid, targetName);
    }

    private void playClickSound(Player player) {
        if (plugin.getConfigManager().getConfig().getGui().isEnableSounds()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}