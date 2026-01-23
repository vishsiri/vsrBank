package dev.visherryz.plugins.vsrbank;

import dev.triumphteam.gui.guis.BaseGui;
import dev.visherryz.plugins.vsrbank.api.BankAPI;
import dev.visherryz.plugins.vsrbank.command.BankCommand;
import dev.visherryz.plugins.vsrbank.config.ConfigManager;
import dev.visherryz.plugins.vsrbank.database.DatabaseManager;
import dev.visherryz.plugins.vsrbank.hook.PlaceholderExpansionHook;
import dev.visherryz.plugins.vsrbank.hook.VaultHook;
import dev.visherryz.plugins.vsrbank.listener.PlayerListener;
import dev.visherryz.plugins.vsrbank.redis.RedisManager;
import dev.visherryz.plugins.vsrbank.service.BankService;
import dev.visherryz.plugins.vsrbank.service.InterestService;
import dev.visherryz.plugins.vsrbank.task.InterestTask;
import dev.visherryz.plugins.vsrbank.util.DiscordWebhook;
import dev.visherryz.plugins.vsrbank.util.MessageUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;


import revxrsal.commands.bukkit.BukkitLamp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class VsrBank extends JavaPlugin {

    @Getter private static VsrBank instance;
    @Getter private static BankAPI api;
    @Getter private ConfigManager configManager;
    @Getter private DatabaseManager databaseManager;
    @Getter private RedisManager redisManager;
    @Getter private BankService bankService;
    @Getter private InterestService interestService;
    @Getter private MessageUtil messageUtil;
    @Getter private DiscordWebhook discordWebhook;
    @Getter private VaultHook vaultHook;
    private InterestTask interestTask;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private static final long COOLDOWN_MILLIS = 500;
    @Override
    public void onEnable() {
        instance = this;
        long startTime = System.currentTimeMillis();

        getLogger().info("╔═══════════════════════════════════════════╗");
        getLogger().info("║         VsrBank - Starting Up...          ║");
        getLogger().info("╚═══════════════════════════════════════════╝");

        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        messageUtil = new MessageUtil(this);
        discordWebhook = new DiscordWebhook(this);

        vaultHook = new VaultHook(this);
        if (!vaultHook.setup()) {
            getLogger().warning("Vault not found! Deposit/Withdraw will be disabled.");
        }

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initialize().get();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        redisManager = new RedisManager(this);
        redisManager.initialize();

        bankService = new BankService(this);
        interestService = new InterestService(this);

        // Register Commands
        registerCommands();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        startTasks();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderExpansionHook(this).register();
        }

        api = new BankAPI(this);
        getServer().getServicesManager().register(BankAPI.class, api, this, ServicePriority.Normal);

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("Enabled successfully in " + loadTime + "ms");
    }

    @Override
    public void onDisable() {
        closeOpenMenus();
        if (interestTask != null) interestTask.cancel();
        if (messageUtil != null) messageUtil.shutdown();
        if (discordWebhook != null) discordWebhook.shutdown();
        if (redisManager != null) {
            try { redisManager.shutdown().get(); } catch (Exception ignored) {}
        }
        if (databaseManager != null) {
            try { databaseManager.shutdown().get(); } catch (Exception ignored) {}
        }
    }

    public void closeOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BaseGui) {
                player.closeInventory();

                if (messageUtil != null) {
                    messageUtil.sendRaw(player, "<red>Bank system is reloading, closing menu...</red>");
                }
            }
        }
    }

    public boolean canClick(Player player) {
        long now = System.currentTimeMillis();
        long lastClick = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - lastClick < COOLDOWN_MILLIS) {
            return false;
        }

        clickCooldowns.put(player.getUniqueId(), now);
        return true;
    }

    public void removeCooldown(UUID uuid) {
        clickCooldowns.remove(uuid);
    }

    private void registerCommands() {
        var lamp = BukkitLamp.builder(this).build();

        lamp.register(new BankCommand(this));
    }

    private void startTasks() {
        if (configManager.getConfig().getInterest().isEnabled()) {
            interestTask = new InterestTask(this);
            interestTask.start();
        }
    }

    public static BankAPI getAPI() {
        return api;
    }
}