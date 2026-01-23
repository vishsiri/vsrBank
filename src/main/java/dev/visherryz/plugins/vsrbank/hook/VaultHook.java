package dev.visherryz.plugins.vsrbank.hook;

import dev.visherryz.plugins.vsrbank.VsrBank;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault Economy integration
 */
public class VaultHook {

    private final VsrBank plugin;

    @Getter
    private Economy economy;

    @Getter
    private boolean hooked = false;

    public VaultHook(VsrBank plugin) {
        this.plugin = plugin;
    }

    /**
     * Setup Vault economy hook
     */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Economy features will be limited.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No economy provider found! Make sure you have an economy plugin installed.");
            return false;
        }

        economy = rsp.getProvider();
        hooked = true;
        plugin.getLogger().info("Hooked into Vault economy: " + economy.getName());
        return true;
    }

    /**
     * Check if Vault is available
     */
    public boolean isAvailable() {
        return hooked && economy != null;
    }
}