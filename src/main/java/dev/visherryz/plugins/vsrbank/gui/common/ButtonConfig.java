package dev.visherryz.plugins.vsrbank.gui.common;

import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

import java.util.List;

@Getter
@Setter
@Configuration
public class ButtonConfig {

    // Required fields - ต้องกำหนดเสมอ
    private ButtonType type;
    private Material material;

    // Optional fields - ConfigLib จะไม่ generate ถ้าเป็น null
    private Integer slot;
    private String name;
    private List<String> lore;
    private Integer customModelData;

    // Action-specific fields - ทั้งหมดเป็น optional
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

    // ===== Custom Getter Methods (นอกเหนือจาก Lombok) =====
    // สำหรับ backward compatibility และ default values

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

    public enum ButtonType {
        BASIC,              // ปุ่มธรรมดา
        CLOSE,              // ปิด GUI
        BACK,               // กลับหน้าก่อนหน้า
        OPEN_GUI,           // เปิด GUI อื่น
        DEPOSIT,            // ฝากเงิน
        WITHDRAW,           // ถอนเงิน
        DEPOSIT_ALL,        // ฝากทั้งหมด
        WITHDRAW_ALL,       // ถอนทั้งหมด
        CUSTOM_AMOUNT,      // กรอกจำนวนเอง
        TRANSFER_PLAYER,    // โอนให้ player
        TRANSFER_CUSTOM,    // โอนแบบกรอกชื่อ
        UPGRADE_TIER,       // อัพเกรดระดับ
        CONFIRM,            // ยืนยัน
        CANCEL,             // ยกเลิก
        NEXT_PAGE,          // หน้าถัดไป
        PREVIOUS_PAGE,      // หน้าก่อนหน้า
        INFO,               // แสดงข้อมูล
        PLAYER_HEAD,        // หัวผู้เล่น
        FILLER,             // ไอเทมเติม
        CUSTOM_COMMAND,      // รันคำสั่งเอง
        WITHDRAW_HALF,
        DEPOSIT_HALF
    }
}