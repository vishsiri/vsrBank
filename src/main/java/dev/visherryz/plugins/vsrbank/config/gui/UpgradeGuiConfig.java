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
public class UpgradeGuiConfig {

    @Comment("GUI Title - placeholder: %tier%")
    private String title = "<gradient:#9D4EDD:#7209B7><bold>Upgrade to Tier %tier%</bold></gradient>";

    @Comment("Number of rows")
    private int rows = 3;

    @Comment("Upgrade info display")
    private UpgradeInfoDisplay infoDisplay = new UpgradeInfoDisplay();

    @Comment("Custom buttons")
    private List<ButtonConfig> buttons = createDefaultButtons();

    @Comment("Filler (leave empty to use common settings)")
    private ButtonConfig filler;  // เปลี่ยนจาก = new ButtonConfig() เป็น null

    @Getter
    @Setter
    @Configuration
    public static class UpgradeInfoDisplay {
        @Comment("Slot position")
        private int slot = 13;

        @Comment("Material")
        private Material material = Material.NETHER_STAR;

        @Comment("Name format - placeholder: %tier_name%")
        private String nameFormat = "<gold><bold>Upgrade to %tier_name%</bold></gold>";

        @Comment("Lore format - placeholders: %cost%, %xp%, %interest%, %max_balance%")
        private List<String> lore = List.of(
                "",
                "<gray>Cost: <red>%cost%</red></gray>",
                "<gray>XP Required: <green>%xp% Levels</green></gray>",
                "",
                "<gray>Benefits:</gray>",
                "<gray>• Interest: <green>%interest%</green></gray>",
                "<gray>• Max Balance: <green>%max_balance%</green></gray>"
        );
        @Comment("Custom model data (optional)")
        private Integer customModelData;
    }

    private static List<ButtonConfig> createDefaultButtons() {
        List<ButtonConfig> buttons = new ArrayList<>();

        // Confirm
        ButtonConfig confirm = new ButtonConfig();
        confirm.setType(ButtonConfig.ButtonType.CONFIRM);
        confirm.setMaterial(Material.LIME_WOOL);
        confirm.setName("<green><bold>✓ Confirm Upgrade</bold></green>");
        confirm.setLore(List.of(
                "<gray>Click to upgrade your tier</gray>"
        ));
        confirm.setSlot(11);
        buttons.add(confirm);

        // Cancel
        ButtonConfig cancel = new ButtonConfig();
        cancel.setType(ButtonConfig.ButtonType.CANCEL);
        cancel.setMaterial(Material.RED_WOOL);
        cancel.setName("<red><bold>✗ Cancel</bold></red>");
        cancel.setLore(List.of(
                "<gray>Go back without upgrading</gray>"
        ));
        cancel.setSlot(15);
        cancel.setTargetGui("bank");
        buttons.add(cancel);

        return buttons;
    }
}