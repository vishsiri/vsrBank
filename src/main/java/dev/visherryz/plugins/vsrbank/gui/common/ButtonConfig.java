package dev.visherryz.plugins.vsrbank.gui.common;

import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Button configuration for GUI elements.
 *
 * Supports single or multi-slot placement:
 *
 *   Single slot (backward compatible):
 *     slot: 4
 *
 *   Multiple slots:
 *     slots:
 *       - 4
 *       - 5
 *       - 6
 *
 *   Both set → slots takes priority over slot.
 *
 * Use getEffectiveSlots() everywhere instead of getSlot() directly.
 */
@Getter
@Setter
@Configuration
public class ButtonConfig {

    // Required fields
    private ButtonType type;
    private Material material;

    // ==================== Slot Configuration ====================
    // Single slot (backward compatible) — use -1 or null to hide
    private Integer slot;

    // Multiple slots — overrides 'slot' if non-empty
    private List<Integer> slots;

    // Optional display fields
    private String name;
    private List<String> lore;
    private Integer customModelData;

    // Action-specific fields
    private String action;
    private String targetGui;
    private Double amount;
    private String command;

    // Player head specific
    private String playerName;
    private Boolean useOwnerHead;

    // Conditional display
    private String requirePermission;
    private String showWhen;

    // ==================== Multi-Slot Helpers ====================

    /**
     * Get all effective slots for this button.
     *
     * Priority:
     *   1. 'slots' list non-empty → return slots (filtered: >= 0)
     *   2. 'slot' >= 0 → return single-element list
     *   3. Otherwise → empty list (hidden)
     *
     * YAML examples:
     *   slot: 4              → [4]
     *   slots: [4, 5, 6]     → [4, 5, 6]
     *   slot: -1              → [] (hidden)
     *   (neither set)         → [] (hidden)
     */
    public List<Integer> getEffectiveSlots() {
        // slots field takes priority
        if (slots != null && !slots.isEmpty()) {
            List<Integer> valid = new ArrayList<>();
            for (Integer s : slots) {
                if (s != null && s >= 0) {
                    valid.add(s);
                }
            }
            return valid;
        }

        // Fallback to single slot
        if (slot != null && slot >= 0) {
            return List.of(slot);
        }

        return Collections.emptyList();
    }

    /**
     * Check if this button has at least one visible slot
     */
    public boolean isVisible() {
        return !getEffectiveSlots().isEmpty();
    }

    /**
     * Check if this button is hidden (no valid slots)
     */
    public boolean isHidden() {
        return getEffectiveSlots().isEmpty();
    }

    // ==================== Backward Compatible Getters ====================

    /**
     * @deprecated Use getEffectiveSlots() instead.
     * Returns first effective slot or -1 if hidden.
     */
    @Deprecated
    public int getSlot() {
        List<Integer> effective = getEffectiveSlots();
        return effective.isEmpty() ? -1 : effective.get(0);
    }

    public ButtonType getType() {
        return type != null ? type : ButtonType.BASIC;
    }

    public Material getMaterial() {
        return material != null ? material : Material.STONE;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public List<String> getLore() {
        return lore != null ? lore : List.of();
    }

    public boolean hasCustomModelData() {
        return customModelData != null && customModelData > 0;
    }

    public double getAmount() {
        return amount != null ? amount : 0;
    }

    public String getAction() {
        return action != null ? action : "";
    }

    public String getTargetGui() {
        return targetGui != null ? targetGui : "";
    }

    public String getCommand() {
        return command != null ? command : "";
    }

    public String getPlayerName() {
        return playerName != null ? playerName : "";
    }

    public boolean isUseOwnerHead() {
        return useOwnerHead != null && useOwnerHead;
    }

    public String getRequirePermission() {
        return requirePermission != null ? requirePermission : "";
    }

    public String getShowWhen() {
        return showWhen != null ? showWhen : "";
    }

    // ==================== Button Types ====================

    public enum ButtonType {
        BASIC,
        CLOSE,
        BACK,
        OPEN_GUI,
        DEPOSIT,
        WITHDRAW,
        DEPOSIT_ALL,
        WITHDRAW_ALL,
        DEPOSIT_HALF,
        WITHDRAW_HALF,
        CUSTOM_AMOUNT,
        TRANSFER_PLAYER,
        TRANSFER_CUSTOM,
        UPGRADE_TIER,
        CONFIRM,
        CANCEL,
        NEXT_PAGE,
        PREVIOUS_PAGE,
        INFO,
        PLAYER_HEAD,
        FILLER,
        CUSTOM_COMMAND
    }
}