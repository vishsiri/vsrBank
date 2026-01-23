package dev.visherryz.plugins.vsrbank.gui;

import dev.triumphteam.gui.builder.item.PaperItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.model.TransactionLog;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.List;

/**
 * GUI for viewing transaction history with Pagination
 */
public class HistoryGui {

    private final VsrBank plugin;
    private final DecimalFormat currencyFormat;

    public HistoryGui(VsrBank plugin) {
        this.plugin = plugin;
        this.currencyFormat = new DecimalFormat("#,##0.00");
    }

    public void open(Player player) {
        // แก้ไข: ดึงข้อมูลมาเยอะๆ เพื่อให้มีข้อมูลสำหรับแบ่งหน้า (เช่น 100 รายการ)
        // หรือคุณอาจจะไปปรับค่า HistoryEntries ใน config ให้สูงขึ้นแทนก็ได้
        int limit = Math.max(100, plugin.getConfigManager().getConfig().getGui().getHistoryEntries());

        plugin.getBankService().getHistory(player.getUniqueId(), limit).thenAccept(logs -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                openGui(player, logs);
            });
        });
    }

    private void openGui(Player player, List<TransactionLog> logs) {
        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();

        PaginatedGui gui = Gui.paginated()
                .title(plugin.getMessageUtil().parse(plugin.getConfigManager().getConfig().getGui().getHistoryTitle()))
                .rows(6)
                .pageSize(18) // ใช้พื้นที่ Slot 0-17 (แถว 1-2)
                .disableAllInteractions() // ปิดการขยับของใน GUI ทั้งหมด
                .disableItemPlace()       // ป้องกันการวางของจากกระเป๋าตัวเองลงไป
                .disableItemSwap()        // ป้องกันการกดเลข 1-9 สลับของ หรือกด F (Offhand)
                .disableItemDrop()        // ป้องกันการกด Q โยนของทิ้ง
                .create();

        // --- ส่วนที่แก้ไข (Fix Error) ---
        // สร้าง Item สำหรับถมพื้นหลัง
        GuiItem filler = PaperItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .asGuiItem();

        // ห้ามใช้ .fill(filler); เด็ดขาดใน PaginatedGui

        // ให้ถมเฉพาะส่วนล่าง (แถวที่ 6)
        gui.getFiller().fillBottom(filler);

        // และถมพื้นที่ว่างตรงกลาง (แถวที่ 3 ถึง 5) ที่ไม่ได้ใช้แสดงรายการ
        // fillBetweenPoints(row1, col1, row2, col2)
        gui.getFiller().fillBetweenPoints(3, 1, 5, 9, filler);
        // -----------------------------

        if (logs.isEmpty()) {
            ItemStack emptyItem = PaperItemBuilder.from(Material.BARRIER)
                    .name(plugin.getMessageUtil().parse("<gray>No transaction history</gray>"))
                    .build();
            // วางไว้แถว 3 ตรงกลาง
            gui.setItem(3, 5, new GuiItem(emptyItem));
        } else {
            for (TransactionLog log : logs) {
                // ... (ส่วนการสร้าง Item เหมือนเดิม) ...
                Material material;
                try {
                    material = Material.valueOf(log.getType().getMaterial());
                } catch (IllegalArgumentException e) {
                    material = Material.PAPER;
                }

                String color = switch (log.getType()) {
                    case DEPOSIT, TRANSFER_IN, INTEREST, ADMIN_GIVE -> "<green>";
                    case WITHDRAW, TRANSFER_OUT, ADMIN_TAKE, FEE, UPGRADE -> "<red>";
                    default -> "<yellow>";
                };

                String amountPrefix = switch (log.getType()) {
                    case DEPOSIT, TRANSFER_IN, INTEREST, ADMIN_GIVE -> "+";
                    case WITHDRAW, TRANSFER_OUT, ADMIN_TAKE, FEE, UPGRADE -> "-";
                    default -> "";
                };

                ItemStack logItem = PaperItemBuilder.from(material)
                        .name(plugin.getMessageUtil().parse(color + log.getType().getIcon() + " " + log.getType().getDisplayName() + color.replace("<", "</")))
                        .lore(
                                plugin.getMessageUtil().parse("<gray>Amount: " + color + amountPrefix + symbol + currencyFormat.format(log.getAmount()) + color.replace("<", "</")),
                                plugin.getMessageUtil().parse("<gray>Balance: <white>" + symbol + currencyFormat.format(log.getBalanceAfter()) + "</white></gray>"),
                                log.getTargetName() != null ?
                                        plugin.getMessageUtil().parse("<gray>Player: <white>" + log.getTargetName() + "</white></gray>") :
                                        Component.empty(),
                                Component.empty(),
                                plugin.getMessageUtil().parse("<dark_gray>" + log.getFormattedDate() + "</dark_gray>")
                        )
                        .build();

                gui.addItem(new GuiItem(logItem));
            }
        }

        // ปุ่ม Previous (แถว 6 ช่อง 3)
        gui.setItem(6, 3, new GuiItem(PaperItemBuilder.from(Material.PAPER)
                .name(plugin.getMessageUtil().parse("<yellow>Previous Page</yellow>"))
                .build(), event -> {
            playClickSound(player);
            gui.previous();
        }));

        // ปุ่ม Next (แถว 6 ช่อง 7)
        gui.setItem(6, 7, new GuiItem(PaperItemBuilder.from(Material.PAPER)
                .name(plugin.getMessageUtil().parse("<yellow>Next Page</yellow>"))
                .build(), event -> {
            playClickSound(player);
            gui.next();
        }));

        // ปุ่ม Back (แถว 6 ช่อง 5)
        ItemStack backItem = PaperItemBuilder.from(Material.ARROW)
                .name(plugin.getMessageUtil().parse("<gray>Back to Menu</gray>"))
                .build();
        gui.setItem(6, 5, new GuiItem(backItem, event -> {
            playClickSound(player);
            new BankGui(plugin).open(player);
        }));

        gui.open(player);
    }

    private void playClickSound(Player player) {
        if (plugin.getConfigManager().getConfig().getGui().isEnableSounds()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}