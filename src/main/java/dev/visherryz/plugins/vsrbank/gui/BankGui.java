package dev.visherryz.plugins.vsrbank.gui;

import dev.triumphteam.gui.builder.item.PaperItemBuilder;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Consumer;

public class BankGui {

    private final VsrBank plugin;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,##0.00");

    public BankGui(VsrBank plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        plugin.getBankService().getOrCreateAccount(player.getUniqueId(), player.getName())
                .thenAccept(account -> plugin.getServer().getScheduler().runTask(plugin, () -> openGui(player, account)));
    }

    private void openGui(Player player, BankAccount account) {
        BankConfig config = plugin.getConfigManager().getConfig();
        String symbol = config.getEconomy().getCurrencySymbol();

        Gui gui = Gui.gui()
                .title(plugin.getMessageUtil().parse(config.getGui().getMainTitle()))
                .rows(6)
                .disableAllInteractions().disableItemPlace().disableItemSwap().disableItemDrop()
                .create();

        // 1. Setup Background & Tracking
        gui.getFiller().fill(createItem(Material.GRAY_STAINED_GLASS_PANE, "", null, null)); // Filler ไม่มี Action


        // 2. Header Info (Player Head)
        setupHeader(player, account, config, gui);

        // 3. Withdraw Section (Row 2)
        setupTransactionSection(player, gui, false, account, symbol,
                new int[]{10, 11, 12, 13, 14, 15}, 16, 17);

        // 4. Deposit Section (Row 4)
        setupTransactionSection(player, gui, true, account, symbol,
                new int[]{28, 29, 30, 31, 32, 33}, 34, 35);

        // 5. Utilities (Row 6)
        setupUtilities(player, gui, account, config);

        gui.open(player);
    }

    // --- Helper Sections ---

    private void setupHeader(Player player, BankAccount account, BankConfig config, Gui gui) {
        BankConfig.TierSettings tier = config.getTier(account.getTier());
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) { meta.setOwningPlayer(player); head.setItemMeta(meta); }

        gui.setItem(4, new GuiItem(PaperItemBuilder.from(head)
                .name(plugin.getMessageUtil().parse("<gold><bold>" + player.getName() + "'s Account</bold></gold>"))
                .lore(
                        plugin.getMessageUtil().parse("<gray>Balance: <white>" + config.getEconomy().getCurrencySymbol() + currencyFormat.format(account.getBalance()) + "</white></gray>"),
                        plugin.getMessageUtil().parse("<gray>Tier: <yellow>" + tier.getName() + "</yellow></gray>"),
                        plugin.getMessageUtil().parse("<gray>Interest: <green>" + String.format("%.2fx", tier.getInterestMultiplier()) + "</green></gray>")
                ).build()));
    }

    private void setupTransactionSection(Player player, Gui gui, boolean isDeposit, BankAccount account, String symbol, int[] presetSlots, int customSlot, int allSlot) {
        String actionName = isDeposit ? "Deposit" : "Withdraw";
        String color = isDeposit ? "<green>" : "<red>";
        Material paneMat = isDeposit ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        Material labelMat = isDeposit ? Material.LIME_WOOL : Material.RED_WOOL;

        // Label
        gui.setItem(presetSlots[0] - 1, createItem(labelMat, color + "<bold>" + actionName.toUpperCase() + "</bold>", List.of("<gray>" + (isDeposit ? "Put money in" : "Take money out") + "</gray>"), null));

        // Presets
        List<Double> presets = plugin.getConfigManager().getConfig().getGui().getPresetAmounts();
        for (int i = 0; i < Math.min(presets.size(), presetSlots.length); i++) {
            double amount = presets.get(i);
            gui.setItem(presetSlots[i], createItem(paneMat, color + symbol + currencyFormat.format(amount),
                    List.of("<gray>Click to " + actionName + "</gray>"),
                    p -> processTransaction(p, amount, isDeposit, gui)));
        }

        // Custom
        gui.setItem(customSlot, createItem(Material.OAK_SIGN, "<yellow>Custom</yellow>", List.of("<gray>Type amount to " + actionName.toLowerCase() + "</gray>"), p -> {
            gui.close(p);
            new ChatInputHandler(plugin).requestInput(p, actionName.toLowerCase());
        }));

        // All
        double allAmount = isDeposit ? plugin.getVaultHook().getEconomy().getBalance(player) : account.getBalance();
        gui.setItem(allSlot, createItem(isDeposit ? Material.CHEST : Material.HOPPER,
                color + actionName + " All",
                List.of("<gray>" + (isDeposit ? "Wallet" : "Balance") + ": " + symbol + currencyFormat.format(allAmount) + "</gray>"),
                p -> {
                    if (allAmount > 0) processTransaction(p, allAmount, isDeposit, gui);
                }));
    }

    private void setupUtilities(Player player, Gui gui, BankAccount account, BankConfig config) {
        // Transfer
        gui.setItem(48, createItem(Material.ENDER_PEARL, "<aqua><bold>Transfer</bold></aqua>", List.of("<gray>Send money to others</gray>"),
                p -> new TransferGui(plugin).open(p)));

        // Upgrade
        int nextTier = account.getTier() + 1;
        boolean canUpgrade = nextTier <= config.getMaxTier();
        gui.setItem(49, createItem(canUpgrade ? Material.NETHER_STAR : Material.BARRIER,
                "<light_purple><bold>Upgrade Tier</bold></light_purple>",
                List.of(canUpgrade ? "<gray>Click to view requirements</gray>" : "<red>Max tier reached</red>"),
                p -> { if (canUpgrade) new UpgradeGui(plugin, account).open(p); }));

        // History
        gui.setItem(50, createItem(Material.BOOK, "<yellow><bold>History</bold></yellow>", List.of("<gray>View transactions</gray>"),
                p -> new HistoryGui(plugin).open(p)));

        // Close
        gui.setItem(53, createItem(Material.BARRIER, "<red>Close</red>", null, Player::closeInventory));
    }

    // --- Core Helpers ---

    /**
     * Helper หลักในการสร้าง Item + Action + Sound + Cooldown ในบรรทัดเดียว
     */
    private GuiItem createItem(Material mat, String name, List<String> lore, Consumer<Player> action) {
        var builder = PaperItemBuilder.from(mat);
        if (!name.isEmpty()) builder.name(plugin.getMessageUtil().parse(name));
        if (lore != null) builder.lore(lore.stream().map(l -> plugin.getMessageUtil().parse(l)).toList());

        // ถ้าไม่มี Action (เช่น Filler) ให้ส่ง GuiItem ธรรมดา
        if (action == null) return new GuiItem(builder.build());

        // ถ้ามี Action ให้ Wrap ด้วย Cooldown และ Sound
        return new GuiItem(builder.build(), withCooldown(event -> action.accept((Player) event.getWhoClicked())));
    }

    private GuiAction<InventoryClickEvent> withCooldown(GuiAction<InventoryClickEvent> action) {
        return event -> {
            if (event.getWhoClicked() instanceof Player player) {
                if (plugin.canClick(player)) {
                    // ✅ เล่นเสียงที่นี่ทีเดียว ไม่ต้องไปใส่ในทุกปุ่ม
                    if (plugin.getConfigManager().getConfig().getGui().isEnableSounds()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    }
                    action.execute(event);
                }
            }
        };
    }

    private void processTransaction(Player player, double amount, boolean isDeposit, Gui gui) {
        var future = isDeposit ? plugin.getBankService().deposit(player, amount, "GUI deposit")
                : plugin.getBankService().withdraw(player, amount, "GUI withdraw");

        future.thenAccept(response -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (response.isSuccess()) {
                if (isDeposit) {
                    plugin.getMessageUtil().sendDepositSuccess(player, amount, response.getNewBalance());
                } else {
                    plugin.getMessageUtil().sendWithdrawSuccess(player, amount, response.getNewBalance());
                }

                player.playSound(player.getLocation(), isDeposit ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                open(player); // Refresh GUI
            } else {
                plugin.getMessageUtil().sendInsufficientFunds(player, response.getNewBalance());
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }));
    }
}