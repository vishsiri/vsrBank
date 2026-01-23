package dev.visherryz.plugins.vsrbank.redis;

import com.google.gson.Gson;
import dev.visherryz.plugins.vsrbank.VsrBank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Redis pub/sub subscriber for receiving cross-server updates
 */
public class RedisSubscriber extends JedisPubSub {

    private final VsrBank plugin;
    private final Gson gson;
    private final String currentServerId;

    public RedisSubscriber(VsrBank plugin, Gson gson) {
        this.plugin = plugin;
        this.gson = gson;
        this.currentServerId = plugin.getConfigManager().getConfig().getServerId();
    }

    @Override
    public void onMessage(String channel, String message) {
        try {
            if (channel.equals("vsrbank:balance")) {
                handleBalanceUpdate(message);
            } else if (channel.equals(plugin.getConfigManager().getConfig().getRedis().getChannel())) {
                handleCustomMessage(message);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing Redis message", e);
        }
    }

    /**
     * Handle balance update from another server
     */
    private void handleBalanceUpdate(String message) {
        RedisManager.BalanceUpdateMessage update = gson.fromJson(message, RedisManager.BalanceUpdateMessage.class);

        // Ignore messages from this server
        if (currentServerId.equals(update.sourceServer())) {
            return;
        }

        UUID playerUuid = update.playerUuid();
        double newBalance = update.newBalance();

        // Update cache if player is online
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            // Schedule on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Update local cache (if you implement caching)
                plugin.getLogger().fine("Received balance update from " + update.sourceServer() +
                        " for " + player.getName() + ": " + newBalance);

                // You could notify the player here if desired
                // plugin.getMessageUtil().send(player, "Your balance was updated on another server!");
            });
        }
    }

    /**
     * Handle custom messages (for future extensibility)
     */
    private void handleCustomMessage(String message) {
        // Placeholder for custom message handling
        plugin.getLogger().fine("Received custom Redis message: " + message);
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        plugin.getLogger().info("Subscribed to Redis channel: " + channel);
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        plugin.getLogger().info("Unsubscribed from Redis channel: " + channel);
    }
}