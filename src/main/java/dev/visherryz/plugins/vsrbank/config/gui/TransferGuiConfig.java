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
public class TransferGuiConfig {

    @Comment("GUI Title")
    private String title = "<gradient:#00FFFF:#0080FF><bold>Transfer Money</bold></gradient>";

    @Comment("Number of rows")
    private int rows = 4;

    @Comment("Slots for online players (auto-populated)")
    private List<Integer> playerSlots = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    );

    @Comment("Player head display format")
    private PlayerHeadDisplay playerHeadDisplay = new PlayerHeadDisplay();

    @Comment("Balance info display")
    private ButtonConfig balanceInfo = createBalanceInfo();

    @Comment("Custom buttons")
    private List<ButtonConfig> buttons = createDefaultButtons();

    @Comment("Filler (leave empty to use common settings)")
    private ButtonConfig filler;  // เปลี่ยนจาก = new ButtonConfig() เป็น null

    @Getter
    @Setter
    @Configuration
    public static class PlayerHeadDisplay {
        @Comment("Name format - placeholder: %player%")
        private String nameFormat = "<yellow>%player%</yellow>";

        @Comment("Lore format")
        private List<String> lore = List.of(
                "<gray>Click to transfer</gray>"
        );

        @Comment("Custom model data (optional)")
        private Integer customModelData;
    }

    private static ButtonConfig createBalanceInfo() {
        ButtonConfig btn = new ButtonConfig();
        btn.setType(ButtonConfig.ButtonType.INFO);
        btn.setMaterial(Material.GOLD_INGOT);
        btn.setName("<gold><bold>Your Balance</bold></gold>");
        btn.setLore(List.of(
                "<gray>Available: <white>%balance%</white></gray>",
                "",
                "<gray>Select a player to transfer</gray>"
        ));
        btn.setSlot(4);
        return btn;
    }

    private static List<ButtonConfig> createDefaultButtons() {
        List<ButtonConfig> buttons = new ArrayList<>();

        // Custom player
        ButtonConfig custom = new ButtonConfig();
        custom.setType(ButtonConfig.ButtonType.TRANSFER_CUSTOM);
        custom.setMaterial(Material.NAME_TAG);
        custom.setName("<aqua><bold>Custom Player</bold></aqua>");
        custom.setLore(List.of(
                "<gray>Type player name in chat</gray>",
                "<gray>to transfer to offline players</gray>"
        ));
        custom.setSlot(31);
        buttons.add(custom);

        // Back
        ButtonConfig back = new ButtonConfig();
        back.setType(ButtonConfig.ButtonType.BACK);
        back.setMaterial(Material.ARROW);
        back.setName("<gray>Back to Menu</gray>");
        back.setSlot(27);
        back.setTargetGui("bank");
        buttons.add(back);

        return buttons;
    }


}