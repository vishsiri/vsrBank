package dev.visherryz.plugins.vsrbank.listener;

import dev.visherryz.plugins.vsrbank.VsrBank;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join/quit events for account management
 */
public class PlayerListener implements Listener {

    private final VsrBank plugin;

    public PlayerListener(VsrBank plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Create or update account async
        plugin.getBankService().getOrCreateAccount(player.getUniqueId(), player.getName())
                .thenAccept(account -> {
                    // Update last online time
                    plugin.getDatabaseManager().getProvider().updateLastOnline(player.getUniqueId());
                    plugin.getLogger().fine("Loaded bank account for " + player.getName());
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to load bank account for " + player.getName() + ": " + ex.getMessage());
                    return null;
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();

        // Update last online time async
        plugin.getDatabaseManager().getProvider().updateLastOnline(player.getUniqueId())
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to update last online for " + player.getName());
                    return false;
                });
    }
}
