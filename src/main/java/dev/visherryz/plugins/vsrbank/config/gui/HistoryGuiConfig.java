package dev.visherryz.plugins.vsrbank.config.gui;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import dev.visherryz.plugins.vsrbank.gui.common.ButtonConfig;
import dev.visherryz.plugins.vsrbank.model.TransactionLog;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

import java.util.*;

@Getter
@Setter
@Configuration
public class HistoryGuiConfig {

    @Comment("GUI Title")
    private String title = "<gradient:#FFD700:#FF8C00><bold>Transaction History</bold></gradient>";

    @Comment("Number of rows")
    private int rows = 6;

    @Comment("Items per page (max 18 for 2 rows)")
    private int pageSize = 18;

    @Comment("Maximum entries to load from database")
    private int maxEntries = 100;

    @Comment("Transaction log display format")
    private TransactionLogFormat logFormat = new TransactionLogFormat();

    @Comment("Empty state display")
    private ButtonConfig emptyItem = createEmptyItem();

    @Comment("Navigation buttons")
    private List<ButtonConfig> buttons = createDefaultButtons();

    @Comment("Filler settings")
    private FillerSettings fillerSettings = new FillerSettings();

    @Getter
    @Setter
    @Configuration
    public static class TransactionLogFormat {
        @Comment("Color codes for transaction types")
        private Map<String, String> typeColors = createTypeColors();

        @Comment("Amount prefix for transaction types")
        private Map<String, String> amountPrefixes = createAmountPrefixes();

        @Comment("Custom model data for transaction types (optional)")
        private Map<String, Integer> customModelData = new HashMap<>();

        @Comment("Lore format - placeholders: %color%, %amount%, %balance%, %target%, %date%")
        private List<String> lore = List.of(
                "<gray>Amount: %color%%amount_prefix%%amount%</gray>",
                "<gray>Balance: <white>%balance%</white></gray>",
                "%target_line%",
                "",
                "<dark_gray>%date%</dark_gray>"
        );

        @Comment("Target player line format (shown when target exists)")
        private String targetLineFormat = "<gray>Player: <white>%target%</white></gray>";

        // เพิ่ม helper method
        public Integer getCustomModelData(TransactionLog.TransactionType type) {
            return customModelData.get(type.name());
        }

        private static Map<String, String> createTypeColors() {
            Map<String, String> colors = new LinkedHashMap<>();
            colors.put("DEPOSIT", "<green>");
            colors.put("WITHDRAW", "<red>");
            colors.put("TRANSFER_IN", "<green>");
            colors.put("TRANSFER_OUT", "<red>");
            colors.put("INTEREST", "<green>");
            colors.put("ADMIN_GIVE", "<green>");
            colors.put("ADMIN_TAKE", "<red>");
            colors.put("FEE", "<red>");
            colors.put("UPGRADE", "<red>");
            return colors;
        }

        private static Map<String, String> createAmountPrefixes() {
            Map<String, String> prefixes = new LinkedHashMap<>();
            prefixes.put("DEPOSIT", "+");
            prefixes.put("WITHDRAW", "-");
            prefixes.put("TRANSFER_IN", "+");
            prefixes.put("TRANSFER_OUT", "-");
            prefixes.put("INTEREST", "+");
            prefixes.put("ADMIN_GIVE", "+");
            prefixes.put("ADMIN_TAKE", "-");
            prefixes.put("FEE", "-");
            prefixes.put("UPGRADE", "-");
            return prefixes;
        }
    }

    @Getter
    @Setter
    @Configuration
    public static class FillerSettings {
        @Comment("Fill bottom row")
        private boolean fillBottom = true;

        @Comment("Middle filler area (rows 3-5)")
        private FillerArea middleArea = new FillerArea();

        @Comment("Custom filler (leave empty to use common settings)")
        private ButtonConfig customFiller;  // เปลี่ยนจาก = new ButtonConfig() เป็น null
    }

    @Getter
    @Setter
    @Configuration
    public static class FillerArea {
        private int startRow = 3;
        private int startCol = 1;
        private int endRow = 5;
        private int endCol = 9;
    }

    private static ButtonConfig createEmptyItem() {
        ButtonConfig btn = new ButtonConfig();
        btn.setType(ButtonConfig.ButtonType.INFO);
        btn.setMaterial(Material.BARRIER);
        btn.setName("<gray>No Transaction History</gray>");
        btn.setLore(List.of("<gray>Make your first transaction!</gray>"));
        btn.setSlot(22);
        return btn;
    }

    private static List<ButtonConfig> createDefaultButtons() {
        List<ButtonConfig> buttons = new ArrayList<>();

        // Previous page
        ButtonConfig previous = new ButtonConfig();
        previous.setType(ButtonConfig.ButtonType.PREVIOUS_PAGE);
        previous.setMaterial(Material.PAPER);
        previous.setName("<yellow>◀ Previous Page</yellow>");
        previous.setSlot(48);
        buttons.add(previous);

        // Back
        ButtonConfig back = new ButtonConfig();
        back.setType(ButtonConfig.ButtonType.BACK);
        back.setMaterial(Material.ARROW);
        back.setName("<gray>Back to Menu</gray>");
        back.setSlot(49);
        back.setTargetGui("bank");
        buttons.add(back);

        // Next page
        ButtonConfig next = new ButtonConfig();
        next.setType(ButtonConfig.ButtonType.NEXT_PAGE);
        next.setMaterial(Material.PAPER);
        next.setName("<yellow>Next Page ▶</yellow>");
        next.setSlot(50);
        buttons.add(next);

        return buttons;
    }
}