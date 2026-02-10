package dev.visherryz.plugins.vsrbank.config;

import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import dev.visherryz.plugins.vsrbank.gui.common.CommonGuiSettings;
import dev.visherryz.plugins.vsrbank.config.gui.BankGuiConfig;
import dev.visherryz.plugins.vsrbank.config.gui.HistoryGuiConfig;
import dev.visherryz.plugins.vsrbank.config.gui.TransferGuiConfig;
import dev.visherryz.plugins.vsrbank.config.gui.UpgradeGuiConfig;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Manages all plugin configurations using ConfigLib
 * Includes both main configs and GUI configs
 */
@Getter
public class ConfigManager {

    private final JavaPlugin plugin;

    // Main config files
    private final File configFile;
    private final File messagesFile;

    // GUI config path (separate files)
    private final Path guiConfigPath;

    // Main configs
    private BankConfig config;
    private MessagesConfig messages;

    // GUI configs (new system)
    private CommonGuiSettings commonGui;
    private BankGuiConfig bankGui;
    private TransferGuiConfig transferGui;
    private UpgradeGuiConfig upgradeGui;
    private HistoryGuiConfig historyGui;

    private final YamlConfigurationProperties properties;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.guiConfigPath = plugin.getDataFolder().toPath().resolve("guis");

        this.properties = YamlConfigurationProperties.newBuilder()
                .charset(StandardCharsets.UTF_8)
                .header("""
                        ╔═══════════════════════════════════════════════════════════════╗
                        ║                     VsrBank Configuration                     ║
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

        // Create guis folder
        guiConfigPath.toFile().mkdirs();

        // 1. Load Main Config
        this.config = YamlConfigurations.update(
                configFile.toPath(),
                BankConfig.class,
                properties
        );

        // 2. Load Messages Config
        YamlConfigurationProperties messageProperties = YamlConfigurationProperties.newBuilder()
                .charset(StandardCharsets.UTF_8)
                .header("""
                        ╔═══════════════════════════════════════════════════════════════╗
                        ║                      VsrBank Messages                         ║
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


        // 4. Load New GUI Configs (separate files)
        loadGuiConfigs();

        plugin.getLogger().info("All configurations loaded successfully!");
    }

    /**
     * Load GUI configs from separate files
     */
    private void loadGuiConfigs() {
        YamlConfigurationProperties guiProps = YamlConfigurationProperties.newBuilder()
                .charset(StandardCharsets.UTF_8)
                .build();

        commonGui = YamlConfigurations.update(
                guiConfigPath.resolve("common.yml"),
                CommonGuiSettings.class,
                guiProps
        );

        bankGui = YamlConfigurations.update(
                guiConfigPath.resolve("bank.yml"),
                BankGuiConfig.class,
                guiProps
        );

        transferGui = YamlConfigurations.update(
                guiConfigPath.resolve("transfer.yml"),
                TransferGuiConfig.class,
                guiProps
        );

        upgradeGui = YamlConfigurations.update(
                guiConfigPath.resolve("upgrade.yml"),
                UpgradeGuiConfig.class,
                guiProps
        );

        historyGui = YamlConfigurations.update(
                guiConfigPath.resolve("history.yml"),
                HistoryGuiConfig.class,
                guiProps
        );

        plugin.getLogger().info("GUI configurations loaded from guis/ folder");
    }

    /**
     * Reload all configuration files
     */
    public void reloadConfigs() {
        try {
            loadConfigs();
            plugin.getLogger().info("All configurations reloaded successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to reload configurations! Please check your YAML syntax.");
            plugin.getLogger().severe("Error: " + e.getMessage());
            e.printStackTrace();
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

    /**
     * Get GUI config by name (for dynamic loading)
     */
    public Object getGuiConfigByName(String guiName) {
        return switch (guiName.toLowerCase()) {
            case "bank", "main" -> bankGui;
            case "transfer" -> transferGui;
            case "upgrade" -> upgradeGui;
            case "history" -> historyGui;
            case "common" -> commonGui;
            default -> null;
        };
    }
}