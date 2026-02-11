package dev.visherryz.plugins.vsrbank.config.gui;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import dev.visherryz.plugins.vsrbank.gui.common.ButtonConfig;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
public class BankGuiConfig {

    @Comment("GUI Title")
    private String title = "<gradient:#FFD700:#FFA500><bold>Bank Account</bold></gradient>";

    @Comment("Number of rows (1-6)")
    private int rows = 6;

    @Comment("Player head display")
    private PlayerHeadSection playerHead = new PlayerHeadSection();

    @Comment("Withdraw section")
    private TransactionSection withdrawSection = createWithdrawSection();

    @Comment("Deposit section")
    private TransactionSection depositSection = createDepositSection();

    @Comment("Utility buttons")
    private List<ButtonConfig> buttons = createDefaultButtons();

    @Comment("Filler - all fields optional, will use common settings for missing values")
    private ButtonConfig filler;  // เปลี่ยนจาก = new ButtonConfig() เป็น null

    @Getter
    @Setter
    @Configuration
    public static class PlayerHeadSection {
        @Comment("Slot position (optional)")
        private Integer slot = 4;
        private List<Integer> slots = new ArrayList<>();

        public List<Integer> getEffectiveSlots() {
            if (slots != null && !slots.isEmpty()) {
                return slots;
            }
            if (slot != null && slot != -1) {
                return List.of(slot);
            }
            return java.util.Collections.emptyList();
        }
        @Comment("Display format (optional)")
        private String nameFormat = "<gold><bold>%player%'s Account</bold></gold>";

        @Comment("Lore format - placeholders: %balance%, %tier%, %interest% (optional)")
        private List<String> lore = List.of(
                "<gray>Balance: <white>%balance%</white></gray>",
                "<gray>Tier: <yellow>%tier%</yellow></gray>",
                "<gray>Interest: <green>%interest%</green></gray>"
        );

        @Comment("Custom model data (optional)")
        private Integer customModelData;
    }

    @Getter
    @Setter
    @Configuration
    public static class TransactionSection {
        @Comment("Section label (all fields optional)")
        private ButtonConfig label;  // เปลี่ยนเป็น null

        @Comment("Preset amount button slots (optional)")
        private List<Integer> slots = new ArrayList<>();

        @Comment("Preset amounts (optional)")
        private List<Double> amounts = List.of(5.0, 10.0, 50.0, 100.0, 500.0, 1000.0);

        @Comment("Preset button template (all fields optional)")
        private ButtonConfig presetTemplate;  // เปลี่ยนเป็น null

        @Comment("Custom amount button (all fields optional)")
        private ButtonConfig customButton;  // เปลี่ยนเป็น null

        @Comment("All in/out button (all fields optional)")
        private ButtonConfig allButton;  // เปลี่ยนเป็น null

        @Comment("Half in/out button (all fields optional)")
        private ButtonConfig halfButton;
    }

    private static TransactionSection createWithdrawSection() {
        TransactionSection section = new TransactionSection();

        // Label
        ButtonConfig label = new ButtonConfig();
        label.setType(ButtonConfig.ButtonType.INFO);
        label.setMaterial(Material.RED_WOOL);
        label.setName("<red><bold>WITHDRAW</bold></red>");
        label.setLore(List.of("<gray>Take money from your account</gray>"));
        label.setSlot(9);
        section.setLabel(label);

        // Preset slots
        section.setSlots(List.of(10, 11, 12, 13, 14, 15));

        // Preset template
        ButtonConfig preset = new ButtonConfig();
        preset.setType(ButtonConfig.ButtonType.WITHDRAW);
        preset.setMaterial(Material.RED_STAINED_GLASS_PANE);
        preset.setLore(List.of("<gray>Click to withdraw</gray>"));
        section.setPresetTemplate(preset);

        // Custom
        ButtonConfig custom = new ButtonConfig();
        custom.setType(ButtonConfig.ButtonType.CUSTOM_AMOUNT);
        custom.setMaterial(Material.OAK_SIGN);
        custom.setName("<yellow>Custom Amount</yellow>");
        custom.setLore(List.of("<gray>Type amount in chat</gray>"));
        custom.setSlot(16);
        custom.setAction("withdraw");
        section.setCustomButton(custom);

        // All out
        ButtonConfig all = new ButtonConfig();
        all.setType(ButtonConfig.ButtonType.WITHDRAW_ALL);
        all.setMaterial(Material.HOPPER);
        all.setName("<red>Withdraw All</red>");
        all.setLore(List.of("<gray>Balance: %balance%</gray>"));
        all.setSlot(17);
        section.setAllButton(all);

        return section;
    }

    private static TransactionSection createDepositSection() {
        TransactionSection section = new TransactionSection();

        // Label
        ButtonConfig label = new ButtonConfig();
        label.setType(ButtonConfig.ButtonType.INFO);
        label.setMaterial(Material.LIME_WOOL);
        label.setName("<green><bold>DEPOSIT</bold></green>");
        label.setLore(List.of("<gray>Put money into your account</gray>"));
        label.setSlot(27);
        section.setLabel(label);

        // Preset slots
        section.setSlots(List.of(28, 29, 30, 31, 32, 33));

        // Preset template
        ButtonConfig preset = new ButtonConfig();
        preset.setType(ButtonConfig.ButtonType.DEPOSIT);
        preset.setMaterial(Material.LIME_STAINED_GLASS_PANE);
        preset.setLore(List.of("<gray>Click to deposit</gray>"));
        section.setPresetTemplate(preset);

        // Custom
        ButtonConfig custom = new ButtonConfig();
        custom.setType(ButtonConfig.ButtonType.CUSTOM_AMOUNT);
        custom.setMaterial(Material.OAK_SIGN);
        custom.setName("<yellow>Custom Amount</yellow>");
        custom.setLore(List.of("<gray>Type amount in chat</gray>"));
        custom.setSlot(34);
        custom.setAction("deposit");
        section.setCustomButton(custom);

        // All in
        ButtonConfig all = new ButtonConfig();
        all.setType(ButtonConfig.ButtonType.DEPOSIT_ALL);
        all.setMaterial(Material.CHEST);
        all.setName("<green>Deposit All</green>");
        all.setLore(List.of("<gray>Wallet: %wallet%</gray>"));
        all.setSlot(35);
        section.setAllButton(all);

        return section;
    }

    private static List<ButtonConfig> createDefaultButtons() {
        List<ButtonConfig> buttons = new ArrayList<>();

        // Transfer
        ButtonConfig transfer = new ButtonConfig();
        transfer.setType(ButtonConfig.ButtonType.OPEN_GUI);
        transfer.setMaterial(Material.ENDER_PEARL);
        transfer.setName("<aqua><bold>Transfer</bold></aqua>");
        transfer.setLore(List.of("<gray>Send money to other players</gray>"));
        transfer.setSlot(48);
        transfer.setTargetGui("transfer");
        buttons.add(transfer);

        // Upgrade
        ButtonConfig upgrade = new ButtonConfig();
        upgrade.setType(ButtonConfig.ButtonType.UPGRADE_TIER);
        upgrade.setMaterial(Material.NETHER_STAR);
        upgrade.setName("<light_purple><bold>Upgrade Tier</bold></light_purple>");
        upgrade.setLore(List.of("<gray>Click to view upgrade options</gray>"));
        upgrade.setSlot(49);
        upgrade.setShowWhen("tier < max_tier");
        buttons.add(upgrade);

        // Max tier
        ButtonConfig maxTier = new ButtonConfig();
        maxTier.setType(ButtonConfig.ButtonType.INFO);
        maxTier.setMaterial(Material.BARRIER);
        maxTier.setName("<red><bold>Max Tier Reached</bold></red>");
        maxTier.setLore(List.of("<gray>You are at maximum tier!</gray>"));
        maxTier.setSlot(49);
        maxTier.setShowWhen("tier >= max_tier");
        buttons.add(maxTier);

        // History
        ButtonConfig history = new ButtonConfig();
        history.setType(ButtonConfig.ButtonType.OPEN_GUI);
        history.setMaterial(Material.BOOK);
        history.setName("<yellow><bold>Transaction History</bold></yellow>");
        history.setLore(List.of("<gray>View your past transactions</gray>"));
        history.setSlot(50);
        history.setTargetGui("history");
        buttons.add(history);

        // Close
        ButtonConfig close = new ButtonConfig();
        close.setType(ButtonConfig.ButtonType.CLOSE);
        close.setMaterial(Material.BARRIER);
        close.setName("<red>Close</red>");
        close.setSlot(53);
        buttons.add(close);

        return buttons;
    }
}