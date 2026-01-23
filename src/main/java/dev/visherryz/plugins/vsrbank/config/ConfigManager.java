package dev.visherryz.plugins.vsrbank.config;

import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Manages all plugin configurations using ConfigLib
 */
@Getter
public class ConfigManager {

    private final JavaPlugin plugin;
    private final File configFile;
    private final File messagesFile;

    private BankConfig config;
    private MessagesConfig messages;

    private final YamlConfigurationProperties properties;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        this.properties = YamlConfigurationProperties.newBuilder()
                .charset(StandardCharsets.UTF_8)
                .header("""
                        ╔═══════════════════════════════════════════════════════════════╗
                        ║                     VsrBank Configuration                      ║
                        ║                                                               ║
                        ║  Supports MiniMessage format for colors and gradients         ║
                        ║  Example: <gradient:#FFD700:#FFA500>Text</gradient>           ║
                        ║                                                               ║
                        ║  Created by Visherryz                                         ║
                        ╚═══════════════════════════════════════════════════════════════╝
                        """)
                .build();
    }

    /**
     * Load or create all configuration files
     */
    public void loadConfigs() {
        // Ensure plugin directory exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // 1. Load Main Config
        this.config = YamlConfigurations.update(
                configFile.toPath(),
                BankConfig.class,
                properties // ใช้ properties ตัวบนที่ประกาศใน Constructor
        );

        // 2. Load Messages Config
        // สร้าง Properties แยกสำหรับ Messages โดยเฉพาะ เพื่อใส่ Header ของ Messages
        YamlConfigurationProperties messageProperties = YamlConfigurationProperties.newBuilder()
                .charset(StandardCharsets.UTF_8) // บังคับ UTF-8
                .header("""
                        ╔═══════════════════════════════════════════════════════════════╗
                        ║                      VsrBank Messages                          ║
                        ║                                                               ║
                        ║  Supports MiniMessage format for colors and formatting        ║
                        ║  Placeholders: {player}, {amount}, {balance}, etc.            ║
                        ╚═══════════════════════════════════════════════════════════════╝
                        """)
                .build();

        this.messages = YamlConfigurations.update(
                messagesFile.toPath(),
                MessagesConfig.class,
                messageProperties
        );

        plugin.getLogger().info("Configurations loaded successfully!");
    }

    /**
     * Reload all configuration files
     */
    public void reloadConfigs() {
        try {
            loadConfigs();
            plugin.getLogger().info("Configurations reloaded successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to reload configurations! Please check your YAML syntax.");
            plugin.getLogger().severe("Error: " + e.getMessage());
            e.printStackTrace(); // ปริ้นท์ Error ออกมาดูว่าผิดบรรทัดไหน
        }
    }

    /**
     * Save the main configuration
     */
    public void saveConfig() {
        YamlConfigurations.save(configFile.toPath(), BankConfig.class, config, properties);
    }

    /**
     * Save the messages configuration
     */
    public void saveMessages() {
        YamlConfigurations.save(
                messagesFile.toPath(),
                MessagesConfig.class,
                messages,
                properties
        );
    }
}