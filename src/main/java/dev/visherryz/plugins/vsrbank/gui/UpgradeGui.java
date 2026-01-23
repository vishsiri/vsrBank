package dev.visherryz.plugins.vsrbank.gui;

import dev.triumphteam.gui.builder.item.PaperItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;

public class UpgradeGui {

    private final VsrBank plugin;
    private final BankAccount account;
    private final DecimalFormat currencyFormat;

    public UpgradeGui(VsrBank plugin, BankAccount account) {
        this.plugin = plugin;
        this.account = account;
        this.currencyFormat = new DecimalFormat("#,##0.00");
    }

    public void open(Player player) {
        BankConfig config = plugin.getConfigManager().getConfig();
        int nextTier = account.getTier() + 1;

        // Safety Check: ถ้าตันแล้วไม่ต้องเปิดหน้านี้ ให้เปิดหน้าหลักเลย
        if (nextTier > config.getMaxTier()) {
            new BankGui(plugin).open(player);
            return;
        }

        BankConfig.TierSettings nextTierSettings = config.getTier(nextTier);
        String symbol = config.getEconomy().getCurrencySymbol();

        Gui gui = Gui.gui()
                .title(plugin.getMessageUtil().parse("<yellow>Confirm Upgrade: Tier " + nextTier + "</yellow>"))
                .rows(3)
                .disableAllInteractions() // ปิดการขยับของใน GUI ทั้งหมด
                .disableItemPlace()       // ป้องกันการวางของจากกระเป๋าตัวเองลงไป
                .disableItemSwap()        // ป้องกันการกดเลข 1-9 สลับของ หรือกด F (Offhand)
                .disableItemDrop()        // ป้องกันการกด Q โยนของทิ้ง
                .create();

        // Filler
        GuiItem filler = PaperItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .asGuiItem();
        gui.getFiller().fill(filler);

        // --- Info Item (Center) ---
        ItemStack infoItem = PaperItemBuilder.from(Material.NETHER_STAR)
                .name(plugin.getMessageUtil().parse("<gold><bold>Upgrade to " + nextTierSettings.getName() + "</bold></gold>"))
                .lore(
                        Component.empty(),
                        plugin.getMessageUtil().parse("<gray>Cost: <red>" + symbol + currencyFormat.format(nextTierSettings.getUpgradeCost()) + "</red></gray>"),
                        plugin.getMessageUtil().parse("<gray>XP Required: <green>" + nextTierSettings.getUpgradeXpCost() + " Levels</green></gray>"),
                        Component.empty(),
                        plugin.getMessageUtil().parse("<gray>Benefits:</gray>"),
                        plugin.getMessageUtil().parse("<gray>• Interest: <green>" + String.format("%.2fx", nextTierSettings.getInterestMultiplier()) + "</green></gray>"),
                        plugin.getMessageUtil().parse("<gray>• Max Balance: <green>" + currencyFormat.format(nextTierSettings.getMaxBalance()) + "</green></gray>")
                )
                .build();
        gui.setItem(13, new GuiItem(infoItem));

        // --- Confirm Button (Left) ---
        ItemStack confirmItem = PaperItemBuilder.from(Material.LIME_WOOL)
                .name(plugin.getMessageUtil().parse("<green><bold>CONFIRM UPGRADE</bold></green>"))
                .lore(plugin.getMessageUtil().parse("<gray>Click to purchase</gray>"))
                .build();

        gui.setItem(11, new GuiItem(confirmItem, event -> {
            playClickSound(player);

            // ป้องกันการกดรัว: ปิด GUI ก่อนเริ่ม Process
            gui.close(player);
            plugin.getMessageUtil().sendRaw(player, "<yellow>Processing upgrade...</yellow>");

            plugin.getBankService().upgradeTier(player).thenAccept(response -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (response.isSuccess()) {
                        plugin.getMessageUtil().sendUpgradeSuccess(player, nextTierSettings.getName());
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                        // เปิดหน้าหลักใหม่เพื่อให้เห็น Tier ล่าสุด
                        new BankGui(plugin).open(player);
                    } else {
                        // ส่งข้อความ error
                        switch (response.getResult()) {
                            case INSUFFICIENT_MONEY_FOR_UPGRADE -> plugin.getMessageUtil().send(player, "<red>Insufficient funds for upgrade!</red>");
                            case INSUFFICIENT_XP_FOR_UPGRADE -> plugin.getMessageUtil().send(player, "<red>Insufficient XP levels!</red>");
                            default -> plugin.getMessageUtil().sendUpgradeFailed(player);
                        }
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

                        // เปิดหน้าหลักกลับมา (หรือจะเปิดหน้า Upgrade เดิมก็ได้)
                        new BankGui(plugin).open(player);
                    }
                });
            });
        }));

        // --- Cancel Button (Right) ---
        ItemStack cancelItem = PaperItemBuilder.from(Material.RED_WOOL)
                .name(plugin.getMessageUtil().parse("<red><bold>CANCEL</bold></red>"))
                .lore(plugin.getMessageUtil().parse("<gray>Return to menu</gray>"))
                .build();

        gui.setItem(15, new GuiItem(cancelItem, event -> {
            playClickSound(player);
            new BankGui(plugin).open(player); // กลับหน้าหลัก
        }));

        gui.open(player);
    }

    private void playClickSound(Player player) {
        if (plugin.getConfigManager().getConfig().getGui().isEnableSounds()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}