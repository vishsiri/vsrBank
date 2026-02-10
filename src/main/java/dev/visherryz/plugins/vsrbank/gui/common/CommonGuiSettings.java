package dev.visherryz.plugins.vsrbank.gui.common;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

import java.util.List;

@Getter
@Setter
@Configuration
public class CommonGuiSettings {

    @Comment("Enable sound effects in GUIs")
    private boolean enableSounds = true;

    @Comment("Anti-spam click cooldown (milliseconds)")
    private int clickCooldown = 200;

    @Comment("Default filler material")
    private ButtonConfig defaultFiller = createDefaultFiller();

    @Comment("Default close button")
    private ButtonConfig defaultCloseButton = createDefaultClose();

    @Comment("Default back button")
    private ButtonConfig defaultBackButton = createDefaultBack();

    @Comment("Default next page button")
    private ButtonConfig defaultNextButton = createDefaultNext();

    @Comment("Default previous page button")
    private ButtonConfig defaultPreviousButton = createDefaultPrevious();

    private static ButtonConfig createDefaultFiller() {
        ButtonConfig btn = new ButtonConfig();
        btn.setType(ButtonConfig.ButtonType.FILLER);
        btn.setMaterial(Material.GRAY_STAINED_GLASS_PANE);
        btn.setName(" ");
        return btn;
    }

    private static ButtonConfig createDefaultClose() {
        ButtonConfig btn = new ButtonConfig();
        btn.setType(ButtonConfig.ButtonType.CLOSE);
        btn.setMaterial(Material.BARRIER);
        btn.setName("<red>Close</red>");
        btn.setSlot(53);
        return btn;
    }

    private static ButtonConfig createDefaultBack() {
        ButtonConfig btn = new ButtonConfig();
        btn.setType(ButtonConfig.ButtonType.BACK);
        btn.setMaterial(Material.ARROW);
        btn.setName("<gray>Back</gray>");
        btn.setSlot(49);
        btn.setTargetGui("bank");
        return btn;
    }

    private static ButtonConfig createDefaultNext() {
        ButtonConfig btn = new ButtonConfig();
        btn.setType(ButtonConfig.ButtonType.NEXT_PAGE);
        btn.setMaterial(Material.PAPER);
        btn.setName("<yellow>Next Page ▶</yellow>");
        btn.setSlot(50);
        return btn;
    }

    private static ButtonConfig createDefaultPrevious() {
        ButtonConfig btn = new ButtonConfig();
        btn.setType(ButtonConfig.ButtonType.PREVIOUS_PAGE);
        btn.setMaterial(Material.PAPER);
        btn.setName("<yellow>◀ Previous Page</yellow>");
        btn.setSlot(48);
        return btn;
    }
}